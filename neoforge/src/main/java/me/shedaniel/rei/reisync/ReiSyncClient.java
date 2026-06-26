/*
 * This file is licensed under the MIT License, part of Roughly Enough Items.
 * Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.shedaniel.rei.reisync;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Decodes ReiSync item chunks into native client {@link ItemStack}s and hands them to
 * {@link ReiSyncStore}. Decoding is intentionally tolerant: a component the server (a
 * newer MC version) sent that this client doesn't understand is skipped, not fatal.
 */
public final class ReiSyncClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReiSync");

    /** Verbose per-item logging, off unless launched with -Dreisync.debug=true. */
    private static final boolean DEBUG = Boolean.getBoolean("reisync.debug");

    /** Per-session count of items that failed to decode; summarised, not logged per item. */
    private static int decodeFailures = 0;

    private ReiSyncClient() {}

    /** Reset diagnostics at the start of a session. */
    public static void resetStats() {
        decodeFailures = 0;
    }

    /** Called on the main thread for each received chunk. */
    public static void onItemsChunk(byte[] data) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        RegistryAccess registryAccess = connection.registryAccess();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            boolean last = in.readBoolean();
            int count = in.readInt();
            List<EntryStack<?>> entries = new ArrayList<>(Math.max(0, count));
            for (int i = 0; i < count; i++) {
                String ceId = in.readUTF();
                int len = in.readInt();
                byte[] itemBytes = in.readNBytes(len);
                try {
                    ItemStack stack = decodeTolerant(itemBytes, registryAccess);
                    if (!stack.isEmpty()) {
                        entries.add(EntryStacks.of(stack));
                    }
                } catch (Throwable t) {
                    decodeFailures++;
                    if (DEBUG) {
                        LOGGER.warn("ReiSync: failed to decode item {}: {}", ceId, t.toString());
                    }
                }
            }
            ReiSyncStore.accept(entries, last);
            if (last && decodeFailures > 0) {
                LOGGER.warn("ReiSync: {} item(s) could not be decoded and were skipped"
                        + " (run with -Dreisync.debug=true for details).", decodeFailures);
                decodeFailures = 0;
            }
        } catch (IOException e) {
            LOGGER.warn("ReiSync: malformed chunk: {}", e.toString());
        }
    }

    /**
     * Rebuilds an {@link ItemStack} from the server's {@code serializeAsBytes()} blob,
     * applying components one at a time and skipping any this version can't parse.
     */
    private static ItemStack decodeTolerant(byte[] data, RegistryAccess registryAccess) throws IOException {
        CompoundTag tag = readNbt(data);
        RegistryOps<Tag> registryOps = registryAccess.createSerializationContext(NbtOps.INSTANCE);
        DynamicOps<Tag> nbtOps = NbtOps.INSTANCE;

        String id = tag.getStringOr("id", "minecraft:air");
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
        if (itemOpt.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int count = Math.max(1, tag.getIntOr("count", 1));
        ItemStack stack = new ItemStack(itemOpt.get(), count);

        // CompoundTag#entrySet()/getOptional() aren't accessible from here (protected/private
        // since the 1.21.5 NBT refactor), so iterate the component map through DynamicOps,
        // which exposes the (keyTag, valueTag) pairs via public API.
        CompoundTag components = tag.getCompoundOrEmpty("components");
        List<Pair<Tag, Tag>> entries = nbtOps.getMapValues(components)
                .result()
                .map(stream -> stream.collect(Collectors.toList()))
                .orElse(Collections.emptyList());
        for (Pair<Tag, Tag> pair : entries) {
            String key = nbtOps.getStringValue(pair.getFirst()).result().orElse(null);
            if (key == null || key.startsWith("!")) {
                continue; // unreadable key, or a component-removal marker -> skip
            }
            ResourceLocation crl = ResourceLocation.tryParse(key);
            if (crl == null) {
                continue;
            }
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(crl).orElse(null);
            if (type == null) {
                continue; // component type unknown on this version -> skip
            }
            applyComponent(stack, type, pair.getSecond(), registryOps);
        }
        return stack;
    }

    private static CompoundTag readNbt(byte[] data) throws IOException {
        // Paper's ItemStack#serializeAsBytes() gzip-compresses the NBT. Detect the gzip
        // magic (1f 8b) and fall back to uncompressed reading just in case.
        boolean gzip = data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
        if (gzip) {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        }
        try (DataInputStream din = new DataInputStream(new ByteArrayInputStream(data))) {
            return NbtIo.read(din);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyComponent(ItemStack stack, DataComponentType<T> type, Tag valueTag, RegistryOps<Tag> ops) {
        Codec<T> codec = type.codec();
        if (codec == null) {
            return; // transient component (not persisted) -> nothing to parse
        }
        codec.parse(ops, valueTag).result().ifPresent(value -> stack.set(type, value));
    }
}

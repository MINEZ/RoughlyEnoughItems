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

import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.impl.common.InternalLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decodes ReiSync item chunks into native client {@link ItemStack}s and hands them to
 * {@link ReiSyncStore}.
 *
 * <p>Decoding is deliberately tolerant. The server may run a different Minecraft version than
 * the client, so the item is rebuilt one data component at a time: a component this client
 * cannot parse is skipped instead of discarding the whole item. A stack left without any component
 * is dropped, since it would only be its base item.
 */
public final class ReiSyncClient {
    /** Verbose per-item logging, off unless launched with {@code -Dreisync.debug=true}. */
    private static final boolean DEBUG = Boolean.getBoolean("reisync.debug");
    
    /** Items that failed to decode during the current sync; summarised, not logged per item. */
    private static int decodeFailures = 0;
    
    private ReiSyncClient() {}
    
    /** Resets diagnostics at the start of a session. */
    public static void resetStats() {
        decodeFailures = 0;
    }
    
    /** Called on the client thread for each received chunk. */
    public static void onItemsChunk(byte[] data, boolean batched) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        RegistryAccess registryAccess = connection.registryAccess();
        
        final Protocol.Chunk chunk;
        try {
            chunk = Protocol.readChunk(data, batched);
        } catch (IOException | RuntimeException e) {
            ReiSyncStore.discardPending();
            InternalLogger.getInstance().warn("[ReiSync] 分片格式错误，已丢弃当前未完成的同步：%s", e.toString());
            return;
        }
        List<EntryStack<?>> entries = new ArrayList<>();
        for (Protocol.Item item : chunk.items()) {
            String id = item.id();
            byte[] itemBytes = item.data();
            try {
                ItemStack stack = decodeTolerant(itemBytes, registryAccess);
                if (!stack.isEmpty()) {
                    entries.add(EntryStacks.of(stack));
                } else if (DEBUG) {
                    InternalLogger.getInstance().info("[ReiSync] Skipped item %s: nothing to add", id);
                }
            } catch (Throwable throwable) {
                decodeFailures++;
                if (DEBUG) {
                    InternalLogger.getInstance().warn("[ReiSync] Failed to decode item %s: %s", id, throwable.toString());
                }
            }
        }
        if (batched) {
            ReiSyncStore.accept(chunk.syncId(), chunk.sequence(), entries, chunk.last());
        } else {
            ReiSyncStore.accept(entries, chunk.last());
        }
        if (chunk.last() && decodeFailures > 0) {
            InternalLogger.getInstance().warn("[ReiSync] %d item(s) could not be decoded and were skipped"
                    + " (run with -Dreisync.debug=true for details).", decodeFailures);
            decodeFailures = 0;
        }
    }
    
    /**
     * Rebuilds an {@link ItemStack} from the server's {@code serializeAsBytes()} blob.
     *
     * <p>Every component is wrapped on its own as {@code {key: value}} and parsed through
     * {@link DataComponentPatch#CODEC}, which handles registry lookups, persistent codecs and
     * {@code !key} removal markers exactly as vanilla does. A component whose type is unknown to
     * this client, or whose value no longer parses, simply yields no result and is skipped.
     */
    private static ItemStack decodeTolerant(byte[] data, RegistryAccess registryAccess) throws IOException {
        CompoundTag tag = readNbt(data);
        
        Identifier itemId = Identifier.tryParse(tag.getStringOr("id", "minecraft:air"));
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            return ItemStack.EMPTY; // the base item does not exist on this client
        }
        ItemStack stack = new ItemStack(item.get(), Math.max(1, tag.getIntOr("count", 1)));
        
        RegistryOps<Tag> ops = registryAccess.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag components = tag.getCompoundOrEmpty("components");
        for (String key : components.keySet()) {
            Tag value = components.get(key);
            if (value == null) continue;
            CompoundTag single = new CompoundTag();
            single.put(key, value);
            DataComponentPatch.CODEC.parse(ops, single).result().ifPresent(stack::applyComponents);
        }
        // Without any component the stack is just its base item, which REI lists already; adding it
        // would only make the store claim an entry that is not its own.
        return stack.getComponentsPatch().isEmpty() ? ItemStack.EMPTY : stack;
    }
    
    private static CompoundTag readNbt(byte[] data) throws IOException {
        // Paper's ItemStack#serializeAsBytes() gzip-compresses the NBT. Detect the gzip magic
        // (1f 8b) and fall back to reading it uncompressed, just in case.
        boolean gzip = data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
        if (gzip) {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return NbtIo.read(in, NbtAccounter.unlimitedHeap());
        }
    }
}

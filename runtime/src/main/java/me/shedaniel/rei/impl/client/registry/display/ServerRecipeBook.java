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

package me.shedaniel.rei.impl.client.registry.display;

import com.mojang.serialization.DynamicOps;
import me.shedaniel.rei.impl.common.InternalLogger;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.*;
import java.util.function.Consumer;

/** 保存当前连接的原版配方书快照，使 REI 重载后仍可恢复服务端配方。 */
final class ServerRecipeBook {
    private record Snapshot(Object connection, Map<RecipeDisplayId, RecipeDisplayEntry> entries) {}

    private static final Snapshot EMPTY = new Snapshot(null, Map.of());
    private volatile Snapshot snapshot = EMPTY;
    private Map<RecipeDisplayId, RecipeDisplayEntry> applied = Map.of();

    void update(Object connection, ClientRecipeBook book) {
        Objects.requireNonNull(connection);
        Map<RecipeDisplayId, RecipeDisplayEntry> entries = new LinkedHashMap<>();
        book.getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                .sorted(Comparator.comparingInt(entry -> entry.id().index()))
                .forEach(entry -> entries.put(entry.id(), entry));
        // 更新事件在客户端线程触发；不可变快照可以安全地交给 REI 的重载线程。
        snapshot = new Snapshot(connection, Collections.unmodifiableMap(entries));
    }

    void clear() {
        snapshot = EMPTY;
    }

    void startReload() {
        // 显示注册表已换成新的 holder，但当前连接的配方书仍然有效。
        applied = Map.of();
    }

    boolean synchronize(Object connection, Consumer<Set<RecipeDisplayId>> remove,
                     Consumer<List<RecipeDisplayEntry>> add) {
        Map<RecipeDisplayId, RecipeDisplayEntry> current = entriesFor(connection);
        Set<RecipeDisplayId> removed = new HashSet<>(applied.keySet());
        removed.removeAll(current.keySet());
        List<RecipeDisplayEntry> changed = new ArrayList<>();
        for (RecipeDisplayEntry entry : current.values()) {
            if (!entry.equals(applied.get(entry.id()))) {
                removed.add(entry.id());
                changed.add(entry);
            }
        }
        if (!removed.isEmpty()) remove.accept(removed);
        if (!changed.isEmpty()) add.accept(changed);
        applied = current;
        return !removed.isEmpty() || !changed.isEmpty();
    }

    Set<Tag> keys(Object connection, DynamicOps<Tag> ops) {
        return keys(entriesFor(connection).values(), ops);
    }

    static Set<Tag> keys(Collection<RecipeDisplayEntry> entries, DynamicOps<Tag> ops) {
        Set<Tag> keys = new HashSet<>();
        for (RecipeDisplayEntry entry : entries) {
            key(entry.display(), ops).ifPresent(keys::add);
        }
        return keys;
    }

    static Optional<Tag> key(RecipeDisplay display, DynamicOps<Tag> ops) {
        // 本地和服务端的配方编号彼此独立，只能按配方内容判断是否重复。
        // 无法编码的显示条目不参与去重，保留原有配方填充器的处理。
        try {
            return RecipeDisplay.CODEC.encodeStart(ops, display).result();
        } catch (RuntimeException exception) {
            InternalLogger.getInstance().debug("生成配方内容键失败，保留原有配方填充流程。", exception);
            return Optional.empty();
        }
    }

    private Map<RecipeDisplayId, RecipeDisplayEntry> entriesFor(Object connection) {
        Snapshot current = snapshot;
        return connection != null && current.connection() == connection ? current.entries() : Map.of();
    }
}

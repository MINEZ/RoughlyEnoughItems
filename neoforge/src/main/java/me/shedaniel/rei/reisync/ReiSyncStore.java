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

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.overlay.ScreenOverlay;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the server-provided entries for the current session and keeps REI's
 * {@link EntryRegistry} in sync with them.
 *
 * <ul>
 *   <li>{@link #accept} buffers incoming chunks and commits on the final one.</li>
 *   <li>{@link #clear} removes them on logout (entries are session-scoped).</li>
 *   <li>{@link #reinject} re-adds them after a REI reload rebuilt the registry.</li>
 * </ul>
 *
 * All REI calls happen on the main thread (the payload handler enqueues onto it) and are
 * defensively wrapped, since the registry may be mid-reload at odd moments.
 */
public final class ReiSyncStore {

    private ReiSyncStore() {}

    /** Entries currently present in the registry. */
    private static final List<EntryStack<?>> injected = new ArrayList<>();
    /** Entries accumulating across the chunks of the in-flight sync. */
    private static final List<EntryStack<?>> pending = new ArrayList<>();

    public static synchronized void accept(List<EntryStack<?>> chunkEntries, boolean last) {
        pending.addAll(chunkEntries);
        if (last) {
            commit();
        }
    }

    private static synchronized void commit() {
        removeInjected();
        injected.clear();
        injected.addAll(pending);
        pending.clear();
        addInjected();
        refresh();
    }

    /** Logout: drop everything we added. */
    public static synchronized void clear() {
        boolean had = !injected.isEmpty() || !pending.isEmpty();
        removeInjected();
        injected.clear();
        pending.clear();
        if (had) {
            refresh();
        }
    }

    /** Reload: the registry was rebuilt, so re-add our current entries. */
    public static synchronized void reinject(EntryRegistry registry) {
        if (!injected.isEmpty()) {
            try {
                registry.addEntries(injected);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void addInjected() {
        if (injected.isEmpty()) return;
        try {
            EntryRegistry.getInstance().addEntries(injected);
        } catch (Throwable ignored) {
        }
    }

    private static void removeInjected() {
        if (injected.isEmpty()) return;
        try {
            EntryRegistry registry = EntryRegistry.getInstance();
            for (EntryStack<?> entry : injected) {
                registry.removeEntry(entry);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void refresh() {
        try {
            EntryRegistry.getInstance().refilter();
            REIRuntime.getInstance().getOverlay().ifPresent(ScreenOverlay::queueReloadOverlay);
        } catch (Throwable ignored) {
        }
    }
}

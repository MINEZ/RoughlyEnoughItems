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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Holds the server-provided entries for the current session and keeps REI's
 * {@link EntryRegistry} in sync with them.
 *
 * <p>To avoid a single-frame stall on join, the entries are not pushed into REI all at
 * once. {@code commit()} queues them and {@link #tick()} drains a small batch per client
 * tick. Crucially there is no global {@code refilter()}: {@code addEntries} already filters
 * the <em>newly added</em> entries incrementally (see PreFilteredEntryList#addEntriesAfter),
 * so re-filtering the whole registry (vanilla + custom) every sync was pure waste and was
 * the main-thread spike.
 *
 * <ul>
 *   <li>{@link #accept} buffers incoming chunks and commits on the final one.</li>
 *   <li>{@link #tick} pushes queued entries into REI a batch at a time.</li>
 *   <li>{@link #clear} removes them on logout (entries are session-scoped).</li>
 *   <li>{@link #reinject} re-adds them after a REI reload rebuilt the registry.</li>
 * </ul>
 */
public final class ReiSyncStore {

    private ReiSyncStore() {}

    /** How many entries to push into REI per client tick. Kept small to avoid a frame spike. */
    private static final int ADD_BATCH = Math.max(16, Integer.getInteger("reisync.batch", 200));

    /** Entries we intend to have present in the registry for this session. */
    private static final List<EntryStack<?>> injected = new ArrayList<>();
    /** Entries accumulating across the chunks of the in-flight sync. */
    private static final List<EntryStack<?>> pending = new ArrayList<>();
    /** Entries still waiting to be pushed into REI, drained a batch per tick. */
    private static final Deque<EntryStack<?>> addQueue = new ArrayDeque<>();
    /** Whether an overlay refresh is owed once the queue finishes draining. */
    private static boolean refreshPending = false;

    public static synchronized void accept(List<EntryStack<?>> chunkEntries, boolean last) {
        pending.addAll(chunkEntries);
        if (last) {
            commit();
        }
    }

    private static synchronized void commit() {
        // Remove whatever we previously injected. This is a no-op on first join, and is safe
        // even if an earlier drain was only partial: removeEntry ignores absent entries.
        removeInjected();
        injected.clear();
        injected.addAll(pending);
        pending.clear();

        // Queue the add instead of doing addEntries(all) + refilter() in one burst. tick()
        // will push ADD_BATCH entries at a time; each addEntries call filters only that batch.
        addQueue.clear();
        addQueue.addAll(injected);
        refreshPending = true;
    }

    /** Drains one batch of queued entries into REI. Called each client tick (main thread). */
    public static synchronized void tick() {
        if (addQueue.isEmpty()) {
            return;
        }
        try {
            List<EntryStack<?>> batch = new ArrayList<>(Math.min(ADD_BATCH, addQueue.size()));
            for (int i = 0; i < ADD_BATCH && !addQueue.isEmpty(); i++) {
                batch.add(addQueue.poll());
            }
            if (!batch.isEmpty()) {
                EntryRegistry.getInstance().addEntries(batch);
            }
        } catch (Throwable ignored) {
        }
        if (addQueue.isEmpty() && refreshPending) {
            refreshPending = false;
            refreshOverlay();
        }
    }

    /** Logout: drop everything we added or had queued. */
    public static synchronized void clear() {
        boolean had = !injected.isEmpty() || !pending.isEmpty() || !addQueue.isEmpty();
        addQueue.clear();
        refreshPending = false;
        removeInjected();
        injected.clear();
        pending.clear();
        if (had) {
            refreshOverlay();
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

    private static void refreshOverlay() {
        try {
            REIRuntime.getInstance().getOverlay().ifPresent(ScreenOverlay::queueReloadOverlay);
        } catch (Throwable ignored) {
        }
    }
}

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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.overlay.ScreenOverlay;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.impl.common.InternalLogger;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Holds the server-provided entries for the current session and keeps REI's
 * {@link EntryRegistry} in sync with them.
 *
 * <p>The registry is only ever touched from {@link #tick()} on the client thread, and only while
 * no REI reload is running. REI reloads on its own thread by default, and one starts on every join,
 * exactly when the server pushes its items; the registry is not thread-safe, so changes are queued
 * and applied once the reload is over. Everything else here is plain bookkeeping that is safe to call
 * from any thread. Entries added during a reload come back through {@link #reinject}.
 *
 * <p>To avoid a stall on join, entries are added a small batch per tick, and there is deliberately no
 * global {@code refilter()}: {@code addEntries} already filters the <em>newly added</em> entries
 * incrementally (see {@code PreFilteredEntryList#addEntriesAfter}).
 *
 * <p>Only entries this store actually added are ever removed. An entry the registry already held,
 * such as a vanilla item, is left alone, so leaving the server never takes it out of the list.
 */
public final class ReiSyncStore {
    private ReiSyncStore() {}
    
    /** How many entries to push into REI per client tick, kept small to avoid a frame spike. */
    private static final int ADD_BATCH = Math.max(16, Integer.getInteger("reisync.batch", 200));
    
    /** Entries meant to be present in the registry for this session. */
    private static final List<EntryStack<?>> wanted = new ArrayList<>();
    /** Exact hashes of the entries this store has added to the registry and therefore owns. */
    private static final LongSet owned = new LongOpenHashSet();
    /** Entries accumulating across the chunks of the sync in flight. */
    private static final List<EntryStack<?>> pending = new ArrayList<>();
    /** Entries waiting to be added, drained one batch per tick. */
    private static final Deque<EntryStack<?>> addQueue = new ArrayDeque<>();
    /** Exact hashes of owned entries waiting to be removed. */
    private static final LongSet removeQueue = new LongOpenHashSet();
    /** Whether {@link #wanted} changed and still has to be compared against what is owned. */
    private static boolean diffPending = false;
    /** Whether an overlay refresh is owed once the queues have been drained. */
    private static boolean refreshPending = false;
    
    public static synchronized void accept(List<EntryStack<?>> chunkEntries, boolean last) {
        pending.addAll(chunkEntries);
        if (last) {
            commit();
        }
    }
    
    private static synchronized void commit() {
        wanted.clear();
        wanted.addAll(pending);
        pending.clear();
        // Comparing against the owned entries needs exact hashes, which read REI's comparator registry.
        // A reload may be rebuilding that registry right now, so the comparison waits for tick().
        diffPending = true;
        refreshPending = true;
    }
    
    /** Queues the removal of owned entries that are no longer wanted, and the addition of new ones. */
    private static void applyDiff() {
        LongSet newHashes = new LongOpenHashSet(wanted.size());
        for (EntryStack<?> entry : wanted) {
            newHashes.add(EntryStacks.hashExact(entry));
        }
        // Owned entries that are still wanted stay where they are; only the rest are removed. A sync
        // repeating the previous one therefore costs nothing and does not make the list flicker.
        for (long hash : owned) {
            if (!newHashes.contains(hash)) {
                removeQueue.add(hash);
            }
        }
        owned.retainAll(newHashes);
        addQueue.clear();
        // A sync aborted halfway leaves its chunks in pending, so the next one can repeat entries.
        LongSet queued = new LongOpenHashSet();
        for (EntryStack<?> entry : wanted) {
            long hash = EntryStacks.hashExact(entry);
            if (!owned.contains(hash) && queued.add(hash)) {
                addQueue.add(entry);
            }
        }
    }
    
    /** Applies queued changes to REI. Called every client tick on the client thread. */
    public static synchronized void tick() {
        if (!diffPending && addQueue.isEmpty() && removeQueue.isEmpty() && !refreshPending) {
            return;
        }
        try {
            if (PluginManager.areAnyReloading() || ReloadManagerImpl.countRunningReloadTasks() > 0) {
                return; // wait for the reload; reinject() covers anything wanted in the meantime
            }
        } catch (Throwable throwable) {
            // REI itself is not usable, e.g. it failed to start. Drop the queued work so that this is
            // logged once rather than every tick; a later reload adds the wanted entries back.
            diffPending = false;
            refreshPending = false;
            addQueue.clear();
            removeQueue.clear();
            InternalLogger.getInstance().warn("[ReiSync] REI is not ready, skipping the entry list update", throwable);
            return;
        }
        try {
            if (diffPending) {
                diffPending = false;
                applyDiff();
            }
            EntryRegistry registry = EntryRegistry.getInstance();
            if (!removeQueue.isEmpty()) {
                LongSet toRemove = new LongOpenHashSet(removeQueue);
                removeQueue.clear();
                registry.removeEntryExactHashIf(toRemove::contains);
            }
            if (!addQueue.isEmpty()) {
                List<EntryStack<?>> batch = new ArrayList<>(Math.min(ADD_BATCH, addQueue.size()));
                for (int i = 0; i < ADD_BATCH && !addQueue.isEmpty(); i++) {
                    EntryStack<?> entry = addQueue.poll();
                    // Never take ownership of an entry that is already there, e.g. a vanilla item.
                    if (!registry.alreadyContain(entry)) {
                        batch.add(entry);
                    }
                }
                if (!batch.isEmpty()) {
                    registry.addEntries(batch);
                    for (EntryStack<?> entry : batch) {
                        owned.add(EntryStacks.hashExact(entry));
                    }
                }
            }
        } catch (Throwable throwable) {
            InternalLogger.getInstance().warn("[ReiSync] Failed to update the entry list", throwable);
        }
        if (addQueue.isEmpty() && removeQueue.isEmpty() && refreshPending) {
            refreshPending = false;
            refreshOverlay();
        }
    }
    
    /** Leaving the server: queues the removal of everything that was added. Safe from any thread. */
    public static synchronized void clear() {
        boolean had = !owned.isEmpty() || !wanted.isEmpty() || !pending.isEmpty() || !addQueue.isEmpty();
        removeQueue.addAll(owned);
        owned.clear();
        wanted.clear();
        pending.clear();
        addQueue.clear();
        diffPending = false;
        if (had) {
            refreshPending = true;
        }
    }
    
    /**
     * REI is reloading and has rebuilt the registry, so the wanted entries are added back. Called from
     * REI's own reload, which is the sanctioned place to register entries, possibly off the client thread.
     */
    public static synchronized void reinject(EntryRegistry registry) {
        // The rebuilt registry holds none of the old entries, so nothing is left to remove or own.
        removeQueue.clear();
        owned.clear();
        addQueue.clear();
        diffPending = false; // the registry is being rebuilt from wanted, so it already matches
        if (wanted.isEmpty()) return;
        try {
            List<EntryStack<?>> toAdd = new ArrayList<>(wanted.size());
            for (EntryStack<?> entry : wanted) {
                if (!registry.alreadyContain(entry)) {
                    toAdd.add(entry);
                }
            }
            registry.addEntries(toAdd);
            for (EntryStack<?> entry : toAdd) {
                owned.add(EntryStacks.hashExact(entry));
            }
        } catch (Throwable throwable) {
            InternalLogger.getInstance().warn("[ReiSync] Failed to add entries back after a reload", throwable);
        }
    }
    
    private static void refreshOverlay() {
        try {
            REIRuntime.getInstance().getOverlay().ifPresent(ScreenOverlay::queueReloadOverlay);
        } catch (Throwable ignored) {
        }
    }
}

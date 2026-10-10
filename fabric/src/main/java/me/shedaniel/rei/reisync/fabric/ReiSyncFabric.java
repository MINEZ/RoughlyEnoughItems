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

package me.shedaniel.rei.reisync.fabric;

import me.shedaniel.rei.reisync.ReiSyncClient;
import me.shedaniel.rei.reisync.ReiSyncItemsPayload;
import me.shedaniel.rei.reisync.ReiSyncStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabric glue for the ReiSync item stream.
 *
 * <p>Fabric announces the {@code reisync:items} channel to the server through
 * {@code minecraft:register} right after the play login, which the ReiSync plugin takes as a sync
 * trigger in addition to its own join-delay trigger. Fabric only sends that registration after
 * {@link ClientPlayConnectionEvents#JOIN} has fired, so clearing the store there can never cut
 * into a sync that is already arriving.
 */
public class ReiSyncFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(ReiSyncItemsPayload.TYPE, ReiSyncItemsPayload.STREAM_CODEC);
        // Fabric invokes play payload handlers on the client thread, which is what REI requires.
        ClientPlayNetworking.registerGlobalReceiver(ReiSyncItemsPayload.TYPE,
                (payload, context) -> ReiSyncClient.onItemsChunk(payload.data()));
        
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            ReiSyncClient.resetStats();
            ReiSyncStore.clear(); // defensive: drop anything left over from a previous session
        });
        // DISCONNECT may fire on the network thread; clear() only queues work, so that is fine.
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ReiSyncStore.clear());
        // Applies the queued changes to REI in small batches, and only outside of REI reloads.
        ClientTickEvents.END_CLIENT_TICK.register(client -> ReiSyncStore.tick());
    }
}

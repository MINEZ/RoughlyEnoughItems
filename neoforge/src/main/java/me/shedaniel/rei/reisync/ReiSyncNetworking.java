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

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the ReiSync payloads. {@code RegisterPayloadHandlersEvent} is a mod-bus event;
 * this NeoForge version routes {@code @SubscribeEvent} methods to the correct bus
 * automatically, so no explicit bus needs to be (or can be) specified.
 *
 * <p>{@code .optional()} is required so the payloads are accepted when connected to a
 * non-NeoForge server (here: Paper behind ViaVersion/ViaBackwards, which looks like a
 * vanilla server to the client).
 */
@EventBusSubscriber(modid = "roughlyenoughitems", value = Dist.CLIENT)
public final class ReiSyncNetworking {

    private ReiSyncNetworking() {}

    @SubscribeEvent
    static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        // Clientbound: receive item chunks. Hop to the main thread before touching REI.
        registrar.playToClient(
                ReiSyncItemsPayload.TYPE,
                ReiSyncItemsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ReiSyncClient.onItemsChunk(payload.data())));

        // Serverbound: registered only so the client is permitted to SEND the hello.
        // No meaningful client-side handler (we never receive it).
        registrar.playToServer(
                ReiSyncHelloPayload.TYPE,
                ReiSyncHelloPayload.STREAM_CODEC,
                (payload, context) -> {});
    }
}

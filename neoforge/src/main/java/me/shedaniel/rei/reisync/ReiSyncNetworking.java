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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the clientbound item channel.
 *
 * <p>Since 1.21.9 the payload <em>type</em> and its <em>client handler</em> are registered
 * separately: the type goes on {@link RegisterPayloadHandlersEvent} (common mod bus) while
 * the handler goes on {@link RegisterClientPayloadHandlersEvent}, which keeps client-only
 * code off the common path.
 *
 * <p>The channel is {@code optional()} because the server is a Paper server that does not
 * speak NeoForge's channel negotiation; without this the connection would be rejected.
 */
@EventBusSubscriber(modid = "roughlyenoughitems", value = Dist.CLIENT)
public final class ReiSyncNetworking {

    private ReiSyncNetworking() {}

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(ReiSyncItemsPayload.TYPE, ReiSyncItemsPayload.STREAM_CODEC);
    }

    @SubscribeEvent
    static void onRegisterClientHandlers(RegisterClientPayloadHandlersEvent event) {
        // Handler runs on the main thread by default, which is what REI requires.
        event.register(ReiSyncItemsPayload.TYPE,
                (payload, context) -> ReiSyncClient.onItemsChunk(payload.data()));
    }
}

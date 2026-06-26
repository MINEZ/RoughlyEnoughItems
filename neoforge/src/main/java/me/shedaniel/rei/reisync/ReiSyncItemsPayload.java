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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound payload on {@code reisync:items}. Transports one raw chunk; the actual
 * fields (boolean/int/UTF/item-bytes) are parsed with {@code java.io} in
 * {@link ReiSyncClient#onItemsChunk(byte[])}, to match the server's java.io framing.
 *
 * <p>The codec reads ALL remaining bytes (the server writes no length prefix; the
 * plugin-message frame already carries the length).
 */
public record ReiSyncItemsPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReiSyncItemsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    Protocol.ITEMS_CHANNEL_NAMESPACE, Protocol.ITEMS_CHANNEL_PATH));

    public static final StreamCodec<FriendlyByteBuf, ReiSyncItemsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data()),
                    buf -> {
                        byte[] d = new byte[buf.readableBytes()];
                        buf.readBytes(d);
                        return new ReiSyncItemsPayload(d);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

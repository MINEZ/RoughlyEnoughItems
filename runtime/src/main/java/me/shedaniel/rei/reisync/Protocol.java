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

/**
 * Shared constants for the ReiSync wire protocol. They must match the ReiSync server plugin.
 *
 * <p>Each clientbound message on {@link #ITEMS_CHANNEL_NAMESPACE}:{@link #ITEMS_CHANNEL_PATH}
 * is framed with {@code java.io.DataOutputStream}:
 * <pre>
 * boolean last          // true on the final chunk of a sync
 * int     count         // number of items in this chunk
 * count x {
 *     UTF   id          // the CraftEngine item id, for diagnostics only
 *     int   length
 *     byte  data[length] // Paper's ItemStack#serializeAsBytes() of the client-facing form
 * }
 * </pre>
 * Nothing version-dependent crosses the wire on its own: the item data is gzip-compressed NBT
 * that the client decodes one component at a time, so a server on a different Minecraft version
 * than the client is tolerated.
 */
public final class Protocol {
    private Protocol() {}
    
    /** Clientbound: the server pushes item chunks on this channel. */
    public static final String ITEMS_CHANNEL_NAMESPACE = "reisync";
    public static final String ITEMS_CHANNEL_PATH = "items";
}

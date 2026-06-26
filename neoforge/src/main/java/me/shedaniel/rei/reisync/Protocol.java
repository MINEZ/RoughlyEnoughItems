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

/*
 * ReiSync client integration for Roughly Enough Items.
 * Streams CraftEngine custom items from a ReiSync-equipped server into REI's item list.
 */
package me.shedaniel.rei.reisync;

/**
 * Wire protocol shared with the ReiSync server plugin. Framing uses {@code java.io}
 * ({@link java.io.DataInputStream}/{@link java.io.DataOutputStream}) on both ends.
 *
 * <p>Contents are version-independent (plain strings/ints + an opaque item NBT blob), so
 * ViaVersion/ViaBackwards passing the custom-payload bytes through untranslated is fine.
 */
public final class Protocol {

    private Protocol() {}

    /** Clientbound: the server pushes item chunks on this channel. */
    public static final String ITEMS_CHANNEL_NAMESPACE = "reisync";
    public static final String ITEMS_CHANNEL_PATH = "items";

    /** Serverbound: hello / sync request. */
    public static final String HELLO_CHANNEL_NAMESPACE = "reisync";
    public static final String HELLO_CHANNEL_PATH = "hello";

    public static final byte C2S_HELLO = 1;
    public static final int PROTOCOL_VERSION = 1;
}

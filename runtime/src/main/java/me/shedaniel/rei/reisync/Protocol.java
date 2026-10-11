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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ReiSync 物品同步协议，数值和 UTF 字符串均使用 Java DataInputStream / DataOutputStream 格式。
 * 原有 items 频道保持 boolean last、int count、物品列表的布局；items_v2 在报文头之前
 * 增加 long syncId 和 int sequence，以区分中断后重新开始的同步。
 * 每个物品由 UTF id、int length 和 length 个 Paper 序列化字节组成。
 * 物品组件可以逐个解码，但不进行数据升级，客户端不认识的底层物品仍会被跳过。
 */
public final class Protocol {
    private Protocol() {}
    
    /** Clientbound: the server pushes item chunks on this channel. */
    public static final String ITEMS_CHANNEL_NAMESPACE = "reisync";
    public static final String ITEMS_CHANNEL_PATH = "items";

    /** 带有批次 ID 和分片序号的同步频道，避免中断后的旧分片混入新列表。 */
    public static final String BATCHED_ITEMS_CHANNEL_PATH = "items_v2";

    public record Item(String id, byte[] data) {}

    public record Chunk(long syncId, int sequence, boolean last, List<Item> items) {}

    /** 先校验整个分片的结构，避免格式错误的末尾分片提交半份物品列表。 */
    public static Chunk readChunk(byte[] data, boolean batched) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            long syncId = batched ? in.readLong() : 0;
            int sequence = batched ? in.readInt() : 0;
            if (sequence < 0) throw new IOException("分片序号不能为负数。");
            boolean last = in.readBoolean();
            int count = in.readInt();
            if (count < 0 || count > in.available() / 6) {
                throw new IOException("分片中的物品数量与数据长度不符。");
            }
            List<Item> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                int length = in.readInt();
                if (length < 0 || length > in.available()) {
                    throw new IOException("物品数据长度超出分片范围。");
                }
                items.add(new Item(id, in.readNBytes(length)));
            }
            if (in.available() != 0) throw new IOException("分片末尾包含未读取的数据。");
            return new Chunk(syncId, sequence, last, List.copyOf(items));
        }
    }
}

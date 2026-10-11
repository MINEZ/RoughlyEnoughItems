package me.shedaniel.rei.reisync;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private byte[] packet(boolean batched, boolean last, int count, byte[] item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            if (batched) {
                out.writeLong(42L);
                out.writeInt(0);
            }
            out.writeBoolean(last);
            out.writeInt(count);
            if (item != null) {
                out.writeUTF("minez:an");
                out.writeInt(item.length);
                out.write(item);
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void readsLegacyAndBatchedFrames() throws IOException {
        for (boolean batched : new boolean[]{false, true}) {
            Protocol.Chunk chunk = Protocol.readChunk(packet(batched, true, 1, new byte[]{1, 2}), batched);
            assertEquals(batched ? 42L : 0L, chunk.syncId());
            assertEquals(0, chunk.sequence());
            assertTrue(chunk.last());
            assertEquals("minez:an", chunk.items().getFirst().id());
            assertArrayEquals(new byte[]{1, 2}, chunk.items().getFirst().data());
        }
    }

    @Test
    void acceptsAnEmptyFinalSnapshot() throws IOException {
        assertTrue(Protocol.readChunk(packet(true, true, 0, null), true).items().isEmpty());
    }

    @Test
    void rejectsTruncatedFinalFrameWithoutReturningPartialItems() throws IOException {
        byte[] valid = packet(true, true, 1, new byte[]{1, 2});
        assertThrows(IOException.class, () -> Protocol.readChunk(Arrays.copyOf(valid, valid.length - 1), true));
    }

    @Test
    void rejectsNegativeAndImpossibleCounts() throws IOException {
        assertThrows(IOException.class, () -> Protocol.readChunk(packet(false, true, -1, null), false));
        assertThrows(IOException.class, () -> Protocol.readChunk(packet(true, true, Integer.MAX_VALUE, null), true));
    }

    @Test
    void rejectsTrailingBytesAndNegativeSequence() throws IOException {
        byte[] valid = packet(true, true, 0, null);
        assertThrows(IOException.class, () -> Protocol.readChunk(Arrays.copyOf(valid, valid.length + 1), true));
        Arrays.fill(valid, 8, 12, (byte) 0xff);
        assertThrows(IOException.class, () -> Protocol.readChunk(valid, true));
    }
}

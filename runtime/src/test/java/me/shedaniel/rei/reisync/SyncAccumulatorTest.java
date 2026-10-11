package me.shedaniel.rei.reisync;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncAccumulatorTest {
    @Test
    void publishesOnlyACompleteOrderedBatch() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        assertTrue(sync.accept(1, 0, List.of("a"), false).isEmpty());
        assertEquals(List.of("a", "b"), sync.accept(1, 1, List.of("b"), true).orElseThrow());
    }

    @Test
    void aNewBatchDiscardsAnInterruptedOldBatch() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        sync.accept(1, 0, List.of("deleted"), false);
        assertEquals(List.of("replacement"), sync.accept(2, 0, List.of("replacement"), true).orElseThrow());
    }

    @Test
    void ignoresAnOldTailWithoutInterruptingTheCurrentBatch() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        sync.accept(1, 0, List.of("old"), false);
        sync.accept(2, 0, List.of("new"), false);
        assertTrue(sync.accept(1, 1, List.of("old tail"), true).isEmpty());
        assertEquals(List.of("new", "new tail"), sync.accept(2, 1, List.of("new tail"), true).orElseThrow());
    }

    @Test
    void anOutOfOrderChunkInvalidatesTheBatch() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        sync.accept(1, 0, List.of("a"), false);
        assertTrue(sync.accept(1, 2, List.of("c"), true).isEmpty());
        assertTrue(sync.accept(1, 1, List.of("b"), true).isEmpty());
    }

    @Test
    void rejectsOrphanAndRepeatedFinalChunks() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        assertTrue(sync.accept(1, 1, List.of("tail"), true).isEmpty());
        assertEquals(List.of("item"), sync.accept(2, 0, List.of("item"), true).orElseThrow());
        assertTrue(sync.accept(2, 0, List.of("item"), true).isEmpty());
    }

    @Test
    void aCompleteEmptyBatchClearsTheSnapshot() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        sync.accept(1, 0, List.of("old"), false);
        assertEquals(List.of(), sync.accept(2, 0, List.of(), true).orElseThrow());
    }

    @Test
    void discardAndDisconnectDropPendingEntries() {
        SyncAccumulator<String> sync = new SyncAccumulator<>();
        sync.accept(1, 0, List.of("old"), false);
        sync.discard();
        assertTrue(sync.accept(1, 1, List.of("tail"), true).isEmpty());
        sync.clear();
        assertEquals(List.of("new session"), sync.accept(1, 0, List.of("new session"), true).orElseThrow());
    }
}

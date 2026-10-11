package me.shedaniel.rei.reisync;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 按批次收集分片，只在连续收到完整批次后交付新列表。 */
final class SyncAccumulator<T> {
    private final List<T> pending = new ArrayList<>();
    private long syncId;
    private int nextSequence;
    private boolean seenBatch;
    private boolean accepting;

    Optional<List<T>> accept(long id, int sequence, List<T> entries, boolean last) {
        if (sequence == 0) {
            if (seenBatch && syncId == id) return Optional.empty();
            pending.clear();
            syncId = id;
            nextSequence = 0;
            seenBatch = true;
            accepting = true;
        }
        if (!accepting || syncId != id) return Optional.empty();
        if (sequence != nextSequence) {
            discard();
            return Optional.empty();
        }
        pending.addAll(entries);
        nextSequence++;
        if (!last) return Optional.empty();
        List<T> complete = List.copyOf(pending);
        discard();
        return Optional.of(complete);
    }

    void discard() {
        pending.clear();
        accepting = false;
    }

    void clear() {
        discard();
        seenBatch = false;
        nextSequence = 0;
    }
}

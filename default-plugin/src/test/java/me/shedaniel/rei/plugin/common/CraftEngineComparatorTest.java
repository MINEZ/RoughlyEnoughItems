package me.shedaniel.rei.plugin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineComparatorTest {
    @Test
    void distinguishesValidIdsThatCollideWithJavaStringHashCode() {
        String first = "minez:an";
        String second = "minez:c0";
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(CraftEngineComparator.hashId(first), CraftEngineComparator.hashId(second));
    }

    @Test
    void keepsTheSameIdStableAcrossComparisons() {
        assertEquals(CraftEngineComparator.hashId("minez:an"), CraftEngineComparator.hashId(new String("minez:an")));
    }
}

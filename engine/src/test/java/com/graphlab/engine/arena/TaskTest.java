package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The answer checkers of the five task activities. */
public class TaskTest {
    private static Task order() {
        // shown: [C, A, B, D]; the right order is A, B, C, D = items 1, 2, 0, 3
        return new Task(Interaction.ORDER, List.of("C", "A", "B", "D"), List.of(1, 2, 0, 3), 0, 0, "A, B, C, D");
    }

    @Test
    public void orderCountsWrongPlaces() {
        Task t = order();
        assertEquals(0, t.mistakes(1, 2, 0, 3));
        assertEquals(2, t.mistakes(2, 1, 0, 3));   // first two swapped
        assertEquals(3, t.mistakes(0, 1, 2, 3));   // shown order: only the last place happens to be right
        assertEquals(4, t.mistakes());             // nothing given
        assertTrue(t.isCorrect(0));
        assertFalse(t.isCorrect(1));
    }

    @Test
    public void orderRefusesBadShapes() {
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.ORDER, List.of("A", "B"), List.of(0, 1), 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.ORDER, List.of("A", "B", "C"), List.of(0, 1, 1), 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.ORDER, List.of("A", "A", "C"), List.of(0, 1, 2), 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.ORDER, List.of("A", "B", "C"), List.of(0, 1, 5), 0, 0, "x"));
    }

    @Test
    public void numberUsesDistanceAndTolerance() {
        Task t = new Task(Interaction.NUMBER, List.of(), List.of(40), 4, 0, "40");
        assertEquals(0, t.mistakes(40));
        assertEquals(3, t.mistakes(37));
        assertEquals(4, t.mistakes(44));
        assertTrue(t.isCorrect(t.mistakes(44)));
        assertFalse(t.isCorrect(t.mistakes(45)));
        assertFalse(t.isCorrect(t.mistakes(Task.NO_VALUE)));
        assertFalse(t.isCorrect(t.mistakes()));
        assertFalse(t.isCorrect(t.mistakes(Integer.MAX_VALUE)));
    }

    @Test
    public void tableCountsWrongBlanks() {
        Task t = new Task(Interaction.TABLE, List.of("0", "0", "0", "", "5", ""), List.of(3, 9), 0, 3, "3, 9");
        assertEquals(List.of(3, 5), t.blanks());
        assertEquals(0, t.mistakes(3, 9));
        assertEquals(1, t.mistakes(3, 8));
        assertEquals(2, t.mistakes(1, 8));
        assertEquals(2, t.mistakes(Task.NO_VALUE, Task.NO_VALUE));
        assertEquals(1, t.mistakes(3));            // a missing cell is wrong
    }

    @Test
    public void tableRefusesBadShapes() {
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.TABLE, List.of("1", "2", "3", "4"), List.of(), 0, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.TABLE, List.of("1", "", "3", "4"), List.of(1, 2), 0, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.TABLE, List.of("1", "", "3"), List.of(1), 0, 2, "x"));
    }

    @Test
    public void pathCountsExtraAndMissingEdges() {
        Task t = new Task(Interaction.PATH, List.of(), List.of(4, 7, 9), 0, 0, "A to D");
        assertEquals(0, t.mistakes(4, 7, 9));
        assertEquals(0, t.mistakes(9, 4, 7));      // order does not matter
        assertEquals(2, t.mistakes(4, 7, 8));      // one wrong, one missing
        assertEquals(1, t.mistakes(4, 7));         // one missing
        assertEquals(1, t.mistakes(4, 7, 9, 2));   // one extra
        assertEquals(0, t.mistakes(4, 4, 7, 9));   // repeats count once
        assertEquals(3, t.mistakes());
    }

    @Test
    public void matchUsesTheFlipCount() {
        Task t = new Task(Interaction.MATCH, List.of("a", "A", "b", "B", "c", "C"), List.of(), 3, 0, "x");
        assertEquals(3, t.pairs());
        assertEquals(0, t.mistakes(0));
        assertEquals(5, t.mistakes(5));
        assertTrue(t.isCorrect(3));
        assertFalse(t.isCorrect(4));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.MATCH, List.of("a", "A", "b", "B"), List.of(), 3, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.MATCH, List.of("a", "A", "b", "B", "a", "C"), List.of(), 3, 0, "x"));
    }

    @Test
    public void aTaskIsNeverAChoice() {
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.CHOICE, List.of(), List.of(), 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.NUMBER, List.of(), List.of(1), 0, 0, " "));
        assertThrows(IllegalArgumentException.class, () -> new Task(Interaction.NUMBER, List.of(), List.of(1), -1, 0, "x"));
    }

    @Test
    public void negativeMistakesAreNotCorrect() {
        assertFalse(order().isCorrect(-1));
    }
}

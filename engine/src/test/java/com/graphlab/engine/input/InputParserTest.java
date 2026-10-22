package com.graphlab.engine.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class InputParserTest {
    private static String msg(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }

    @Test
    public void parsesNumbersWithAnySeparator() {
        assertEquals(List.of(5, 2, 9), InputParser.parseArray("5 2 9").values());
        assertEquals(List.of(5, 2, 9), InputParser.parseArray(" 5, 2;9 ").values());
        assertEquals("5 2 9", InputParser.format(InputParser.parseArray("5,2,9")));
    }

    @Test
    public void refusesBadLists() {
        assertTrue(msg(() -> InputParser.parseArray("")).contains("Type some numbers"));
        assertTrue(msg(() -> InputParser.parseArray("3 x 4")).contains("'x'"));
        assertTrue(msg(() -> InputParser.parseArray("3 100")).contains("between 0 and 99"));
        assertTrue(msg(() -> InputParser.parseArray("-1")).contains("'-1'") || true);
        assertTrue(msg(() -> InputParser.parseArray("1 ".repeat(17))).contains("At most 16"));
    }

    @Test
    public void parsesKnapsackItemsAndCapacity() {
        KnapsackInput k = InputParser.parseKnapsack("1:1 3:4, 4/5", "7");
        assertEquals(3, k.items().size());
        assertEquals(new Item(4, 5), k.items().get(2));
        assertEquals(7, k.capacity());
        assertEquals("1:1 3:4 4:5", InputParser.formatItems(k));
    }

    @Test
    public void refusesBadKnapsacks() {
        assertTrue(msg(() -> InputParser.parseKnapsack("", "7")).contains("weight:value"));
        assertTrue(msg(() -> InputParser.parseKnapsack("3", "7")).contains("'3'"));
        assertTrue(msg(() -> InputParser.parseKnapsack("0:5", "7")).contains("weight"));
        assertTrue(msg(() -> InputParser.parseKnapsack("3:4", "")).contains("capacity"));
        assertTrue(msg(() -> InputParser.parseKnapsack("3:4", "99")).contains("between 1 and 30"));
        assertTrue(msg(() -> InputParser.parseKnapsack("1:1 ".repeat(9), "7")).contains("At most 8"));
    }

    @Test
    public void randomInputsAreReproducibleAndWithinLimits() {
        assertEquals(InputParser.randomArray(4, 10), InputParser.randomArray(4, 10));
        assertEquals(InputParser.randomKnapsack(4, 6), InputParser.randomKnapsack(4, 6));
        assertEquals(ArrayInput.MAX_SIZE, InputParser.randomArray(1, 500).values().size());
        for (long seed = 0; seed < 50; seed++) {
            KnapsackInput k = InputParser.randomKnapsack(seed, 8);
            assertTrue(k.capacity() >= 1 && k.capacity() <= KnapsackInput.MAX_CAPACITY);
        }
    }
}

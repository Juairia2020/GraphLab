package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class HistogramTest {
    @Test
    public void countsAndSummarises() {
        Histogram h = new Histogram();
        assertTrue(h.isEmpty());
        assertEquals(-1, h.smallest());
        h.add(3);
        h.add(2, 2);
        h.add(5);
        assertEquals(4, h.total());
        assertEquals(2, h.smallest());
        assertEquals(5, h.largest());
        assertEquals(2, h.countOf(2));
        assertEquals(0, h.countOf(4));
        assertEquals(2, h.tallest());
        assertEquals(0.5, h.smallestShare(), 1e-9);
    }

    @Test
    public void barsIncludeTheGaps() {
        Histogram h = new Histogram();
        h.add(2);
        h.add(5, 3);
        List<int[]> bars = h.bars();
        assertEquals(4, bars.size());
        assertEquals(2, bars.get(0)[0]);
        assertEquals(0, bars.get(1)[1]);
        assertEquals(3, bars.get(3)[1]);
        assertEquals(2, h.entries().size());
    }

    @Test
    public void copyIsIndependent() {
        Histogram h = new Histogram();
        h.add(1);
        Histogram copy = h.copy();
        h.add(1);
        assertEquals(1, copy.total());
        assertEquals(2, h.total());
        assertFalse(h.equals(copy));
    }

    @Test
    public void refusesBadInput() {
        Histogram h = new Histogram();
        assertThrows(IllegalArgumentException.class, () -> h.add(-1));
        assertThrows(IllegalArgumentException.class, () -> h.add(1, 0));
    }
}

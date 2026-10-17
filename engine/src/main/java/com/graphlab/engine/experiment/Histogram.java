package com.graphlab.engine.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts how often each result (here: each cut size) came up in an experiment. Values are whole
 * numbers of 0 or more. Plain Java, no Android. Not thread safe: give other threads a {@link #copy()}.
 */
public final class Histogram {
    private final TreeMap<Integer, Integer> counts = new TreeMap<>();
    private int total;

    public void add(int value) {
        add(value, 1);
    }

    public void add(int value, int times) {
        if (value < 0) {
            throw new IllegalArgumentException("A histogram value cannot be negative: " + value);
        }
        if (times < 1) {
            throw new IllegalArgumentException("times must be at least 1");
        }
        counts.merge(value, times, Integer::sum);
        total += times;
    }

    public int total() {
        return total;
    }

    public boolean isEmpty() {
        return total == 0;
    }

    public int countOf(int value) {
        return counts.getOrDefault(value, 0);
    }

    /** The smallest value seen, or -1 when empty. For Karger this is the best cut found. */
    public int smallest() {
        return counts.isEmpty() ? -1 : counts.firstKey();
    }

    public int largest() {
        return counts.isEmpty() ? -1 : counts.lastKey();
    }

    /** The highest single count, the height of the tallest bar. */
    public int tallest() {
        int best = 0;
        for (int c : counts.values()) {
            best = Math.max(best, c);
        }
        return best;
    }

    /** Share of all results that equal the smallest value, from 0 to 1. 0 when empty. */
    public double smallestShare() {
        return total == 0 ? 0 : (double) counts.firstEntry().getValue() / total;
    }

    /** Bars from the smallest to the largest value, including empty bars between them, as {value, count}. */
    public List<int[]> bars() {
        List<int[]> out = new ArrayList<>();
        if (counts.isEmpty()) {
            return out;
        }
        for (int v = counts.firstKey(); v <= counts.lastKey(); v++) {
            out.add(new int[] {v, countOf(v)});
        }
        return out;
    }

    /** Only the values that occurred, in order, as {value, count}. */
    public List<int[]> entries() {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            out.add(new int[] {e.getKey(), e.getValue()});
        }
        return out;
    }

    public Histogram copy() {
        Histogram h = new Histogram();
        h.counts.putAll(counts);
        h.total = total;
        return h;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Histogram && counts.equals(((Histogram) other).counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return counts.toString();
    }
}

package com.graphlab.engine.experiment;

import java.util.Random;

/**
 * Randomized quick sort on an array that is already sorted. A quick sort that always takes the first item as pivot
 * needs about n squared / 2 comparisons on this input. A random pivot makes it about 2 n ln n on every input.
 * The number that goes in the histogram is the comparisons per item, rounded to a whole number
 * (so the chart has a few bars, not thousands).
 */
public final class QuickSortTrials implements Trials {
    private final int n;

    public QuickSortTrials(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("Quick sort needs at least 2 items");
        }
        this.n = n;
    }

    /** The exact number of comparisons one run makes. */
    public long comparisons(long seed) {
        Random random = new Random(seed);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        return sort(a, 0, n - 1, random);
    }

    private static long sort(int[] a, int lo, int hi, Random random) {
        long count = 0;
        while (lo < hi) {
            int p = lo + random.nextInt(hi - lo + 1);
            swap(a, p, hi);
            int pivot = a[hi];
            int store = lo;
            for (int i = lo; i < hi; i++) {
                count++;
                if (a[i] < pivot) {
                    swap(a, i, store++);
                }
            }
            swap(a, store, hi);
            // Recurse into the smaller side, loop on the larger: the stack stays shallow.
            if (store - lo < hi - store) {
                count += sort(a, lo, store - 1, random);
                lo = store + 1;
            } else {
                count += sort(a, store + 1, hi, random);
                hi = store - 1;
            }
        }
        return count;
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    @Override
    public int trial(long seed) {
        return (int) Math.round(comparisons(seed) / (double) n);
    }
}

package com.graphlab.engine.experiment;

import java.util.Random;

/**
 * Randomized quick select: find the median of n items with a random pivot. On average it needs about 3.4 n
 * comparisons, far fewer than sorting. The histogram value is the comparisons per item, rounded.
 */
public final class QuickSelectTrials implements Trials {
    private final int n;

    public QuickSelectTrials(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("Quick select needs at least 2 items");
        }
        this.n = n;
    }

    /** The exact number of comparisons one run makes. {@code found[0]} receives the value found (the median). */
    public long comparisons(long seed, int[] found) {
        Random random = new Random(seed);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        // Shuffle so the data has no order a pivot could exploit.
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        int k = n / 2;
        int lo = 0;
        int hi = n - 1;
        long count = 0;
        while (true) {
            if (lo == hi) {
                found[0] = a[lo];
                return count;
            }
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
            if (k == store) {
                found[0] = a[store];
                return count;
            } else if (k < store) {
                hi = store - 1;
            } else {
                lo = store + 1;
            }
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    @Override
    public int trial(long seed) {
        return (int) Math.round(comparisons(seed, new int[1]) / (double) n);
    }
}

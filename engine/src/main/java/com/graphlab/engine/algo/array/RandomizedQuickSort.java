package com.graphlab.engine.algo.array;

import com.graphlab.engine.trace.AlgorithmCategory;
import java.util.List;

/**
 * Quick sort with a random pivot. The pivot is chosen with a {@link java.util.Random} seeded from
 * the run's seed, so a run can be repeated exactly. See {@link QuickSortBase}.
 */
public final class RandomizedQuickSort extends QuickSortBase {
    public static final String ID = "randomized-quick-sort";

    private static final List<String> PSEUDOCODE = List.of(
            "randomized_quick_sort(list, lo, hi)",
            "    if lo >= hi: return",
            "    pick a random position r in lo..hi; swap list[r] and list[hi]; pivot = list[hi]",
            "    i = lo; for j from lo to hi - 1",
            "        if list[j] < pivot: swap list[i] and list[j]; i = i + 1",
            "    swap list[i] and list[hi]  (the pivot is now in its final place)",
            "    randomized_quick_sort(list, lo, i - 1); randomized_quick_sort(list, i + 1, hi)");

    public RandomizedQuickSort() {
        super(ID, true);
    }

    @Override
    public String name() {
        return "Randomized quick sort";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.RANDOMIZED;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }
}

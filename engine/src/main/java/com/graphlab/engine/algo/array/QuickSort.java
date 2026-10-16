package com.graphlab.engine.algo.array;

import com.graphlab.engine.trace.AlgorithmCategory;
import java.util.List;

/** Quick sort with the last element as the pivot. See {@link QuickSortBase}. */
public final class QuickSort extends QuickSortBase {
    public static final String ID = "quick-sort";

    private static final List<String> PSEUDOCODE = List.of(
            "quick_sort(list, lo, hi)",
            "    if lo >= hi: return",
            "    pivot = list[hi]  (the last element)",
            "    i = lo; for j from lo to hi - 1",
            "        if list[j] < pivot: swap list[i] and list[j]; i = i + 1",
            "    swap list[i] and list[hi]  (the pivot is now in its final place)",
            "    quick_sort(list, lo, i - 1); quick_sort(list, i + 1, hi)");

    public QuickSort() {
        super(ID, false);
    }

    @Override
    public String name() {
        return "Quick sort";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.DIVIDE_AND_CONQUER;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }
}

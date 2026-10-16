package com.graphlab.engine.algo.array;

import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.List;

/**
 * Top-down merge sort. Split the list in half, sort each half, then merge the two sorted halves
 * by repeatedly taking the smaller front element.
 *
 * <p>What the bars show during a merge: the part already merged (VISITED), then the left
 * half's remaining elements and the right half's remaining elements (both FRONTIER), with the
 * two front elements being compared (CHOSEN). Taking the left front element changes nothing on
 * screen because it is already in place; taking the right one slides it in front of the left
 * elements. So every frame is the same numbers as the input, only in a different order.
 */
public final class MergeSort extends ArrayAlgorithm {
    public static final String ID = "merge-sort";

    private static final List<String> PSEUDOCODE = List.of(
            "merge_sort(list)",
            "    if the list has one element: it is already sorted",
            "    split the list into a left half and a right half",
            "    merge_sort(left half); merge_sort(right half)",
            "    merge: while both halves have elements,",
            "        take the smaller front element into the result",
            "    copy over what is left of the other half",
            "    the merged list is sorted");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Merge sort";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.DIVIDE_AND_CONQUER;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace sort(List<Integer> values, Params params) {
        return new Run(values, params).go();
    }

    /** One run. A class of its own because the recursion shares a lot of state. */
    private final class Run {
        private final Params params;
        private final int[] a;
        private final boolean[] merged;          // part of a merged (sorted) run
        private final ArrayFrame.Builder paint;
        private final TraceRecorder rec = new TraceRecorder().counter("compared", 0).counter("moved", 0);
        private long compared;
        private long moved;

        Run(List<Integer> values, Params params) {
            this.params = params;
            this.a = toArray(values);
            this.merged = new boolean[a.length];
            this.paint = ArrayFrame.builder(values);
        }

        Trace go() {
            emit(1, "Start with the list. Merge sort splits it in halves until each piece has one element, then merges the pieces back in order.");
            if (a.length > 1) {
                sort(0, a.length - 1);
            } else {
                merged[0] = true;
            }
            emit(8, "Done: the whole list is one sorted run. " + compared + " comparisons, " + moved + " elements written.");
            return new Trace(ID, params, rec.steps(),
                    Result.builder().sortedValues(toList(a)).value(compared).build());
        }

        private void sort(int lo, int hi) {
            if (lo >= hi) {
                return;   // one element: sorted, nothing to show
            }
            int mid = (lo + hi) >>> 1;
            paintBase();
            paint.states(lo, hi, State.FRONTIER).mark("lo", lo).mark("mid", mid).mark("hi", hi);
            rec.step(paint.build(), 3, "Split positions " + pos(lo) + " to " + pos(hi) + " into positions "
                    + pos(lo) + " to " + pos(mid) + " and " + pos(mid + 1) + " to " + pos(hi));
            sort(lo, mid);
            sort(mid + 1, hi);
            merge(lo, mid, hi);
        }

        private void merge(int lo, int mid, int hi) {
            int[] left = java.util.Arrays.copyOfRange(a, lo, mid + 1);
            int[] right = java.util.Arrays.copyOfRange(a, mid + 1, hi + 1);
            int i = 0;
            int j = 0;
            int k = lo;
            while (i < left.length && j < right.length) {
                compared++;
                rec.set("compared", compared);
                int l = left[i];
                int r = right[j];
                int rightHeadAt = k + (left.length - i);
                String what;
                if (l <= r) {
                    i++;
                    what = l + " and " + r + ": " + l + " is smaller or equal, so it goes next (it is already in place)";
                } else {
                    System.arraycopy(a, k, a, k + 1, rightHeadAt - k);   // slide the left rest one step right
                    a[k] = r;
                    j++;
                    what = l + " and " + r + ": " + r + " is smaller, so it moves in front of the remaining left elements";
                }
                moved++;
                rec.set("moved", moved);
                int leftRest = left.length - i;
                paintBase();
                paint.states(lo, k - 1, State.VISITED);
                paint.state(k, State.CHOSEN);
                paint.states(k + 1, k + leftRest, State.FRONTIER);
                paint.states(k + leftRest + 1, hi, State.FRONTIER);
                paint.mark("lo", lo).mark("mid", mid).mark("hi", hi);
                k++;
                rec.step(paint.build(), 6, "Compare " + what);
            }
            if (k <= hi) {
                // One half is empty; the rest of the other is already sitting in the right place.
                int rest = hi - k + 1;
                moved += rest;
                rec.set("moved", moved);
                paintBase();
                paint.states(lo, hi, State.VISITED);
                paint.mark("lo", lo).mark("mid", mid).mark("hi", hi);
                rec.step(paint.build(), 7, "The " + (i < left.length ? "right" : "left") + " half is used up, so the last "
                        + rest + (rest == 1 ? " element is" : " elements are") + " already in place");
            }
            for (int p = lo; p <= hi; p++) {
                merged[p] = true;
            }
            paintBase();
            paint.mark("lo", lo).mark("mid", mid).mark("hi", hi);
            rec.step(paint.build(), 8, "Merged positions " + pos(lo) + " to " + pos(hi) + " into one sorted run of "
                    + (hi - lo + 1));
        }

        /** Values from {@code a}, sorted runs VISITED, everything else IDLE, no markers. */
        private void paintBase() {
            for (int p = 0; p < a.length; p++) {
                paint.set(p, a[p]);
                paint.state(p, merged[p] ? State.VISITED : State.IDLE);
            }
            paint.clearMarks();
        }

        private void emit(int line, String message) {
            paintBase();
            rec.step(paint.build(), line, message);
        }
    }
}

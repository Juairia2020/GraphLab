package com.graphlab.engine.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The "one-hour spike" from the plan: a throw-away merge sort that records ArrayFrame steps, to
 * prove the Step / Frame / Trace model fits something that is not a graph. If this test ever
 * needs a model change, fix the model now, not in MS5.
 */
public class MergeSortSpikeTest {

    private static final int[] INPUT = {5, 2, 9, 1, 7, 3};

    /** The stub. Lines: 1 split, 2 compare and merge, 3 done. */
    private static Trace mergeSort(int[] input) {
        TraceRecorder rec = new TraceRecorder().counter("comparisons", 0);
        int[] a = Arrays.copyOf(input, input.length);
        State[] states = new State[a.length];
        Arrays.fill(states, State.IDLE);
        rec.step(frame(a, states), 1, "Start with the unsorted array");
        sort(a, 0, a.length - 1, states, rec);
        Arrays.fill(states, State.VISITED);
        rec.step(frame(a, states), 3, "Sorted");
        return new Trace("merge-sort-stub", Params.none(), rec.steps(), Result.builder().build());
    }

    private static void sort(int[] a, int lo, int hi, State[] states, TraceRecorder rec) {
        if (lo >= hi) {
            return;
        }
        int mid = (lo + hi) / 2;
        sort(a, lo, mid, states, rec);
        sort(a, mid + 1, hi, states, rec);
        int[] merged = new int[hi - lo + 1];
        int i = lo;
        int j = mid + 1;
        int k = 0;
        long comparisons = 0;
        while (i <= mid && j <= hi) {
            comparisons++;
            merged[k++] = a[i] <= a[j] ? a[i++] : a[j++];
        }
        while (i <= mid) {
            merged[k++] = a[i++];
        }
        while (j <= hi) {
            merged[k++] = a[j++];
        }
        System.arraycopy(merged, 0, a, lo, merged.length);
        Arrays.fill(states, lo, hi + 1, State.FRONTIER);
        rec.set("comparisons", comparisons);
        rec.step(frame(a, states), 2, "Merge positions " + lo + " to " + hi);
    }

    private static ArrayFrame frame(int[] values, State[] states) {
        List<Integer> v = new ArrayList<>();
        for (int x : values) {
            v.add(x);
        }
        return new ArrayFrame(v, Arrays.asList(states));
    }

    @Test
    public void stubProducesAValidTraceOfArrayFrames() {
        Trace trace = mergeSort(INPUT);
        assertTrue(trace.size() > 2);
        for (Step step : trace.steps()) {
            assertTrue(step.frame() instanceof ArrayFrame);
            assertEquals(INPUT.length, ((ArrayFrame) step.frame()).values().size());
            assertTrue(step.line() >= 1 && step.line() <= 3);
        }
    }

    @Test
    public void lastFrameIsSortedAndAllVisited() {
        ArrayFrame last = (ArrayFrame) mergeSort(INPUT).last().frame();
        assertEquals(List.of(1, 2, 3, 5, 7, 9), last.values());
        assertTrue(last.states().stream().allMatch(s -> s == State.VISITED));
    }

    @Test
    public void firstFrameIsTheUntouchedInput() {
        ArrayFrame first = (ArrayFrame) mergeSort(INPUT).step(0).frame();
        assertEquals(List.of(5, 2, 9, 1, 7, 3), first.values());
    }

    @Test
    public void formatterCanPrintArrayFrames() {
        ArrayFrame first = (ArrayFrame) mergeSort(INPUT).step(0).frame();
        String text = TraceFormatter.describe(com.graphlab.engine.model.Graph.builder(false).build(), first);
        assertEquals("5[I] 2[I] 9[I] 1[I] 7[I] 3[I]", text);
    }
}

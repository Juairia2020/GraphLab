package com.graphlab.engine.algo.array;

import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.List;
import java.util.Random;

/**
 * Quick sort with Lomuto partitioning: the pivot is moved to the end, everything smaller than it
 * is gathered at the front, then the pivot is dropped between the two parts, where it stays for
 * good. The two parts are sorted the same way. The randomized version picks the pivot with a
 * seeded random number first; the rest is shared.
 *
 * <p>What the bars show: the part being partitioned is FRONTIER, the pivot is PATH, the element
 * looked at (and any swapped pair) is CHOSEN, and finished positions are VISITED. Pointers
 * {@code lo}, {@code hi}, {@code i} (end of the "smaller" part) and {@code j} (the element being
 * looked at) are drawn above the bars.
 */
abstract class QuickSortBase extends ArrayAlgorithm {
    private final boolean randomized;
    private final String id;

    QuickSortBase(String id, boolean randomized) {
        this.id = id;
        this.randomized = randomized;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    protected Trace sort(List<Integer> values, Params params) {
        return new Run(values, params).go();
    }

    private final class Run {
        private final Params params;
        private final int[] a;
        private final boolean[] done;
        private final Random random;
        private final ArrayFrame.Builder paint;
        private final TraceRecorder rec;
        private long compared;
        private long swapped;
        private long picks;

        Run(List<Integer> values, Params params) {
            this.params = params;
            this.a = toArray(values);
            this.done = new boolean[a.length];
            this.random = new Random(params.seed());
            this.paint = ArrayFrame.builder(values);
            TraceRecorder r = new TraceRecorder().counter("compared", 0).counter("swapped", 0);
            this.rec = randomized ? r.counter("random picks", 0) : r;
        }

        Trace go() {
            base();
            rec.step(paint.build(), 1, "Start with the list. Quick sort picks a pivot, puts the smaller numbers before it and the rest after it, then sorts each side the same way.");
            quick(0, a.length - 1);
            base();
            rec.step(paint.build(), 7, "Done: every number is in its final place. " + compared + " comparisons, " + swapped + " swaps.");
            return new Trace(id, params, rec.steps(),
                    Result.builder().sortedValues(toList(a)).value(compared).build());
        }

        private void quick(int lo, int hi) {
            if (lo > hi) {
                return;
            }
            if (lo == hi) {
                done[lo] = true;   // a single element is already in its place
                return;
            }
            if (randomized) {
                pickRandomPivot(lo, hi);
            }
            int pivot = a[hi];
            base();
            paint.states(lo, hi - 1, State.FRONTIER).state(hi, State.PATH);
            paint.mark("lo", lo).mark("hi", hi);
            rec.step(paint.build(), 3, "Partition positions " + pos(lo) + " to " + pos(hi) + " around the pivot "
                    + pivot + " (the last one)");

            int i = lo;
            for (int j = lo; j < hi; j++) {
                compared++;
                rec.set("compared", compared);
                int x = a[j];
                boolean smaller = x < pivot;
                String message;
                int swappedWith = -1;
                if (smaller) {
                    if (i != j) {
                        int t = a[i];
                        a[i] = a[j];
                        a[j] = t;
                        swapped++;
                        rec.set("swapped", swapped);
                        swappedWith = i;
                        message = x + " is smaller, so swap it with the number at position " + pos(i)
                                + " to grow the left part";
                    } else {
                        message = x + " is smaller, and it is already at the end of the left part";
                    }
                    i++;
                } else {
                    message = x + " is not smaller, so it stays in the right part";
                }
                base();
                paint.states(lo, hi - 1, State.FRONTIER).state(hi, State.PATH);
                paint.state(j, State.CHOSEN);
                if (swappedWith >= 0) {
                    paint.state(swappedWith, State.CHOSEN);
                }
                paint.mark("lo", lo).mark("hi", hi).mark("i", Math.min(i, hi)).mark("j", j);
                rec.step(paint.build(), 5, "Compare " + x + " with the pivot " + pivot + ": " + message);
            }
            if (i != hi) {
                int t = a[i];
                a[i] = a[hi];
                a[hi] = t;
                swapped++;
                rec.set("swapped", swapped);
            }
            done[i] = true;
            base();
            paint.states(lo, hi, State.FRONTIER);
            paint.state(i, State.PATH);
            paint.mark("lo", lo).mark("hi", hi);
            for (int p = lo; p <= hi; p++) {
                if (done[p] && p != i) {
                    paint.state(p, State.VISITED);
                }
            }
            rec.step(paint.build(), 6, "Put the pivot " + pivot + " between the two parts: position " + pos(i)
                    + " is its final place");
            quick(lo, i - 1);
            quick(i + 1, hi);
        }

        private void pickRandomPivot(int lo, int hi) {
            int r = lo + random.nextInt(hi - lo + 1);
            picks++;
            rec.set("random picks", picks);
            int value = a[r];
            if (r != hi) {
                int t = a[r];
                a[r] = a[hi];
                a[hi] = t;
                swapped++;
                rec.set("swapped", swapped);
            }
            base();
            paint.states(lo, hi - 1, State.FRONTIER).state(hi, State.PATH);
            if (r != hi) {
                paint.state(r, State.CHOSEN);
            }
            paint.mark("lo", lo).mark("hi", hi);
            rec.step(paint.build(), 3, "Pick the pivot at random: position " + pos(r) + " holds " + value
                    + (r == hi ? ", which is already the last one"
                    : ". Swap it to the end, so the partition can use it"));
        }

        /** Values from {@code a}, finished positions VISITED, everything else IDLE, no markers. */
        private void base() {
            for (int p = 0; p < a.length; p++) {
                paint.set(p, a[p]);
                paint.state(p, done[p] ? State.VISITED : State.IDLE);
            }
            paint.clearMarks();
        }
    }
}

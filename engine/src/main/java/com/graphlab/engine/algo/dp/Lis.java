package com.graphlab.engine.algo.dp;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Longest strictly increasing subsequence (MS12). {@code L[i]} is the length of the longest increasing subsequence that
 * ends at position {@code i}; it is one more than the best {@code L[j]} among the earlier positions with a smaller number.
 * The position that gave the best value is remembered, so the subsequence can be walked back from the best end.
 *
 * <p>Result: {@link Result#value()} is the length and {@link Result#chosen()} the 0-based positions, from left to right.
 * When several are equally long, the earliest end is used and, for each position, the earliest best predecessor.
 */
public final class Lis extends DpAlgorithm<ArrayInput> {
    public static final String ID = "lis";

    private static final List<String> PSEUDOCODE = List.of(
            "for each position i = 1..n",
            "    L[i] = 1 and prev[i] = none  (the number alone)",
            "    for each earlier position j < i with a[j] < a[i]",
            "        if L[j] + 1 is larger than L[i]: L[i] = L[j] + 1 and prev[i] = j",
            "the answer ends where L is largest (the earliest such position)",
            "walk back along prev from that position",
            "the numbers on the walk, read from the left, are the subsequence",
            "its length is the largest L");

    public Lis() {
        super(ArrayInput.class, InputKind.ARRAY, "a list of numbers");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Longest increasing subsequence";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(ArrayInput input, Params params) {
        List<Integer> a = input.values();
        int n = a.size();

        List<String> colHeaders = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            colHeaders.add(Integer.toString(i + 1));
        }
        GridFrame.Builder paint = GridFrame.builder("", List.of("number", "length L", "previous"), colHeaders);
        TraceRecorder rec = new TraceRecorder().counter("comparisons", 0).counter("longest", 0);

        for (int i = 0; i < n; i++) {
            paint.set(0, i, Integer.toString(a.get(i))).state(0, i, State.IDLE);
        }
        rec.step(paint.build(), 1, "The numbers, left to right. L[i] will be the longest increasing run that ends at position i");

        int[] length = new int[n];
        int[] prev = new int[n];
        long comparisons = 0;
        for (int i = 0; i < n; i++) {
            length[i] = 1;
            prev[i] = -1;
            for (int j = 0; j < i; j++) {
                comparisons++;
                if (a.get(j) < a.get(i) && length[j] + 1 > length[i]) {
                    length[i] = length[j] + 1;
                    prev[i] = j;
                }
            }
            rec.set("comparisons", comparisons);
            settle(paint);
            paint.set(1, i, Integer.toString(length[i])).set(2, i, prev[i] < 0 ? "-" : Integer.toString(prev[i] + 1));
            paint.state(0, i, State.CHOSEN).state(1, i, State.CHOSEN).state(2, i, State.CHOSEN);
            if (prev[i] >= 0) {
                paint.state(0, prev[i], State.FRONTIER).state(1, prev[i], State.FRONTIER);
            }
            String message = prev[i] < 0
                    ? "Position " + (i + 1) + " (" + a.get(i) + "): no earlier number is smaller, so L = 1"
                    : "Position " + (i + 1) + " (" + a.get(i) + "): best earlier smaller number is " + a.get(prev[i]) + " at position "
                            + (prev[i] + 1) + " with L = " + length[prev[i]] + ", so L = " + length[i];
            rec.step(paint.build(), prev[i] < 0 ? 2 : 4, message);
            paint.state(0, i, State.VISITED).state(1, i, State.VISITED).state(2, i, State.VISITED);
        }
        settle(paint);

        int end = 0;
        for (int i = 1; i < n; i++) {
            if (length[i] > length[end]) {
                end = i;
            }
        }
        rec.set("longest", length[end]);
        paint.state(1, end, State.CHOSEN);
        rec.step(paint.build(), 5, "The largest L is " + length[end] + ", first reached at position " + (end + 1) + " (" + a.get(end) + ")");

        List<Integer> chosen = new ArrayList<>();
        int at = end;
        while (at >= 0) {
            chosen.add(at);
            paint.state(0, at, State.PATH).state(1, at, State.PATH).state(2, at, State.PATH);
            rec.step(paint.build(), 6, prev[at] < 0
                    ? "Position " + (at + 1) + " (" + a.get(at) + ") has no previous: the walk ends"
                    : "Position " + (at + 1) + " (" + a.get(at) + ") came from position " + (prev[at] + 1));
            at = prev[at];
        }
        Collections.reverse(chosen);
        StringBuilder numbers = new StringBuilder();
        for (int index : chosen) {
            numbers.append(numbers.length() == 0 ? "" : " ").append(a.get(index));
        }
        rec.step(paint.build(), 8, "Done: the longest increasing subsequence is " + numbers + " (" + length[end] + " numbers)");
        Result result = Result.builder().value(length[end]).chosen(chosen).text(numbers.toString()).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

package com.graphlab.engine.algo.extra;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import com.graphlab.engine.trace.Visual;
import java.util.ArrayList;
import java.util.List;

/**
 * Huffman coding (MS18). The input numbers are the frequencies of symbols A, B, C, ... (each at least 1, at least two of them). The
 * two lightest items are merged into a new item until one is left; reading the tree from the top (left branch 0, right branch 1) gives
 * a code for every symbol. Ties are broken by age: the item made earlier comes first. The first item taken becomes the left branch.
 *
 * <p>The table has one column for every leaf and every merged item (N1, N2, ...). Result: {@link Result#value()} is the total cost,
 * the sum of frequency times code length, and {@link Result#text()} lists the codes ("A=0 B=10 ...").
 */
public final class Huffman extends ArrayInputAlgorithm {
    public static final String ID = "huffman";

    private static final List<String> PSEUDOCODE = List.of(
            "make one item for every symbol, with its frequency as the weight",
            "while more than one item is left",
            "    take the two items with the smallest weights (the older one first on a tie)",
            "    make a new item whose weight is their sum, with those two as its left and right branch",
            "walk down from the last item: left adds 0 to the code, right adds 1",
            "cost = the sum of frequency times code length over all symbols");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Huffman coding";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.GREEDY;
    }

    @Override
    public Visual visual() {
        return Visual.GRID;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    static String symbol(int i) {
        return i < 26 ? String.valueOf((char) ('A' + i)) : "S" + (i + 1);
    }

    @Override
    protected Trace solve(ArrayInput input, Params params) {
        List<Integer> freq = input.values();
        int n = freq.size();
        if (n < 2) {
            throw new IllegalArgumentException(name() + " needs at least two frequencies.");
        }
        for (int f : freq) {
            if (f < 1) {
                throw new IllegalArgumentException(name() + " needs every frequency to be at least 1.");
            }
        }
        int total = 2 * n - 1;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            names.add(symbol(i));
        }
        for (int i = 1; i < n; i++) {
            names.add("N" + i);
        }
        GridFrame.Builder paint = GridFrame.builder("", List.of("weight", "left", "right", "code"), names);
        TraceRecorder rec = new TraceRecorder().counter("merges", 0).counter("cost", 0);

        long[] weight = new long[total];
        int[] left = new int[total];
        int[] right = new int[total];
        boolean[] active = new boolean[total];
        for (int i = 0; i < n; i++) {
            weight[i] = freq.get(i);
            active[i] = true;
            paint.set(0, i, Long.toString(weight[i])).state(0, i, State.IDLE);
        }
        rec.step(paint.build(), 1, "One item for each symbol, weighted by its frequency");

        long cost = 0;
        int made = n;
        while (made < total) {
            int a = lightest(weight, active, made, -1);
            active[a] = false;
            int b = lightest(weight, active, made, -1);
            active[b] = false;
            int id = made++;
            weight[id] = weight[a] + weight[b];
            left[id] = a;
            right[id] = b;
            active[id] = true;
            cost += weight[id];
            paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
            paint.state(0, a, State.CHOSEN).state(0, b, State.CHOSEN);
            rec.step(paint.build(), 3, "The two lightest items are " + names.get(a) + " (" + weight[a] + ") and " + names.get(b) + " ("
                    + weight[b] + ")");
            paint.set(0, id, Long.toString(weight[id])).set(1, id, names.get(a)).set(2, id, names.get(b))
                    .state(0, id, State.FRONTIER).state(1, id, State.FRONTIER).state(2, id, State.FRONTIER);
            rec.set("merges", id - n + 1);
            rec.set("cost", cost);
            rec.step(paint.build(), 4, "Merge them into " + names.get(id) + " with weight " + weight[a] + " + " + weight[b] + " = " + weight[id]);
        }
        paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);

        String[] code = new String[total];
        code[total - 1] = "";
        for (int id = total - 1; id >= n; id--) {
            code[left[id]] = code[id] + "0";
            code[right[id]] = code[id] + "1";
        }
        StringBuilder text = new StringBuilder();
        long check = 0;
        for (int i = 0; i < n; i++) {
            paint.set(3, i, code[i]).state(3, i, State.PATH);
            text.append(text.length() == 0 ? "" : " ").append(names.get(i)).append('=').append(code[i]);
            check += weight[i] * code[i].length();
            rec.step(paint.build(), 5, names.get(i) + " (frequency " + weight[i] + ") gets the code " + code[i]);
        }
        rec.set("cost", check);
        rec.step(paint.build(), 6, "Total cost = " + check + " bits for the whole message. Codes: " + text);
        Result result = Result.builder().value(check).text(text.toString()).build();
        return new Trace(ID, params, rec.steps(), result);
    }

    /** The active item with the smallest weight; the lowest index (the oldest) on a tie. */
    private static int lightest(long[] weight, boolean[] active, int count, int skip) {
        int best = -1;
        for (int i = 0; i < count; i++) {
            if (active[i] && i != skip && (best < 0 || weight[i] < weight[best])) {
                best = i;
            }
        }
        return best;
    }
}

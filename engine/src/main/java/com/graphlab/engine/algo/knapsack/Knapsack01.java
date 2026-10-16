package com.graphlab.engine.algo.knapsack;

import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
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
 * 0/1 knapsack by dynamic programming: each item goes in whole or stays out. {@code K[i][w]} is
 * the best value using only the first {@code i} items with room {@code w}. Every cell is filled
 * from two cells of the row above, then the table is walked backwards to find which items were
 * taken.
 *
 * <p>The table: the cell being filled is CHOSEN, the two cells it is computed from are FRONTIER,
 * filled cells are VISITED, and the walk back is PATH.
 */
public final class Knapsack01 extends KnapsackAlgorithm {
    public static final String ID = "knapsack-01";

    private static final List<String> PSEUDOCODE = List.of(
            "K[0][w] = 0 for every room w  (no items, no value)",
            "for each item i = 1..n and each room w = 0..capacity",
            "    skip = K[i-1][w]",
            "    take = K[i-1][w - weight_i] + value_i   (only if weight_i <= w)",
            "    K[i][w] = the larger of skip and take",
            "start at K[n][capacity] and walk up",
            "    if K[i][w] differs from K[i-1][w]: item i is in the bag; w = w - weight_i",
            "the best value is K[n][capacity]");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "0/1 knapsack";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.DYNAMIC_PROGRAMMING;
    }

    @Override
    public Visual visual() {
        return Visual.GRID;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(KnapsackInput input, Params params) {
        List<Item> items = input.items();
        int n = items.size();
        int capacity = input.capacity();

        List<String> rowHeaders = new ArrayList<>();
        rowHeaders.add("0: none");
        for (int i = 0; i < n; i++) {
            rowHeaders.add((i + 1) + ": w" + items.get(i).weight() + " v" + items.get(i).value());
        }
        List<String> colHeaders = new ArrayList<>();
        for (int w = 0; w <= capacity; w++) {
            colHeaders.add(Integer.toString(w));
        }

        int[][] k = new int[n + 1][capacity + 1];
        GridFrame.Builder paint = GridFrame.builder("item \\ room", rowHeaders, colHeaders);
        TraceRecorder rec = new TraceRecorder().counter("cells filled", 0).counter("best", 0);

        for (int w = 0; w <= capacity; w++) {
            paint.set(0, w, "0").state(0, w, State.VISITED);
        }
        long filled = capacity + 1;
        rec.set("cells filled", filled);
        rec.step(paint.build(), 1, "Row 0: with no items the best value is 0, whatever the room");

        for (int i = 1; i <= n; i++) {
            Item item = items.get(i - 1);
            for (int w = 0; w <= capacity; w++) {
                int skip = k[i - 1][w];
                boolean fits = item.weight() <= w;
                int take = fits ? k[i - 1][w - item.weight()] + item.value() : -1;
                k[i][w] = fits ? Math.max(skip, take) : skip;
                filled++;
                rec.set("cells filled", filled);

                paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
                paint.set(i, w, Integer.toString(k[i][w]));
                paint.state(i - 1, w, State.FRONTIER);
                if (fits) {
                    paint.state(i - 1, w - item.weight(), State.FRONTIER);
                }
                paint.state(i, w, State.CHOSEN);
                String message;
                if (!fits) {
                    message = "Item " + i + " weighs " + item.weight() + ", more than the room " + w
                            + ", so copy " + skip + " from above";
                } else if (take > skip) {
                    message = "Room " + w + ", item " + i + ": skip it = " + skip + ", take it = "
                            + k[i - 1][w - item.weight()] + " + " + item.value() + " = " + take + ". Taking is better: " + take;
                } else {
                    message = "Room " + w + ", item " + i + ": skip it = " + skip + ", take it = "
                            + k[i - 1][w - item.weight()] + " + " + item.value() + " = " + take + ". Skipping is as good or better: " + skip;
                }
                rec.step(paint.build(), fits ? 5 : 3, message);
            }
        }
        paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
        rec.set("best", k[n][capacity]);
        rec.step(paint.build(), 5, "The table is full. The best value with room " + capacity + " is " + k[n][capacity]);

        // Walk back from K[n][capacity] to find the items.
        List<Integer> chosen = new ArrayList<>();
        int w = capacity;
        paint.state(n, w, State.PATH);
        for (int i = n; i >= 1; i--) {
            Item item = items.get(i - 1);
            if (k[i][w] != k[i - 1][w]) {
                chosen.add(0, i - 1);
                w -= item.weight();
                paint.state(i - 1, w, State.PATH);
                rec.step(paint.build(), 7, "K[" + i + "] is not the same as the cell above, so item " + i
                        + " is in the bag. Room left " + w);
            } else {
                paint.state(i - 1, w, State.PATH);
                rec.step(paint.build(), 7, "K[" + i + "] equals the cell above, so item " + i + " is not used");
            }
        }
        StringBuilder used = new StringBuilder();
        for (int index : chosen) {
            used.append(used.length() == 0 ? "" : ", ").append(index + 1);
        }
        rec.step(paint.build(), 8, "Done: the best value is " + k[n][capacity]
                + (chosen.isEmpty() ? " with no items" : " using item" + (chosen.size() == 1 ? " " : "s ") + used));
        Result result = Result.builder().value(k[n][capacity]).chosen(chosen).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

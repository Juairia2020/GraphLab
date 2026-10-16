package com.graphlab.engine.algo.knapsack;

import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import com.graphlab.engine.trace.Visual;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fractional knapsack, the greedy one: items can be cut, so the best plan is to take the items
 * with the most value per unit of weight first, and a piece of the next one to fill the bag.
 *
 * <p>The bars are the items (height = value, text under a bar = its weight), in the order they
 * are considered. VISITED = taken whole, PATH = a piece taken, FAIL = left behind, CHOSEN =
 * the item being looked at.
 */
public final class FractionalKnapsack extends KnapsackAlgorithm {
    public static final String ID = "fractional-knapsack";

    private static final List<String> PSEUDOCODE = List.of(
            "room = the bag's capacity; value = 0",
            "sort the items by value / weight, best first",
            "for each item in that order",
            "    if the whole item fits: take it all; room = room - weight",
            "    else take the fraction room / weight of it; the bag is full",
            "the value is the sum of what was taken");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Fractional knapsack";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.GREEDY;
    }

    @Override
    public Visual visual() {
        return Visual.ARRAY;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(KnapsackInput input, Params params) {
        List<Item> items = input.items();
        int n = items.size();
        // Best value per weight first; ties keep the order they were given in.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> -(double) items.get(i).value() / items.get(i).weight()));

        List<Integer> originalValues = new ArrayList<>();
        List<String> originalText = new ArrayList<>();
        for (Item item : items) {
            originalValues.add(item.value());
            originalText.add("w" + item.weight());
        }
        List<Integer> sortedValues = new ArrayList<>();
        List<String> sortedText = new ArrayList<>();
        for (int i : order) {
            sortedValues.add(items.get(i).value());
            sortedText.add("w" + items.get(i).weight());
        }

        ArrayFrame.Builder paint = ArrayFrame.builder(originalValues);
        paint.replaceAll(originalValues, originalText);
        TraceRecorder rec = new TraceRecorder().counter("items taken", 0).counter("room left", input.capacity());
        Result.Builder result = Result.builder();

        rec.step(paint.build(), 1, "The bag holds " + input.capacity() + ". Each bar is an item: its height is the value, and w is its weight.");
        paint.replaceAll(sortedValues, sortedText);
        StringBuilder ratios = new StringBuilder();
        for (int i : order) {
            ratios.append(ratios.length() == 0 ? "" : ", ").append(Labels.number(round2((double) items.get(i).value() / items.get(i).weight())));
        }
        rec.step(paint.build(), 2, "Sort by value per weight, best first (" + ratios + ")");

        double room = input.capacity();
        double total = 0;
        int taken = 0;
        double[] amount = new double[n];
        for (int rank = 0; rank < n; rank++) {
            Item item = items.get(order.get(rank));
            if (room <= 0) {
                for (int rest = rank; rest < n; rest++) {
                    paint.state(rest, State.FAIL);
                }
                rec.step(paint.build(), 3, "The bag is full, so the " + (n - rank) + " remaining "
                        + (n - rank == 1 ? "item stays" : "items stay") + " behind");
                break;
            }
            paint.state(rank, State.CHOSEN);
            rec.step(paint.build(), 3, "Look at item " + (rank + 1) + " (weight " + item.weight() + ", value " + item.value()
                    + "). Room left: " + Labels.number(room));
            if (item.weight() <= room) {
                room -= item.weight();
                total += item.value();
                amount[order.get(rank)] = 1.0;
                taken++;
                paint.state(rank, State.VISITED);
                rec.set("items taken", taken);
                rec.set("room left", (long) room);
                rec.step(paint.build(), 4, "It fits, so take all of it. Room left " + Labels.number(room)
                        + ", value so far " + Labels.number(round2(total)));
            } else {
                double fraction = room / item.weight();
                double gained = fraction * item.value();
                total += gained;
                amount[order.get(rank)] = fraction;
                taken++;
                paint.state(rank, State.PATH);
                rec.set("items taken", taken);
                rec.set("room left", 0);
                rec.step(paint.build(), 5, "Only " + Labels.number(room) + " of its " + item.weight() + " fits, so take "
                        + Labels.number(round2(fraction)) + " of it (worth " + Labels.number(round2(gained))
                        + "). The bag is full. Value so far " + Labels.number(round2(total)));
                room = 0;
            }
        }
        for (int rank = 0; rank < n; rank++) {
            if (paint.state(rank) == State.CHOSEN) {
                paint.state(rank, State.IDLE);
            }
        }
        rec.step(paint.build(), 6, "Done: the best value is " + Labels.number(round2(total)) + " using " + taken
                + (taken == 1 ? " item" : " items"));

        List<Integer> chosen = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            amounts.add(amount[i]);
            if (amount[i] > 0) {
                chosen.add(i);
            }
        }
        result.value(total).chosen(chosen).amounts(amounts);
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.graphlab.engine.algo.array;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.Visual;
import java.util.List;

/**
 * Shared plumbing for the sorting algorithms: they take a list of numbers (not a graph), need no
 * start node, and replay through bars.
 *
 * <p>Colours in every sorting trace: IDLE is outside the part being worked on, FRONTIER is
 * inside it, CHOSEN is being compared or moved right now, PATH is a pivot, VISITED is sorted.
 */
abstract class ArrayAlgorithm implements Algorithm {

    @Override
    public final InputKind inputKind() {
        return InputKind.ARRAY;
    }

    @Override
    public final Visual visual() {
        return Visual.ARRAY;
    }

    @Override
    public final boolean needsSource() {
        return false;
    }

    @Override
    public final Trace run(Graph graph, Params params) {
        throw new IllegalArgumentException(name() + " sorts a list of numbers, not a graph. Type the numbers in the box.");
    }

    @Override
    public final Trace run(Input input, Params params) {
        if (!(input instanceof ArrayInput array)) {
            throw new IllegalArgumentException(name() + " does not work on " + Algorithm.describe(input.kind()) + ".");
        }
        return sort(array.values(), params);
    }

    /** Sorts {@code values} (already checked: 1 to 16 numbers from 0 to 99) and records every step. */
    protected abstract Trace sort(List<Integer> values, Params params);

    /** "1" for index 0: positions are shown to people counting from 1. */
    static int pos(int index) {
        return index + 1;
    }

    static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    static List<Integer> toList(int[] a) {
        java.util.List<Integer> out = new java.util.ArrayList<>(a.length);
        for (int v : a) {
            out.add(v);
        }
        return out;
    }
}

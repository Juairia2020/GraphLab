package com.graphlab.engine.algo.knapsack;

import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;

/** Shared plumbing for the two knapsack algorithms: they take items and a capacity, not a graph, and need no start node. */
abstract class KnapsackAlgorithm implements Algorithm {

    @Override
    public final InputKind inputKind() {
        return InputKind.KNAPSACK;
    }

    @Override
    public final boolean needsSource() {
        return false;
    }

    @Override
    public final Trace run(Graph graph, Params params) {
        throw new IllegalArgumentException(name() + " packs items into a bag, not a graph. Type the items and the capacity.");
    }

    @Override
    public final Trace run(Input input, Params params) {
        if (!(input instanceof KnapsackInput knapsack)) {
            throw new IllegalArgumentException(name() + " does not work on " + Algorithm.describe(input.kind()) + ".");
        }
        return solve(knapsack, params);
    }

    protected abstract Trace solve(KnapsackInput input, Params params);
}

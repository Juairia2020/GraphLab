package com.graphlab.engine.algo.extra;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;

/** Shared plumbing for the MS18 algorithms that work on a typed list of numbers and need no start node. */
abstract class ArrayInputAlgorithm implements Algorithm {
    @Override
    public final InputKind inputKind() {
        return InputKind.ARRAY;
    }

    @Override
    public final boolean needsSource() {
        return false;
    }

    @Override
    public final Trace run(Graph graph, Params params) {
        throw new IllegalArgumentException(name() + " works on a list of numbers, not a graph. Type it in the box.");
    }

    @Override
    public final Trace run(Input input, Params params) {
        if (!(input instanceof ArrayInput array)) {
            throw new IllegalArgumentException(name() + " does not work on " + Algorithm.describe(input.kind()) + ".");
        }
        return solve(array, params);
    }

    protected abstract Trace solve(ArrayInput input, Params params);
}

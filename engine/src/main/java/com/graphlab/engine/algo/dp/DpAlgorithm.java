package com.graphlab.engine.algo.dp;

import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.Visual;

/**
 * Shared plumbing for the dynamic-programming pack (MS12): the algorithms work on their own kind of input, need no start
 * node and replay through a table. Colours in every table: the cell being filled is CHOSEN, the cells it is computed from
 * are FRONTIER, filled cells are VISITED and the traceback is PATH.
 */
abstract class DpAlgorithm<I extends Input> implements Algorithm {
    /** Shown for "impossible" in a table of counts (coin change). */
    static final String INFINITY = "∞";

    private final Class<I> inputType;
    private final InputKind kind;
    private final String what;

    DpAlgorithm(Class<I> inputType, InputKind kind, String what) {
        this.inputType = inputType;
        this.kind = kind;
        this.what = what;
    }

    @Override
    public final InputKind inputKind() {
        return kind;
    }

    @Override
    public final boolean needsSource() {
        return false;
    }

    @Override
    public final AlgorithmCategory category() {
        return AlgorithmCategory.DYNAMIC_PROGRAMMING;
    }

    @Override
    public final Visual visual() {
        return Visual.GRID;
    }

    @Override
    public final Trace run(Graph graph, Params params) {
        throw new IllegalArgumentException(name() + " works on " + what + ", not a graph. Type it in the box.");
    }

    @Override
    public final Trace run(Input input, Params params) {
        if (!inputType.isInstance(input)) {
            throw new IllegalArgumentException(name() + " does not work on " + Algorithm.describe(input.kind()) + ".");
        }
        return solve(inputType.cast(input), params);
    }

    protected abstract Trace solve(I input, Params params);

    /** Moves the "just filled" colours on: the last CHOSEN and FRONTIER cells become plain filled cells. */
    static void settle(com.graphlab.engine.trace.GridFrame.Builder paint) {
        paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
    }
}

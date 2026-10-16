package com.graphlab.engine.trace;

import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.model.Graph;
import java.util.List;

/**
 * An algorithm that can be run on a graph and replayed step by step.
 *
 * <p>Implementations are pure and deterministic: the same graph and params always give an equal
 * trace. They must not keep state between runs, and anything random must use a
 * {@link java.util.Random} seeded from {@link Params#seed()}.
 */
public interface Algorithm {
    /** Stable identifier, used in saved data. Never change it once released. */
    String id();

    /** Human-readable name for pickers and titles. */
    String name();

    /**
     * Whether the run needs a start node ({@link Params#source()}). Kruskal does not; the others
     * do. The Run screen uses this to show or hide the "Start" control.
     */
    default boolean needsSource() {
        return true;
    }

    /** Which family the picker lists this algorithm under. */
    AlgorithmCategory category();

    /** What the algorithm works on. The Run screen shows the matching input control. */
    default InputKind inputKind() {
        return InputKind.GRAPH;
    }

    /** Which kind of frame its trace holds, so the screen shows the canvas, the bars or the table. */
    default Visual visual() {
        return Visual.GRAPH;
    }

    /** Whether the run also needs an end node ({@link Params#target()}), like a flow's sink. */
    default boolean needsTarget() {
        return false;
    }

    /**
     * A small graph this algorithm works on, for "load an example" and for tests. Most algorithms use the six-node undirected
     * sample; the ones that need arrows or two sides override it. Node ids 0 to 5 (A to F) always exist.
     */
    default Graph exampleGraph() {
        return com.graphlab.engine.model.SampleGraph.sixNodeUndirected();
    }

    /** The pseudocode shown next to the trace. {@link Step#line()} counts from 1 into this list. */
    List<String> pseudocode();

    /**
     * Runs the algorithm.
     *
     * @throws IllegalArgumentException if the input is not valid for this algorithm (missing
     *                                  source node, negative weights for Dijkstra, a directed
     *                                  graph for a spanning-tree algorithm)
     */
    Trace run(Graph graph, Params params);

    /**
     * Runs the algorithm on any kind of input. Graph algorithms accept a {@link GraphInput};
     * sorting and knapsack algorithms override this for their own input. Giving an algorithm the
     * wrong kind of input is an {@link IllegalArgumentException}.
     */
    default Trace run(Input input, Params params) {
        if (input instanceof GraphInput g) {
            return run(g.graph(), params);
        }
        throw new IllegalArgumentException(name() + " does not work on " + describe(input.kind()) + ".");
    }

    /** "a list of numbers", "a knapsack problem" or "a graph", for error messages. */
    static String describe(InputKind kind) {
        return switch (kind) {
            case GRAPH -> "a graph";
            case ARRAY -> "a list of numbers";
            case KNAPSACK -> "a knapsack problem";
            case TEXT -> "text";
            case COINS -> "a coin problem";
            case STRINGS -> "two pieces of text";
        };
    }
}

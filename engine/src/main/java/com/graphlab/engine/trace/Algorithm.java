package com.graphlab.engine.trace;

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
}

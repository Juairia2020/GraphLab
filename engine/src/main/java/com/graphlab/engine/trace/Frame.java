package com.graphlab.engine.trace;

/**
 * A complete, immutable picture of one moment in a run. Every step stores a whole frame, which
 * makes stepping backwards trivial.
 *
 * <p>Sealed so the UI can handle every kind exhaustively: a graph (nodes and edges with states),
 * an array (bars, for sorting and greedy choices) or a grid (a table, for dynamic programming and
 * all-pairs distances).
 */
public sealed interface Frame permits GraphFrame, ArrayFrame, GridFrame {
}

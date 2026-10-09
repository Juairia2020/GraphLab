package com.graphlab.engine.trace;

/**
 * A complete, immutable picture of one moment in a run. Every step stores a whole frame, which
 * makes stepping backwards trivial.
 *
 * <p>Sealed so the UI can handle every kind exhaustively. GridFrame (for dynamic programming)
 * joins the permits list in MS5.
 */
public sealed interface Frame permits GraphFrame, ArrayFrame {
}

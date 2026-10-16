package com.graphlab.engine.trace;

/** Which kind of {@link Frame} an algorithm's trace is made of, so the screen shows the right view. */
public enum Visual {
    /** Nodes and edges on the canvas. */
    GRAPH,
    /** Bars in a row. */
    ARRAY,
    /** A table of cells. */
    GRID
}

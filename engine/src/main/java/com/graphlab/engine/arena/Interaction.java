package com.graphlab.engine.arena;

/** How the player answers a question. {@link #CHOICE} is a button; the others are small tasks (see {@link Task}). */
public enum Interaction {
    /** Pick one of two to four answers. */
    CHOICE,
    /** Drag the steps into the right order. */
    ORDER,
    /** Flip tiles to find pairs. */
    MATCH,
    /** Type a number; close enough counts. */
    NUMBER,
    /** Type the missing cells of a table. */
    TABLE,
    /** Draw a route on the graph. */
    PATH
}

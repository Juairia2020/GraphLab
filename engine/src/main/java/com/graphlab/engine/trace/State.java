package com.graphlab.engine.trace;

/**
 * What a node, edge or array cell is doing at a given step. One value per colour token:
 * IDLE gl_idle, FRONTIER gl_frontier, VISITED gl_visited, CHOSEN gl_chosen, PATH gl_path,
 * FAIL gl_fail. The UI maps these to colours; the engine never knows about colours.
 */
public enum State {
    /** Not touched yet. */
    IDLE,
    /** Discovered or waiting its turn (in a queue or on a stack). */
    FRONTIER,
    /** Finished with. */
    VISITED,
    /** The thing being looked at right now. */
    CHOSEN,
    /** Part of the answer: a tree edge, a shortest-path edge. */
    PATH,
    /** Rejected or wrong: a skipped edge, a negative cycle. */
    FAIL
}

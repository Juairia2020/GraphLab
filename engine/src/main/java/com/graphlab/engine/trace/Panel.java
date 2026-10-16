package com.graphlab.engine.trace;

import java.util.List;
import java.util.Objects;

/**
 * A small named list that an algorithm shows next to the graph: the queue of BFS, the stack of DFS, the
 * priority queue of Dijkstra and Prim, the union-find parents of Kruskal. The State tab draws each panel as a
 * row of chips. Items are plain text, already formatted, in the order they should be read.
 *
 * @param title the heading, for example "Queue"
 * @param items the entries, front first; empty means the structure is empty
 */
public record Panel(String title, List<String> items) {

    public Panel {
        Objects.requireNonNull(title, "title");
        items = List.copyOf(items);
    }
}

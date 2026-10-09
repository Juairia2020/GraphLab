package com.graphlab.engine.model;

/**
 * One direction of travel along an edge. A directed edge gives one arc; an undirected edge
 * gives two (one each way). Algorithms walk arcs so they never need to care which kind of
 * graph they were given.
 */
public record Arc(Edge edge, int from, int to) {
    public double weight() {
        return edge.weight();
    }
}

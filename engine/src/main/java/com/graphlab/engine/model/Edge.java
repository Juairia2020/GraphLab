package com.graphlab.engine.model;

/** An edge between two node ids. For an undirected graph, {@code from} and {@code to} are just the two ends. */
public record Edge(int id, int from, int to, double weight) {
    public Edge {
        if (id < 0 || from < 0 || to < 0) {
            throw new IllegalArgumentException("Edge and node ids must be 0 or more");
        }
        if (!Double.isFinite(weight)) {
            throw new IllegalArgumentException("Edge weight must be a finite number: " + weight);
        }
    }
}

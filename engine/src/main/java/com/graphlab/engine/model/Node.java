package com.graphlab.engine.model;

import java.util.Objects;

/**
 * A graph node. {@code x} and {@code y} are editor coordinates (any unit); the engine never
 * reads them, they only travel with the graph so the UI can draw it.
 */
public record Node(int id, double x, double y, String label) {
    public Node {
        if (id < 0) {
            throw new IllegalArgumentException("Node id must be 0 or more: " + id);
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Node position must be finite");
        }
        Objects.requireNonNull(label, "label");
    }
}

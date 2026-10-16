package com.graphlab.engine.input;

import com.graphlab.engine.model.Graph;
import java.util.Objects;

/** A graph as an algorithm input. */
public record GraphInput(Graph graph) implements Input {
    public GraphInput {
        Objects.requireNonNull(graph, "graph");
    }

    @Override
    public InputKind kind() {
        return InputKind.GRAPH;
    }
}

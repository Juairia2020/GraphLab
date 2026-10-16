package com.graphlab.engine.io;

import com.graphlab.engine.model.Graph;
import java.util.Objects;

/**
 * A graph plus what a file has to remember about it: a name, and whether the weights are
 * meant to be shown ("weighted"). The editor can hold typed weights while the graph is
 * unweighted, so the weights are always stored and this flag says how to treat them.
 */
public final class GraphDocument {
    private final String name;
    private final boolean weighted;
    private final Graph graph;
    private final Background background;

    public GraphDocument(String name, boolean weighted, Graph graph) {
        this(name, weighted, graph, null);
    }

    /** @param background the photo behind the graph, or null for none */
    public GraphDocument(String name, boolean weighted, Graph graph, Background background) {
        this.name = Objects.requireNonNull(name, "name");
        this.weighted = weighted;
        this.graph = Objects.requireNonNull(graph, "graph");
        this.background = background;
    }

    public String name() {
        return name;
    }

    public boolean weighted() {
        return weighted;
    }

    public Graph graph() {
        return graph;
    }

    /** The photo behind the graph, or null. */
    public Background background() {
        return background;
    }

    public GraphDocument withName(String newName) {
        return new GraphDocument(newName, weighted, graph, background);
    }

    public GraphDocument withBackground(Background newBackground) {
        return new GraphDocument(name, weighted, graph, newBackground);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphDocument)) {
            return false;
        }
        GraphDocument that = (GraphDocument) other;
        return weighted == that.weighted && name.equals(that.name) && graph.equals(that.graph)
                && Objects.equals(background, that.background);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, weighted, graph, background);
    }

    @Override
    public String toString() {
        return "GraphDocument{" + name + ", " + graph.nodeCount() + " nodes, " + graph.edgeCount() + " edges}";
    }
}

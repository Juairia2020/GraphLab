package com.graphlab.engine.preset;

import com.graphlab.engine.geo.City;
import com.graphlab.engine.model.Graph;
import java.util.List;
import java.util.Objects;

/**
 * A real-world example for the Toolkit: a story, a graph with canvas coordinates and capacities or distances, the tools
 * that make sense on it, and the start and end places those tools use.
 */
public record Preset(String id, String title, String story, String unit, Graph graph, List<PresetTool> tools,
                     int source, int target, List<City> geo) {
    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(graph, "graph");
        tools = List.copyOf(tools);
        geo = List.copyOf(geo);
        if (!geo.isEmpty() && geo.size() != graph.nodeCount()) {
            throw new IllegalArgumentException("geo needs one place per node");
        }
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("A preset needs at least one tool");
        }
        if (!hasNode(graph, source) || !hasNode(graph, target)) {
            throw new IllegalArgumentException("source and target must be nodes of the graph");
        }
    }

    private static boolean hasNode(Graph graph, int id) {
        return graph.nodes().stream().anyMatch(n -> n.id() == id);
    }

    /** True when the preset can be placed on the map (flow on the map). */
    public boolean hasGeo() {
        return !geo.isEmpty();
    }

    public String nameOf(int nodeId) {
        return graph.node(nodeId).label();
    }
}

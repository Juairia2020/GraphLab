package com.graphlab.engine.trace;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Node;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State of every node and edge of a graph, plus short text labels above nodes (distances,
 * discovery times). All maps are unmodifiable copies, keyed by node id or edge id.
 */
public record GraphFrame(
        Map<Integer, State> nodeStates,
        Map<Integer, State> edgeStates,
        Map<Integer, String> nodeLabels,
        Map<Integer, String> edgeLabels,
        List<Panel> panels) implements Frame {

    /** A frame with no edge labels. Keeps the MS1 three-map form working. */
    public GraphFrame(Map<Integer, State> nodeStates, Map<Integer, State> edgeStates, Map<Integer, String> nodeLabels) {
        this(nodeStates, edgeStates, nodeLabels, Map.of(), List.of());
    }

    /** A frame with edge labels and no panels. */
    public GraphFrame(Map<Integer, State> nodeStates, Map<Integer, State> edgeStates, Map<Integer, String> nodeLabels,
                      Map<Integer, String> edgeLabels) {
        this(nodeStates, edgeStates, nodeLabels, edgeLabels, List.of());
    }

    public GraphFrame {
        nodeStates = freeze(nodeStates);
        edgeStates = freeze(edgeStates);
        nodeLabels = freeze(nodeLabels);
        edgeLabels = freeze(edgeLabels);
        panels = List.copyOf(panels);
    }

    private static <V> Map<Integer, V> freeze(Map<Integer, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public State nodeState(int nodeId) {
        State state = nodeStates.get(nodeId);
        if (state == null) {
            throw new IllegalArgumentException("No node with id " + nodeId + " in this frame");
        }
        return state;
    }

    public State edgeState(int edgeId) {
        State state = edgeStates.get(edgeId);
        if (state == null) {
            throw new IllegalArgumentException("No edge with id " + edgeId + " in this frame");
        }
        return state;
    }

    /** The label above the node, or an empty string when it has none. */
    public String nodeLabel(int nodeId) {
        return nodeLabels.getOrDefault(nodeId, "");
    }

    /**
     * The text on the edge (for example flow "3/5"), or an empty string. When an edge has a label
     * the canvas shows it instead of the weight.
     */
    public String edgeLabel(int edgeId) {
        return edgeLabels.getOrDefault(edgeId, "");
    }

    /** A frame where everything is IDLE and nothing is labelled. */
    public static GraphFrame initial(Graph graph) {
        return builder(graph).build();
    }

    public static Builder builder(Graph graph) {
        return new Builder(graph);
    }

    /**
     * Mutable scratch space an algorithm paints on, then snapshots into an immutable frame with
     * {@link #build()} after each step.
     */
    public static final class Builder {
        private final Map<Integer, State> nodeStates = new LinkedHashMap<>();
        private final Map<Integer, State> edgeStates = new LinkedHashMap<>();
        private final Map<Integer, String> nodeLabels = new LinkedHashMap<>();
        private final Map<Integer, String> edgeLabels = new LinkedHashMap<>();
        private final List<Panel> panels = new ArrayList<>();

        private Builder(Graph graph) {
            for (Node node : graph.nodes()) {
                nodeStates.put(node.id(), State.IDLE);
            }
            for (Edge edge : graph.edges()) {
                edgeStates.put(edge.id(), State.IDLE);
            }
        }

        public Builder node(int nodeId, State state) {
            requireKey(nodeStates, nodeId, "node");
            nodeStates.put(nodeId, state);
            return this;
        }

        public Builder edge(int edgeId, State state) {
            requireKey(edgeStates, edgeId, "edge");
            edgeStates.put(edgeId, state);
            return this;
        }

        public Builder label(int nodeId, String label) {
            requireKey(nodeStates, nodeId, "node");
            nodeLabels.put(nodeId, label);
            return this;
        }

        /** Puts text on an edge; an empty string clears it. */
        public Builder edgeLabel(int edgeId, String label) {
            requireKey(edgeStates, edgeId, "edge");
            if (label.isEmpty()) {
                edgeLabels.remove(edgeId);
            } else {
                edgeLabels.put(edgeId, label);
            }
            return this;
        }

        /** Shows (or replaces) the panel with this title: a queue, a stack or a parent table. */
        public Builder panel(String title, List<String> items) {
            Panel next = new Panel(title, items);
            for (int i = 0; i < panels.size(); i++) {
                if (panels.get(i).title().equals(title)) {
                    panels.set(i, next);
                    return this;
                }
            }
            panels.add(next);
            return this;
        }

        /** Removes every node label. */
        public Builder clearLabels() {
            nodeLabels.clear();
            return this;
        }

        public State nodeState(int nodeId) {
            requireKey(nodeStates, nodeId, "node");
            return nodeStates.get(nodeId);
        }

        public State edgeState(int edgeId) {
            requireKey(edgeStates, edgeId, "edge");
            return edgeStates.get(edgeId);
        }

        /** Turns every edge currently in {@code from} into {@code to}. */
        public Builder recolourEdges(State from, State to) {
            edgeStates.replaceAll((id, state) -> state == from ? to : state);
            return this;
        }

        /** Turns every node currently in {@code from} into {@code to}. */
        public Builder recolourNodes(State from, State to) {
            nodeStates.replaceAll((id, state) -> state == from ? to : state);
            return this;
        }

        /** Takes an immutable snapshot. Later changes to this builder do not affect it. */
        public GraphFrame build() {
            return new GraphFrame(nodeStates, edgeStates, nodeLabels, edgeLabels, panels);
        }

        private static void requireKey(Map<Integer, ?> map, int id, String what) {
            if (!map.containsKey(id)) {
                throw new IllegalArgumentException("No " + what + " with id " + id);
            }
        }
    }
}

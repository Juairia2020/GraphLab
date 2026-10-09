package com.graphlab.engine.trace;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Node;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State of every node and edge of a graph, plus short text labels above nodes (distances,
 * discovery times). All maps are unmodifiable copies, keyed by node id or edge id.
 */
public record GraphFrame(
        Map<Integer, State> nodeStates,
        Map<Integer, State> edgeStates,
        Map<Integer, String> nodeLabels) implements Frame {

    public GraphFrame {
        nodeStates = freeze(nodeStates);
        edgeStates = freeze(edgeStates);
        nodeLabels = freeze(nodeLabels);
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
            return new GraphFrame(nodeStates, edgeStates, nodeLabels);
        }

        private static void requireKey(Map<Integer, ?> map, int id, String what) {
            if (!map.containsKey(id)) {
                throw new IllegalArgumentException("No " + what + " with id " + id);
            }
        }
    }
}

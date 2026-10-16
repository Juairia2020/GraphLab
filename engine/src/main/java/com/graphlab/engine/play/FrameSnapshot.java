package com.graphlab.engine.play;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.State;
import java.util.List;

/**
 * One {@link GraphFrame} laid out as plain arrays in the same order as the graph's node and
 * edge lists, which is also the editor's order. The canvas reads these by index while drawing, so
 * it never looks up a Map (which would box every id into an Integer on every frame).
 *
 * <p>Anything the frame does not mention is IDLE with no label.
 */
public final class FrameSnapshot {
    private final State[] nodeStates;
    private final String[] nodeLabels;
    private final State[] edgeStates;
    private final String[] edgeLabels;
    private final boolean anyEdgeLabel;

    private FrameSnapshot(State[] nodeStates, String[] nodeLabels, State[] edgeStates, String[] edgeLabels) {
        this.nodeStates = nodeStates;
        this.nodeLabels = nodeLabels;
        this.edgeStates = edgeStates;
        this.edgeLabels = edgeLabels;
        boolean any = false;
        for (String label : edgeLabels) {
            any |= !label.isEmpty();
        }
        this.anyEdgeLabel = any;
    }

    public static FrameSnapshot of(Graph graph, GraphFrame frame) {
        List<Node> nodes = graph.nodes();
        List<Edge> edges = graph.edges();
        State[] nodeStates = new State[nodes.size()];
        String[] nodeLabels = new String[nodes.size()];
        State[] edgeStates = new State[edges.size()];
        String[] edgeLabels = new String[edges.size()];
        for (int i = 0; i < nodes.size(); i++) {
            int id = nodes.get(i).id();
            nodeStates[i] = frame.nodeStates().getOrDefault(id, State.IDLE);
            nodeLabels[i] = frame.nodeLabels().getOrDefault(id, "");
        }
        for (int i = 0; i < edges.size(); i++) {
            edgeStates[i] = frame.edgeStates().getOrDefault(edges.get(i).id(), State.IDLE);
            edgeLabels[i] = frame.edgeLabels().getOrDefault(edges.get(i).id(), "");
        }
        return new FrameSnapshot(nodeStates, nodeLabels, edgeStates, edgeLabels);
    }

    public int nodeCount() {
        return nodeStates.length;
    }

    public int edgeCount() {
        return edgeStates.length;
    }

    public State nodeState(int nodeIndex) {
        return nodeStates[nodeIndex];
    }

    /** The text above the node (a distance, a time), or an empty string. */
    public String nodeLabel(int nodeIndex) {
        return nodeLabels[nodeIndex];
    }

    public State edgeState(int edgeIndex) {
        return edgeStates[edgeIndex];
    }

    /** The text on the edge (flow "3/5"), or an empty string. When present it replaces the weight. */
    public String edgeLabel(int edgeIndex) {
        return edgeLabels[edgeIndex];
    }

    /** True when at least one edge has a label, so the canvas shows labels even on an unweighted graph. */
    public boolean hasEdgeLabels() {
        return anyEdgeLabel;
    }

    /** True when this snapshot was made for a graph with this many nodes and edges. */
    public boolean fits(int nodeCount, int edgeCount) {
        return nodeStates.length == nodeCount && edgeStates.length == edgeCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FrameSnapshot that)) {
            return false;
        }
        return java.util.Arrays.equals(nodeStates, that.nodeStates)
                && java.util.Arrays.equals(nodeLabels, that.nodeLabels)
                && java.util.Arrays.equals(edgeStates, that.edgeStates)
                && java.util.Arrays.equals(edgeLabels, that.edgeLabels);
    }

    @Override
    public int hashCode() {
        int h = java.util.Arrays.hashCode(nodeStates);
        h = 31 * h + java.util.Arrays.hashCode(nodeLabels);
        h = 31 * h + java.util.Arrays.hashCode(edgeStates);
        return 31 * h + java.util.Arrays.hashCode(edgeLabels);
    }
}

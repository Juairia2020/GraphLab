package com.graphlab.engine.play;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Step;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The numbers behind the State tab for one step: a row per node, the nodes waiting in the
 * queue (or on the stack), and the running counters. Works the same for every algorithm
 * because it only reads what the step already says: states, labels and counters.
 */
public final class StepSummary {

    /** One row of the node table. A record, so DiffUtil can compare rows with equals(). */
    public record NodeRow(int nodeId, String name, State state, String label) {
    }

    private StepSummary() {
    }

    /** One row per node, in the graph's node order. */
    public static List<NodeRow> nodeRows(Graph graph, GraphFrame frame) {
        List<NodeRow> rows = new ArrayList<>(graph.nodeCount());
        for (Node node : graph.nodes()) {
            rows.add(new NodeRow(node.id(), node.label(),
                    frame.nodeStates().getOrDefault(node.id(), State.IDLE),
                    frame.nodeLabels().getOrDefault(node.id(), "")));
        }
        return rows;
    }

    /**
     * The rows whose state is FRONTIER, which for BFS, DFS, Dijkstra and Prim is exactly what is
     * waiting its turn. Ordered by the number in the label (the smallest distance first); rows
     * with no number keep the node order.
     */
    public static List<NodeRow> frontier(List<NodeRow> rows) {
        List<NodeRow> waiting = new ArrayList<>();
        for (NodeRow row : rows) {
            if (row.state() == State.FRONTIER) {
                waiting.add(row);
            }
        }
        waiting.sort(Comparator.comparingDouble((NodeRow row) -> numberOf(row.label())));   // stable: ties keep node order
        return waiting;
    }

    /** "settled 2", "relaxed 5": one short text per counter, in the order the algorithm declared them. */
    public static List<String> counters(Step step) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> entry : step.counters().entrySet()) {
            out.add(entry.getKey() + " " + entry.getValue());
        }
        return out;
    }

    /** What a chip in the queue shows: "C 4", or just "C" when the node has no label. */
    public static String chipText(NodeRow row) {
        return row.label().isEmpty() ? row.name() : row.name() + " " + row.label();
    }

    private static double numberOf(String label) {
        if (label == null || label.isEmpty()) {
            return Double.MAX_VALUE;
        }
        try {
            return Labels.parse(label);
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }
}

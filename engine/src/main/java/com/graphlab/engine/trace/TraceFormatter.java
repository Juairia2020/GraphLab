package com.graphlab.engine.trace;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a trace into plain text for logs, tests and debugging. One line per step: step number,
 * pseudocode line, message, then every node as {@code name=label[state]} and the counters.
 * State letters: I idle, F frontier, C chosen, V visited, P path, X fail.
 */
public final class TraceFormatter {
    private TraceFormatter() {
    }

    /** For algorithms that do not work on a graph (sorting, knapsack). */
    public static String format(Algorithm algorithm, Trace trace) {
        return format(null, algorithm, trace);
    }

    public static String format(Graph graph, Algorithm algorithm, Trace trace) {
        StringBuilder out = new StringBuilder();
        out.append(algorithm.name()).append(" | ").append(graph == null ? algorithm.inputKind() : graph).append(" | ")
                .append(trace.size()).append(" steps\n");
        List<String> code = algorithm.pseudocode();
        for (int i = 0; i < code.size(); i++) {
            out.append(String.format("  L%d  %s%n", i + 1, code.get(i)));
        }
        out.append('\n');
        for (int i = 0; i < trace.size(); i++) {
            Step step = trace.step(i);
            out.append(String.format("%3d  L%d  %s%n", i + 1, step.line(), step.message()));
            out.append("       ").append(describe(graph, step.frame()));
            if (!step.counters().isEmpty()) {
                out.append("   ").append(step.counters());
            }
            out.append('\n');
        }
        out.append('\n').append(graph == null ? "Result: " + trace.result() : describeResult(graph, trace.result())).append('\n');
        return out.toString();
    }

    static String describe(Graph graph, Frame frame) {
        List<String> parts = new ArrayList<>();
        if (frame instanceof GraphFrame g && graph != null) {
            for (Node node : graph.nodes()) {
                String label = g.nodeLabel(node.id());
                parts.add(graph.nodeName(node.id()) + (label.isEmpty() ? "" : "=" + label)
                        + "[" + letter(g.nodeState(node.id())) + "]");
            }
        } else if (frame instanceof ArrayFrame a) {
            for (int i = 0; i < a.values().size(); i++) {
                parts.add(a.values().get(i) + "[" + letter(a.states().get(i)) + "]");
            }
            if (!a.markers().isEmpty()) {
                parts.add(a.markers().toString());
            }
        } else if (frame instanceof GridFrame g) {
            for (int r = 0; r < g.rows(); r++) {
                StringBuilder row = new StringBuilder(g.rowHeaders().get(r)).append(':');
                for (int c = 0; c < g.cols(); c++) {
                    row.append(' ').append(g.cell(r, c).isEmpty() ? "." : g.cell(r, c)).append('[').append(letter(g.state(r, c))).append(']');
                }
                parts.add(row.toString());
            }
        }
        return String.join(" ", parts);
    }

    static String describeResult(Graph graph, Result result) {
        StringBuilder out = new StringBuilder("Result:");
        if (result.negativeCycle()) {
            return out.append(" negative cycle reachable from the start; distances do not exist").toString();
        }
        if (!result.visitOrder().isEmpty()) {
            out.append("\n  visit order : ").append(names(graph, result.visitOrder()));
        }
        if (!result.finishOrder().isEmpty()) {
            out.append("\n  finish order: ").append(names(graph, result.finishOrder()));
        }
        if (!result.distances().isEmpty()) {
            List<String> d = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : result.distances().entrySet()) {
                d.add(graph.nodeName(e.getKey()) + "=" + Labels.number(e.getValue()));
            }
            out.append("\n  distances   : ").append(String.join(" ", d));
        }
        if (!result.treeEdgeIds().isEmpty()) {
            List<String> t = new ArrayList<>();
            for (int id : result.treeEdgeIds()) {
                Edge e = graph.edge(id);
                t.add(graph.nodeName(e.from()) + (graph.isDirected() ? "->" : "-") + graph.nodeName(e.to()));
            }
            out.append("\n  tree edges  : ").append(String.join(", ", t))
                    .append("  (total weight ").append(Labels.number(result.totalWeight())).append(')');
        }
        return out.toString();
    }

    private static String names(Graph graph, List<Integer> ids) {
        List<String> names = new ArrayList<>();
        for (int id : ids) {
            names.add(graph.nodeName(id));
        }
        return String.join(", ", names);
    }

    private static char letter(State state) {
        return switch (state) {
            case IDLE -> 'I';
            case FRONTIER -> 'F';
            case CHOSEN -> 'C';
            case VISITED -> 'V';
            case PATH -> 'P';
            case FAIL -> 'X';
        };
    }
}

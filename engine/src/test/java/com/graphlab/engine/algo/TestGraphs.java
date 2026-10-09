package com.graphlab.engine.algo;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small graphs and helpers shared by the algorithm tests. */
public final class TestGraphs {
    private TestGraphs() {
    }

    /** A-B-C-... with every edge weight {@code weight}; edge i joins node i and i+1. */
    public static Graph chain(int nodes, double weight, boolean directed) {
        Graph.Builder b = Graph.builder(directed).nodes(nodes);
        for (int i = 0; i + 1 < nodes; i++) {
            b.edge(i, i + 1, weight);
        }
        return b.build();
    }

    /** A cycle of {@code nodes} nodes plus a chord from every 5th node to the node 7 ahead. */
    public static Graph ringWithChords(int nodes) {
        Graph.Builder b = Graph.builder(false).nodes(nodes);
        for (int i = 0; i < nodes; i++) {
            b.edge(i, (i + 1) % nodes, 1 + (i % 4));
        }
        for (int i = 0; i < nodes; i += 5) {
            b.edge(i, (i + 7) % nodes, 3);
        }
        return b.build();
    }

    /** Two separate triangles plus one isolated node: 7 nodes, never connected. */
    public static Graph disconnected() {
        return Graph.builder(false).nodes(7)
                .edge(0, 1, 2).edge(1, 2, 3).edge(0, 2, 4)
                .edge(3, 4, 1).edge(4, 5, 5).edge(3, 5, 2)
                .build();
    }

    /** Parallel edges (two between A and B), a self-loop on C and a zero-weight edge. */
    public static Graph awkward() {
        return Graph.builder(false).nodes(4)
                .edge(0, 1, 5).edge(0, 1, 2).edge(1, 2, 0).edge(2, 2, 1).edge(2, 3, 4)
                .build();
    }

    public static Graph singleNode() {
        return Graph.builder(false).nodes(1).build();
    }

    /** Every undirected graph the contract tests run each algorithm on, with a label for failure messages. */
    public static Map<String, Graph> undirectedSuite() {
        return Map.of(
                "sample", com.graphlab.engine.model.SampleGraph.sixNodeUndirected(),
                "chain", chain(6, 2, false),
                "disconnected", disconnected(),
                "awkward", awkward(),
                "single", singleNode(),
                "ring60", ringWithChords(60));
    }

    public static GraphFrame lastFrame(Trace trace) {
        return (GraphFrame) trace.last().frame();
    }

    public static Set<Integer> edgesIn(GraphFrame frame, State state) {
        Set<Integer> ids = new HashSet<>();
        frame.edgeStates().forEach((id, s) -> {
            if (s == state) {
                ids.add(id);
            }
        });
        return ids;
    }

    public static Set<Integer> nodesIn(GraphFrame frame, State state) {
        Set<Integer> ids = new HashSet<>();
        frame.nodeStates().forEach((id, s) -> {
            if (s == state) {
                ids.add(id);
            }
        });
        return ids;
    }

    public static double weightOf(Graph graph, List<Integer> edgeIds) {
        double sum = 0;
        for (int id : edgeIds) {
            Edge e = graph.edge(id);
            sum += e.weight();
        }
        return sum;
    }
}

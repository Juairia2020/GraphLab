package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kruskal's minimum spanning tree (undirected graphs only). Edges are tried lightest first, ties
 * by lower edge id. Accepted edges turn PATH and their ends VISITED; an edge that would close a
 * cycle turns FAIL. Stops as soon as n - 1 edges are accepted. On a disconnected graph the
 * result is a minimum spanning forest.
 */
public final class Kruskal implements Algorithm {
    public static final String ID = "kruskal";

    private static final List<String> PSEUDOCODE = List.of(
            "sort all edges by weight, lightest first",
            "make every node its own set",
            "for each edge (u, v, w) in order (stop at n - 1 accepted edges)",
            "    if u and v are in different sets",
            "        accept the edge and merge the two sets",
            "    else skip the edge (it would make a cycle)");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Kruskal";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireUndirected(graph, name());

        List<Edge> sorted = new ArrayList<>(graph.edges());
        sorted.sort(Comparator.comparingDouble(Edge::weight).thenComparingInt(Edge::id));

        Map<Integer, Integer> setOf = new HashMap<>();     // node id -> representative
        for (Node node : graph.nodes()) {
            setOf.put(node.id(), node.id());
        }

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("accepted", 0).counter("skipped", 0);
        Result.Builder result = Result.builder();

        rec.step(paint.build(), 1, "Sort the " + sorted.size() + " edges by weight, lightest first");
        rec.step(paint.build(), 2, "Every node starts in its own set");

        int needed = graph.nodeCount() - 1;
        int accepted = 0;
        int skipped = 0;
        double total = 0;
        for (Edge edge : sorted) {
            if (accepted == needed) {
                break;
            }
            String name = graph.nodeName(edge.from()) + "-" + graph.nodeName(edge.to());
            paint.edge(edge.id(), State.CHOSEN);
            rec.step(paint.build(), 3, "Consider edge " + name + " (weight "
                    + Labels.number(edge.weight()) + ")");

            int a = find(setOf, edge.from());
            int b = find(setOf, edge.to());
            if (a != b) {
                setOf.put(a, b);
                accepted++;
                total += edge.weight();
                result.treeEdge(edge.id());
                paint.edge(edge.id(), State.PATH)
                        .node(edge.from(), State.VISITED).node(edge.to(), State.VISITED);
                rec.set("accepted", accepted);
                rec.step(paint.build(), 5, "Accept " + name + ": the ends are in different sets. "
                        + "Total weight so far " + Labels.number(total));
            } else {
                skipped++;
                paint.edge(edge.id(), State.FAIL);
                rec.set("skipped", skipped);
                rec.step(paint.build(), 6, "Skip " + name + ": its ends are already connected, "
                        + "so it would make a cycle");
            }
        }

        result.totalWeight(total);
        String summary = accepted == needed
                ? "Done: a minimum spanning tree with " + accepted + " edges, total weight "
                : "Done: the graph is not connected, so this is a spanning forest with "
                        + accepted + " edges, total weight ";
        rec.step(paint.build(), 3, summary + Labels.number(total));
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static int find(Map<Integer, Integer> setOf, int node) {
        int root = node;
        while (setOf.get(root) != root) {
            root = setOf.get(root);
        }
        int current = node;
        while (setOf.get(current) != root) {      // path compression
            int next = setOf.get(current);
            setOf.put(current, root);
            current = next;
        }
        return root;
    }
}

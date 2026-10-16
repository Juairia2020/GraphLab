package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Topological sort by Kahn's method (MS18): count the edges coming into every node, repeatedly take a node with none left, and remove
 * its outgoing edges. When several nodes are ready, the one listed first in the graph goes first, so the answer is the smallest
 * possible order and is always the same. If nodes remain but none is ready, the graph has a cycle.
 *
 * <p>Works on directed graphs. Labels show the number of edges still coming in; a placed node shows its position. Result:
 * {@link Result#visitOrder()} is the order, {@link Result#value()} the number of nodes placed, and {@link Result#text()} is
 * "cycle" when the graph has one, else the order as node names.
 */
public final class TopologicalSort implements Algorithm {
    public static final String ID = "topological-sort";
    public static final String CYCLE = "cycle";

    private static final List<String> PSEUDOCODE = List.of(
            "count in[v], the edges coming into each node v",
            "put every node with in[v] = 0 into the ready set",
            "while the ready set is not empty",
            "    take the first ready node u and put it next in the order",
            "    for each edge (u, v): in[v] = in[v] - 1; if in[v] = 0, v is ready",
            "if every node is in the order, it is a topological order",
            "otherwise the nodes left are on or behind a cycle");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Topological sort (Kahn)";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public Graph exampleGraph() {
        return com.graphlab.engine.model.SampleGraph.sixNodeDag();
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.GRAPH_SEARCH;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        if (!graph.isDirected()) {
            throw new IllegalArgumentException(name() + " needs a directed graph.");
        }
        Map<Integer, Integer> position = new HashMap<>();   // node id to its place in the graph's node list
        for (int i = 0; i < graph.nodes().size(); i++) {
            position.put(graph.nodes().get(i).id(), i);
        }
        Map<Integer, Integer> in = new HashMap<>();
        for (Node node : graph.nodes()) {
            in.put(node.id(), 0);
        }
        for (var edge : graph.edges()) {
            in.merge(edge.to(), 1, Integer::sum);
        }
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("placed", 0).counter("ready", 0);
        Result.Builder result = Result.builder();
        PriorityQueue<Integer> ready = new PriorityQueue<>((a, b) -> Integer.compare(position.get(a), position.get(b)));

        for (Node node : graph.nodes()) {
            paint.label(node.id(), Integer.toString(in.get(node.id())));
        }
        rec.step(paint.build(), 1, "Count the edges coming into each node (shown on the node)");
        for (Node node : graph.nodes()) {
            if (in.get(node.id()) == 0) {
                ready.add(node.id());
                paint.node(node.id(), State.FRONTIER);
            }
        }
        readyPanel(paint, graph, ready, position);
        rec.set("ready", ready.size());
        rec.step(paint.build(), 2, ready.isEmpty() ? "No node has zero incoming edges"
                : "Nodes with no incoming edge are ready: " + readyNames(graph, ready, position));

        List<Integer> order = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        while (!ready.isEmpty()) {
            int u = ready.poll();
            order.add(u);
            result.visit(u);
            names.append(names.length() == 0 ? "" : " ").append(graph.nodeName(u));
            paint.node(u, State.CHOSEN).label(u, "#" + order.size());
            rec.set("placed", order.size());
            readyPanel(paint, graph, ready, position);
            rec.step(paint.build(), 4, "Take " + graph.nodeName(u) + " as number " + order.size());
            for (Arc arc : graph.arcsFrom(u)) {
                int v = arc.to();
                in.merge(v, -1, Integer::sum);
                paint.edge(arc.edge().id(), State.VISITED);
                paint.label(v, Integer.toString(in.get(v)));
                String message = "Remove edge " + graph.arcName(arc) + ": " + graph.nodeName(v) + " has " + in.get(v) + " left";
                if (in.get(v) == 0) {
                    ready.add(v);
                    paint.node(v, State.FRONTIER);
                    message += ", so it is ready";
                }
                readyPanel(paint, graph, ready, position);
                rec.set("ready", ready.size());
                rec.step(paint.build(), 5, message);
            }
            paint.node(u, State.VISITED);
        }
        result.value(order.size());
        if (order.size() == graph.nodeCount()) {
            result.text(names.toString());
            rec.step(paint.build(), 6, "All " + order.size() + " nodes are placed: " + names);
        } else {
            result.text(CYCLE);
            for (Node node : graph.nodes()) {
                if (!order.contains(node.id())) {
                    paint.node(node.id(), State.FAIL);
                }
            }
            rec.step(paint.build(), 7, "No node is ready but " + (graph.nodeCount() - order.size())
                    + " are left (marked): the graph has a cycle, so no topological order exists");
        }
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static String readyNames(Graph graph, PriorityQueue<Integer> ready, Map<Integer, Integer> position) {
        List<Integer> sorted = new ArrayList<>(ready);
        sorted.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
        StringBuilder out = new StringBuilder();
        for (int id : sorted) {
            out.append(out.length() == 0 ? "" : ", ").append(graph.nodeName(id));
        }
        return out.toString();
    }

    private static void readyPanel(GraphFrame.Builder paint, Graph graph, PriorityQueue<Integer> ready,
            Map<Integer, Integer> position) {
        List<Integer> sorted = new ArrayList<>(ready);
        sorted.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
        List<String> items = new ArrayList<>();
        for (int id : sorted) {
            items.add(graph.nodeName(id));
        }
        paint.panel("Ready (first goes next)", items);
    }
}

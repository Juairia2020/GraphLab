package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra's shortest paths from one node. Labels show the best distance known so far
 * (the infinity sign until a node is reached). Node states: FRONTIER = reached but not settled,
 * CHOSEN = being settled now, VISITED = settled. The current best parent edge of each reached
 * node is shown as PATH, so the finished picture is the shortest-path tree.
 *
 * <p>Ties between equal distances go to the lower node id. Negative weights are rejected.
 */
public final class Dijkstra implements Algorithm {
    public static final String ID = "dijkstra";

    private static final List<String> PSEUDOCODE = List.of(
            "dist[all] = infinity, dist[start] = 0",
            "put the start node in a priority queue",
            "while the queue is not empty",
            "    u = node with the smallest dist (settle it)",
            "    for each edge (u, v, w)",
            "        if dist[u] + w is not less than dist[v]: no change",
            "        else dist[v] = dist[u] + w, parent[v] = u",
            "    mark u settled");

    private record Entry(double dist, int node) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Dijkstra";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireSource(graph, params, name());
        AlgoSupport.requireNonNegativeWeights(graph, name(), "Bellman-Ford");
        int source = params.source();

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("settled", 0).counter("relaxed", 0);
        Result.Builder result = Result.builder();

        Map<Integer, Double> dist = new HashMap<>();
        for (Node node : graph.nodes()) {
            dist.put(node.id(), Double.POSITIVE_INFINITY);
            paint.label(node.id(), Labels.INFINITY);
        }
        dist.put(source, 0.0);
        paint.label(source, "0").node(source, State.FRONTIER);

        Map<Integer, Integer> parentEdge = new HashMap<>();
        Set<Integer> settled = new HashSet<>();
        List<Integer> settleOrder = new java.util.ArrayList<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(Entry::dist).thenComparingInt(Entry::node));
        queue.add(new Entry(0.0, source));
        rec.step(paint.build(), 1, "Every distance starts at infinity, except " + graph.nodeName(source)
                + " which is 0");
        rec.step(paint.build(), 2, "Put " + graph.nodeName(source) + " in the priority queue");

        int relaxed = 0;
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            int u = entry.node();
            if (settled.contains(u) || entry.dist() > dist.get(u)) {
                continue;   // an older, worse entry for a node that was improved since
            }
            settled.add(u);
            settleOrder.add(u);
            paint.node(u, State.CHOSEN);
            rec.set("settled", settled.size() - 1L);
            rec.step(paint.build(), 4, "Settle " + graph.nodeName(u) + ": its distance "
                    + Labels.number(dist.get(u)) + " is the smallest left");

            for (Arc arc : graph.arcsFrom(u)) {
                int edgeId = arc.edge().id();
                int v = arc.to();
                double candidate = dist.get(u) + arc.weight();
                State before = paint.edgeState(edgeId);
                paint.edge(edgeId, State.CHOSEN);
                rec.step(paint.build(), 5, "Check edge " + graph.arcName(arc) + " (weight "
                        + Labels.number(arc.weight()) + ")");

                if (candidate < dist.get(v)) {
                    Integer old = parentEdge.get(v);
                    dist.put(v, candidate);
                    parentEdge.put(v, edgeId);
                    if (old != null && !parentEdge.containsValue(old)) {
                        paint.edge(old, State.IDLE);
                    }
                    paint.edge(edgeId, State.PATH).label(v, Labels.number(candidate));
                    if (paint.nodeState(v) == State.IDLE) {
                        paint.node(v, State.FRONTIER);
                    }
                    queue.add(new Entry(candidate, v));
                    relaxed++;
                    rec.set("relaxed", relaxed);
                    rec.step(paint.build(), 7, "Better route to " + graph.nodeName(v) + ": "
                            + Labels.number(dist.get(u)) + " + " + Labels.number(arc.weight())
                            + " = " + Labels.number(candidate));
                } else {
                    rec.step(paint.build(), 6, Labels.number(candidate) + " is not less than "
                            + Labels.number(dist.get(v)) + ", so " + graph.nodeName(v)
                            + " stays as it is");
                    paint.edge(edgeId, before);
                }
            }

            paint.node(u, State.VISITED);
            rec.set("settled", settled.size());
            rec.step(paint.build(), 8, graph.nodeName(u) + " is settled");
        }

        for (int node : settleOrder) {
            result.visit(node);
            Integer edgeId = parentEdge.get(node);
            if (edgeId != null) {
                result.parent(node, edgeId).treeEdge(edgeId);
            }
        }
        double total = 0;
        for (int edgeId : parentEdge.values()) {
            total += graph.edge(edgeId).weight();
        }
        for (Node node : graph.nodes()) {
            result.distance(node.id(), dist.get(node.id()));
        }
        result.totalWeight(total);
        rec.step(paint.build(), 3, "The queue is empty: " + settled.size() + " of "
                + graph.nodeCount() + " nodes reached, distances are final");
        return new Trace(ID, params, rec.steps(), result.build());
    }
}

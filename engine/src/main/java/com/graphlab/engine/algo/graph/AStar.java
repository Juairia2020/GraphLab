package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* from a start node to an end node on a graph whose nodes have positions. It is Dijkstra with a guide: the next node to
 * settle is the one with the smallest {@code g + h}, where g is the distance found so far and h is the straight-line
 * (Euclidean) distance from the node to the end, read from the node positions.
 *
 * <p><b>Admissible heuristic.</b> A* is exact only when h never overestimates, which holds when no edge is shorter than the
 * straight line between its ends. The run checks every edge. If one is shorter, the first step and the result carry a
 * warning, and h is multiplied by the largest factor that is still safe (the smallest ratio weight / straight length), so the
 * answer stays correct. Labels show g; the priority queue panel shows "node:f".
 */
public final class AStar implements Algorithm {
    public static final String ID = "astar";
    private static final double EPS = 1e-9;

    private static final List<String> PSEUDOCODE = List.of(
            "g[all] = infinity, g[start] = 0",
            "put the start node in a priority queue ordered by f = g + h",
            "while the queue is not empty",
            "    u = node with the smallest f (settle it); if u is the end: stop",
            "    for each edge (u, v, w)",
            "        if g[u] + w is not less than g[v]: no change",
            "        else g[v] = g[u] + w, parent[v] = u, queue v with f = g[v] + h[v]",
            "h = straight-line distance to the end; it must never be more than the real distance");

    private record Entry(double f, int node) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "A*";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.SHORTEST_PATHS;
    }

    @Override
    public boolean needsTarget() {
        return true;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    /**
     * The factor to multiply the straight-line distance by: 1 when no edge is shorter than its straight line, otherwise the
     * smallest weight / length over the edges (so the heuristic never overestimates).
     */
    public static double safeScale(Graph graph) {
        double scale = 1.0;
        for (Edge e : graph.edges()) {
            double len = straight(graph.node(e.from()), graph.node(e.to()));
            if (len > EPS && e.weight() < len) {
                scale = Math.min(scale, e.weight() / len);
            }
        }
        return scale;
    }

    /** True when no edge is shorter than the straight line between its two nodes. */
    public static boolean admissible(Graph graph) {
        return safeScale(graph) >= 1.0 - EPS;
    }

    private static double straight(Node a, Node b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireSource(graph, params, name());
        if (!params.hasTarget()) {
            throw new IllegalArgumentException(name() + " needs an end node");
        }
        if (!graph.hasNode(params.target())) {
            throw new IllegalArgumentException("End node " + params.target() + " is not in the graph");
        }
        AlgoSupport.requireNonNegativeWeights(graph, name(), "Bellman-Ford");
        int source = params.source();
        int target = params.target();
        final double scale = safeScale(graph);
        final boolean safe = scale >= 1.0 - EPS;
        final Node goal = graph.node(target);

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("settled", 0).counter("relaxed", 0);
        Result.Builder result = Result.builder();

        Map<Integer, Double> g = new HashMap<>();
        for (Node node : graph.nodes()) {
            g.put(node.id(), Double.POSITIVE_INFINITY);
            paint.label(node.id(), Labels.INFINITY);
        }
        g.put(source, 0.0);
        paint.label(source, "0").node(source, State.FRONTIER).node(target, State.FRONTIER);
        paint.label(target, graph.node(target).id() == source ? "0" : Labels.INFINITY);

        Map<Integer, Integer> parentEdge = new HashMap<>();
        Set<Integer> settled = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingDouble(Entry::f).thenComparingInt(Entry::node));
        queue.add(new Entry(scale * straight(graph.node(source), goal), source));
        panel(paint, graph, queue, g, settled, scale, goal);

        if (!safe) {
            rec.step(paint.build(), 8, "Warning: some edges are shorter than the straight line between their nodes, so the plain "
                    + "straight-line guide could overestimate. The guide is scaled by " + Labels.number(Math.round(scale * 1000) / 1000.0)
                    + " to keep the answer correct");
        }
        rec.step(paint.build(), 1, "Every g starts at infinity, except " + graph.nodeName(source) + " which is 0");
        rec.step(paint.build(), 2, "Put " + graph.nodeName(source) + " in the queue with f = 0 + h");

        int relaxed = 0;
        boolean reached = false;
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            int u = entry.node();
            if (settled.contains(u)) {
                continue;
            }
            settled.add(u);
            order.add(u);
            panel(paint, graph, queue, g, settled, scale, goal);
            paint.node(u, State.CHOSEN);
            rec.set("settled", settled.size());
            rec.step(paint.build(), 4, "Settle " + graph.nodeName(u) + ": its f = g + h = "
                    + Labels.number(entry.f()) + " is the smallest left");
            if (u == target) {
                reached = true;
                paint.node(u, State.VISITED);
                break;
            }
            for (Arc arc : graph.arcsFrom(u)) {
                int edgeId = arc.edge().id();
                int v = arc.to();
                if (settled.contains(v)) {
                    continue;
                }
                double candidate = g.get(u) + arc.weight();
                State before = paint.edgeState(edgeId);
                paint.edge(edgeId, State.CHOSEN);
                rec.step(paint.build(), 5, "Check edge " + graph.arcName(arc) + " (weight " + Labels.number(arc.weight()) + ")");
                if (candidate < g.get(v) - EPS) {
                    Integer old = parentEdge.get(v);
                    g.put(v, candidate);
                    parentEdge.put(v, edgeId);
                    if (old != null && !parentEdge.containsValue(old)) {
                        paint.edge(old, State.IDLE);
                    }
                    paint.edge(edgeId, State.PATH).label(v, Labels.number(candidate));
                    if (paint.nodeState(v) == State.IDLE) {
                        paint.node(v, State.FRONTIER);
                    }
                    queue.add(new Entry(candidate + scale * straight(graph.node(v), goal), v));
                    panel(paint, graph, queue, g, settled, scale, goal);
                    relaxed++;
                    rec.set("relaxed", relaxed);
                    rec.step(paint.build(), 7, "Better route to " + graph.nodeName(v) + ": " + Labels.number(g.get(u)) + " + "
                            + Labels.number(arc.weight()) + " = " + Labels.number(candidate));
                } else {
                    rec.step(paint.build(), 6, Labels.number(candidate) + " is not less than " + Labels.number(g.get(v))
                            + ", so " + graph.nodeName(v) + " stays as it is");
                    paint.edge(edgeId, before);
                }
            }
            paint.node(u, State.VISITED);
            rec.step(paint.build(), 3, graph.nodeName(u) + " is settled");
        }

        for (int node : order) {
            result.visit(node);
            Integer edgeId = parentEdge.get(node);
            if (edgeId != null) {
                result.parent(node, edgeId);
            }
        }
        for (Node node : graph.nodes()) {
            result.distance(node.id(), g.get(node.id()));
        }
        double length = 0;
        if (reached) {
            for (int x = target; parentEdge.containsKey(x); ) {
                Edge e = graph.edge(parentEdge.get(x));
                result.treeEdge(e.id());
                length += e.weight();
                x = e.to() == x ? e.from() : e.to();
            }
        }
        result.totalWeight(length).value(reached ? length : 0);
        String note = safe ? "" : " Warning: the straight-line guide was scaled down because some edges are shorter than the straight line.";
        result.text(reached
                ? "Shortest route " + graph.nodeName(source) + " to " + graph.nodeName(target) + ": " + Labels.number(length)
                + ", " + order.size() + " of " + graph.nodeCount() + " nodes settled." + note
                : graph.nodeName(target) + " cannot be reached from " + graph.nodeName(source) + "." + note);
        rec.step(paint.build(), 3, reached
                ? "The end node is settled: the shortest route costs " + Labels.number(length) + " (" + order.size() + " of "
                + graph.nodeCount() + " nodes settled)"
                : "The queue is empty: the end node cannot be reached");
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static void panel(GraphFrame.Builder paint, Graph graph, PriorityQueue<Entry> queue, Map<Integer, Double> g,
            Set<Integer> settled, double scale, Node goal) {
        List<Entry> live = new ArrayList<>();
        for (Entry entry : queue) {
            double f = g.get(entry.node()) + scale * straight(graph.node(entry.node()), goal);
            if (!settled.contains(entry.node()) && entry.f() <= f + EPS) {
                live.add(entry);
            }
        }
        live.sort(Comparator.comparingDouble(Entry::f).thenComparingInt(Entry::node));
        List<String> items = new ArrayList<>();
        for (Entry entry : live) {
            items.add(graph.nodeName(entry.node()) + ":" + Labels.number(entry.f()));
        }
        paint.panel("Priority queue (f)", items);
    }
}

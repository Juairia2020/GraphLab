package com.graphlab.engine.algo.graph;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Edmonds-Karp, the maximum flow with breadth-first search. Edge weights are capacities. It
 * repeatedly finds the shortest route from the start (the source) to the end (the sink) that still
 * has spare capacity, and pushes as much as the tightest edge allows along it. When no such route
 * is left, the flow is maximal and the edges leaving the nodes the search can still reach are the
 * minimum cut: their capacities add up to the flow (the max-flow min-cut theorem).
 *
 * <p>Works on directed graphs (flow follows the arrows) and undirected ones (flow may go either
 * way along an edge, up to its capacity). Each edge is labelled "flow/capacity". Edges: IDLE carries
 * nothing, PATH carries some, VISITED is full, CHOSEN is on the route being used, FAIL (dashed) is in
 * the minimum cut.
 */
public final class EdmondsKarp implements Algorithm {
    public static final String ID = "edmonds-karp";
    private static final double EPS = 1e-9;
    private static final int MAX_STEPS = 4000;

    private static final List<String> PSEUDOCODE = List.of(
            "flow = 0 on every edge",
            "repeat",
            "    find the shortest route from the start to the end that still has spare capacity (BFS)",
            "    if there is no such route: stop",
            "    b = the smallest spare capacity on the route",
            "    push b more flow along the route",
            "max flow = the flow out of the start; min cut = the edges leaving what the last search could reach");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Edmonds-Karp (max flow)";
    }

    @Override
    public boolean needsTarget() {
        return true;
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.NETWORK_FLOW;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireSource(graph, params, name());
        if (!params.hasTarget()) {
            throw new IllegalArgumentException(name() + " needs an end node (the sink)");
        }
        if (!graph.hasNode(params.target())) {
            throw new IllegalArgumentException("End node " + params.target() + " is not in the graph");
        }
        if (params.source() == params.target()) {
            throw new IllegalArgumentException("The start and the end must be different nodes");
        }
        for (Edge edge : graph.edges()) {
            if (edge.weight() < 0) {
                throw new IllegalArgumentException(name() + " needs non-negative capacities, but edge "
                        + graph.nodeName(edge.from()) + (graph.isDirected() ? "->" : "-") + graph.nodeName(edge.to())
                        + " has weight " + Labels.number(edge.weight()));
            }
        }

        int n = graph.nodeCount();
        int m = graph.edgeCount();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(graph.nodes().get(i).id(), i);
        }
        // Arc 2e is edge e's own direction, arc 2e+1 its reverse. Net flow on 2e+1 is minus the flow on 2e.
        int[] head = new int[2 * m];
        double[] cap = new double[2 * m];
        double[] flow = new double[2 * m];
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        for (int e = 0; e < m; e++) {
            Edge edge = graph.edges().get(e);
            int u = index.get(edge.from());
            int v = index.get(edge.to());
            head[2 * e] = v;
            head[2 * e + 1] = u;
            cap[2 * e] = edge.weight();
            cap[2 * e + 1] = graph.isDirected() ? 0 : edge.weight();
            out.get(u).add(2 * e);
            out.get(v).add(2 * e + 1);
        }

        int s = index.get(params.source());
        int t = index.get(params.target());
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("augmentations", 0).counter("flow", 0)
                .limit(MAX_STEPS, "This graph needs too many steps to show. Try one with fewer or smaller capacities.");
        String sName = graph.nodeName(params.source());
        String tName = graph.nodeName(params.target());

        paint.label(params.source(), "start").label(params.target(), "end");
        paintFlow(graph, paint, flow, cap);
        rec.step(paint.build(), 1, "Find the largest flow from " + sName + " to " + tName + ". Each edge shows flow/capacity, and starts at 0.");

        double total = 0;
        int augmentations = 0;
        List<Integer> reachable;
        while (true) {
            // Breadth-first search over arcs with spare capacity.
            int[] viaArc = new int[n];
            Arrays.fill(viaArc, -1);
            boolean[] seen = new boolean[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            seen[s] = true;
            while (!queue.isEmpty() && !seen[t]) {
                int u = queue.poll();
                for (int arc : out.get(u)) {
                    int v = head[arc];
                    if (!seen[v] && cap[arc] - flow[arc] > EPS) {
                        seen[v] = true;
                        viaArc[v] = arc;
                        queue.add(v);
                    }
                }
            }
            if (!seen[t]) {
                reachable = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (seen[i]) {
                        reachable.add(i);
                    }
                }
                break;
            }
            // The route, from the end back to the start.
            List<Integer> route = new ArrayList<>();
            double bottleneck = Double.POSITIVE_INFINITY;
            for (int v = t; v != s; ) {
                int arc = viaArc[v];
                route.add(0, arc);
                bottleneck = Math.min(bottleneck, cap[arc] - flow[arc]);
                v = head[arc ^ 1];
            }
            augmentations++;
            rec.set("augmentations", augmentations);
            StringBuilder path = new StringBuilder(sName);
            for (int arc : route) {
                path.append(" \u2192 ").append(graph.nodes().get(head[arc]).label());
            }

            paintFlow(graph, paint, flow, cap);
            for (int arc : route) {
                paint.edge(graph.edges().get(arc >> 1).id(), State.CHOSEN);
                paint.node(graph.nodes().get(head[arc]).id(), State.FRONTIER);
            }
            paint.node(params.source(), State.FRONTIER);
            rec.step(paint.build(), 3, "Shortest route with spare capacity: " + path + ". The tightest edge has "
                    + Labels.number(bottleneck) + " to spare, so push " + Labels.number(bottleneck));

            for (int arc : route) {
                flow[arc] += bottleneck;
                flow[arc ^ 1] -= bottleneck;
            }
            total += bottleneck;
            rec.set("flow", Math.round(total));
            paintFlow(graph, paint, flow, cap);
            paint.recolourNodes(State.FRONTIER, State.IDLE);
            rec.step(paint.build(), 6, "Pushed " + Labels.number(bottleneck) + " along the route. The flow from " + sName + " to " + tName
                    + " is now " + Labels.number(total));
        }

        // The minimum cut: edges between what the last search reached and the rest.
        boolean[] inS = new boolean[n];
        for (int i : reachable) {
            inS[i] = true;
        }
        Result.Builder result = Result.builder().value(total);
        List<Integer> sourceSide = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (inS[i]) {
                sourceSide.add(graph.nodes().get(i).id());
            }
        }
        result.sourceSide(sourceSide);
        paintFlow(graph, paint, flow, cap);
        double cutCapacity = 0;
        List<String> cutNames = new ArrayList<>();
        for (int e = 0; e < m; e++) {
            Edge edge = graph.edges().get(e);
            int u = index.get(edge.from());
            int v = index.get(edge.to());
            boolean crosses = graph.isDirected() ? inS[u] && !inS[v] : inS[u] != inS[v];
            if (crosses) {
                paint.edge(edge.id(), State.FAIL);
                result.cutEdge(edge.id());
                cutCapacity += edge.weight();
                cutNames.add(graph.nodeName(edge.from()) + (graph.isDirected() ? "\u2192" : "-") + graph.nodeName(edge.to()));
            }
            double net = flow[2 * e];
            result.flow(edge.id(), net);
            if (Math.abs(net) > EPS) {
                result.treeEdge(edge.id());
            }
        }
        for (int i : reachable) {
            paint.node(graph.nodes().get(i).id(), State.VISITED);
        }
        rec.step(paint.build(), 7, "No route with spare capacity is left, so the maximum flow is " + Labels.number(total) + ". "
                + "The nodes the search can still reach (ringed) are cut off from " + tName + " by the dashed edges ("
                + String.join(", ", cutNames) + "), whose capacities add up to " + Labels.number(cutCapacity)
                + ": the minimum cut");
        return new Trace(ID, params, rec.steps(), result.build());
    }

    /** Edge states and "flow/capacity" labels from the current flow. */
    private static void paintFlow(Graph graph, GraphFrame.Builder paint, double[] flow, double[] cap) {
        paint.recolourEdges(State.CHOSEN, State.IDLE).recolourEdges(State.PATH, State.IDLE)
                .recolourEdges(State.VISITED, State.IDLE).recolourEdges(State.FAIL, State.IDLE);
        paint.recolourNodes(State.CHOSEN, State.IDLE).recolourNodes(State.VISITED, State.IDLE);
        for (int e = 0; e < graph.edgeCount(); e++) {
            Edge edge = graph.edges().get(e);
            double used = Math.abs(flow[2 * e]);
            double capacity = cap[2 * e];
            State state = used <= EPS ? State.IDLE : used >= capacity - EPS ? State.VISITED : State.PATH;
            paint.edge(edge.id(), state);
            paint.edgeLabel(edge.id(), Labels.number(used) + "/" + Labels.number(capacity));
        }
    }
}

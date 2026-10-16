package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
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
 * Dinic's maximum flow (MS18). Each phase builds a level graph with breadth-first search (the number of edges from the source, over
 * edges with spare capacity), then pushes a blocking flow along edges that go from one level to the next, using a depth-first search
 * that remembers, for every node, the next edge to try. Phases repeat until the sink is out of reach. Edge weights are capacities;
 * directed graphs follow the arrows, undirected edges carry flow either way. The result has the same form as Edmonds-Karp:
 * the flow value, the flow on every edge, and a minimum cut.
 */
public final class Dinic implements Algorithm {
    public static final String ID = "dinic";
    private static final double EPS = 1e-9;
    private static final int MAX_STEPS = 4000;

    private static final List<String> PSEUDOCODE = List.of(
            "flow = 0 on every edge",
            "repeat",
            "    level[v] = fewest edges from the start to v over edges with spare capacity (BFS)",
            "    if the end has no level: stop",
            "    for each node, remember the next edge to try",
            "    while a route start to end exists that only goes up one level at every step (DFS)",
            "        push the smallest spare capacity along it",
            "max flow = the flow out of the start; min cut = the edges leaving what the last BFS could reach");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Dinic (max flow)";
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

    private static final class State0 {
        int n;
        int[] head;
        double[] cap;
        double[] flow;
        List<List<Integer>> out;
        int[] level;
        int[] next;
        int t;
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
        State0 st = new State0();
        st.n = n;
        st.head = new int[2 * m];
        st.cap = new double[2 * m];
        st.flow = new double[2 * m];
        st.out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            st.out.add(new ArrayList<>());
        }
        for (int e = 0; e < m; e++) {
            Edge edge = graph.edges().get(e);
            int u = index.get(edge.from());
            int v = index.get(edge.to());
            st.head[2 * e] = v;
            st.head[2 * e + 1] = u;
            st.cap[2 * e] = edge.weight();
            st.cap[2 * e + 1] = graph.isDirected() ? 0 : edge.weight();
            st.out.get(u).add(2 * e);
            st.out.get(v).add(2 * e + 1);
        }
        int s = index.get(params.source());
        st.t = index.get(params.target());
        int t = st.t;

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("phases", 0).counter("augmentations", 0).counter("flow", 0)
                .limit(MAX_STEPS, "This graph needs too many steps to show. Try one with fewer or smaller capacities.");
        String sName = graph.nodeName(params.source());
        String tName = graph.nodeName(params.target());
        paint.label(params.source(), "start").label(params.target(), "end");
        paintFlow(graph, paint, st);
        rec.step(paint.build(), 1, "Find the largest flow from " + sName + " to " + tName + ". Each edge shows flow/capacity, and starts at 0.");

        double total = 0;
        int phases = 0;
        int augmentations = 0;
        boolean[] reached;
        while (true) {
            st.level = new int[n];
            Arrays.fill(st.level, -1);
            st.level[s] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int arc : st.out.get(u)) {
                    int v = st.head[arc];
                    if (st.level[v] < 0 && st.cap[arc] - st.flow[arc] > EPS) {
                        st.level[v] = st.level[u] + 1;
                        queue.add(v);
                    }
                }
            }
            reached = new boolean[n];
            for (int i = 0; i < n; i++) {
                reached[i] = st.level[i] >= 0;
            }
            if (st.level[t] < 0) {
                break;
            }
            phases++;
            rec.set("phases", phases);
            paintFlow(graph, paint, st);
            for (int i = 0; i < n; i++) {
                if (st.level[i] >= 0 && i != s && i != t) {
                    paint.label(graph.nodes().get(i).id(), "L" + st.level[i]);
                }
                if (st.level[i] >= 0) {
                    paint.node(graph.nodes().get(i).id(), State.FRONTIER);
                }
            }
            rec.step(paint.build(), 3, "Phase " + phases + ": levels by breadth-first search over edges with spare capacity. The end is at level "
                    + st.level[t] + ". Only edges that go up one level may be used in this phase");
            st.next = new int[n];
            while (true) {
                List<Integer> route = new ArrayList<>();
                double pushed = find(st, s, Double.POSITIVE_INFINITY, route);
                if (pushed <= EPS) {
                    break;
                }
                augmentations++;
                rec.set("augmentations", augmentations);
                StringBuilder path = new StringBuilder(sName);
                java.util.Collections.reverse(route);
                for (int arc : route) {
                    path.append(" → ").append(graph.nodes().get(st.head[arc]).label());
                    st.flow[arc] += pushed;
                    st.flow[arc ^ 1] -= pushed;
                }
                total += pushed;
                rec.set("flow", Math.round(total));
                paintFlow(graph, paint, st);
                for (int arc : route) {
                    paint.edge(graph.edges().get(arc >> 1).id(), State.CHOSEN);
                }
                rec.step(paint.build(), 6, "Route " + path + " pushes " + Labels.number(pushed) + ". The flow is now " + Labels.number(total));
            }
            paintFlow(graph, paint, st);
            rec.step(paint.build(), 5, "Phase " + phases + " is finished: no route that goes up one level is left (a blocking flow). The flow is "
                    + Labels.number(total));
        }

        Result.Builder result = Result.builder().value(total);
        List<Integer> sourceSide = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (reached[i]) {
                sourceSide.add(graph.nodes().get(i).id());
            }
        }
        result.sourceSide(sourceSide);
        paintFlow(graph, paint, st);
        double cutCapacity = 0;
        List<String> cutNames = new ArrayList<>();
        for (int e = 0; e < m; e++) {
            Edge edge = graph.edges().get(e);
            int u = index.get(edge.from());
            int v = index.get(edge.to());
            boolean crosses = graph.isDirected() ? reached[u] && !reached[v] : reached[u] != reached[v];
            if (crosses) {
                paint.edge(edge.id(), State.FAIL);
                result.cutEdge(edge.id());
                cutCapacity += edge.weight();
                cutNames.add(graph.nodeName(edge.from()) + (graph.isDirected() ? "→" : "-") + graph.nodeName(edge.to()));
            }
            double net = st.flow[2 * e];
            result.flow(edge.id(), net);
            if (Math.abs(net) > EPS) {
                result.treeEdge(edge.id());
            }
        }
        for (int i = 0; i < n; i++) {
            if (reached[i]) {
                paint.node(graph.nodes().get(i).id(), State.VISITED);
            }
        }
        rec.step(paint.build(), 8, "The end cannot be reached any more, so the maximum flow is " + Labels.number(total) + ". The dashed edges ("
                + String.join(", ", cutNames) + ") form the minimum cut: their capacities add up to " + Labels.number(cutCapacity));
        return new Trace(ID, params, rec.steps(), result.build());
    }

    /** Depth-first search for one route in the level graph; fills {@code route} from the end back to the start. */
    private static double find(State0 st, int u, double limit, List<Integer> route) {
        if (u == st.t) {
            return limit;
        }
        List<Integer> arcs = st.out.get(u);
        for (; st.next[u] < arcs.size(); st.next[u]++) {
            int arc = arcs.get(st.next[u]);
            int v = st.head[arc];
            double spare = st.cap[arc] - st.flow[arc];
            if (spare > EPS && st.level[v] == st.level[u] + 1) {
                double got = find(st, v, Math.min(limit, spare), route);
                if (got > EPS) {
                    route.add(arc);
                    return got;
                }
            }
        }
        return 0;
    }

    private static void paintFlow(Graph graph, GraphFrame.Builder paint, State0 st) {
        paint.recolourEdges(State.CHOSEN, State.IDLE).recolourEdges(State.PATH, State.IDLE)
                .recolourEdges(State.VISITED, State.IDLE).recolourEdges(State.FAIL, State.IDLE);
        paint.recolourNodes(State.CHOSEN, State.IDLE).recolourNodes(State.VISITED, State.IDLE).recolourNodes(State.FRONTIER, State.IDLE);
        for (int e = 0; e < graph.edgeCount(); e++) {
            Edge edge = graph.edges().get(e);
            double used = Math.abs(st.flow[2 * e]);
            double capacity = st.cap[2 * e];
            State state = used <= EPS ? State.IDLE : used >= capacity - EPS ? State.VISITED : State.PATH;
            paint.edge(edge.id(), state);
            paint.edgeLabel(edge.id(), Labels.number(used) + "/" + Labels.number(capacity));
        }
    }
}

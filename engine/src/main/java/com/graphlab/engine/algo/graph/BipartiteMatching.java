package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Edge;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maximum bipartite matching with augmenting paths (Kuhn's method, MS18). The graph must be undirected and two-colourable; the
 * colouring (breadth-first search, the first node of each part is "L") splits the nodes into a left and a right side. For every
 * left node in turn, a depth-first search looks for an alternating path: a free right node, or a right node whose partner can move
 * to another one. Edge weights are ignored.
 *
 * <p>Result: {@link Result#value()} is the matching size and {@link Result#treeEdgeIds()} the matched edges (in edge order).
 */
public final class BipartiteMatching implements Algorithm {
    public static final String ID = "bipartite-matching";
    private static final int MAX_STEPS = 4000;

    private static final List<String> PSEUDOCODE = List.of(
            "colour the nodes with two colours so every edge joins different colours (left and right)",
            "no node is matched yet",
            "for each left node u: try(u) with every right node unvisited",
            "try(u): for each edge (u, v) with v not yet visited in this search",
            "    mark v visited",
            "    if v is free, or try(partner of v) succeeds: match u with v and succeed",
            "fail if no edge works",
            "the matching size is the number of successful searches");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Bipartite matching";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public Graph exampleGraph() {
        return com.graphlab.engine.model.SampleGraph.sixNodeBipartite();
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.NETWORK_FLOW;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    private final class Run {
        final Graph graph;
        final GraphFrame.Builder paint;
        final TraceRecorder rec = new TraceRecorder().counter("matched", 0).counter("searches", 0)
                .limit(MAX_STEPS, "This graph needs too many steps to show. Try a smaller one.");
        final Map<Integer, Integer> side = new HashMap<>();
        final Map<Integer, Integer> matchEdge = new HashMap<>();   // node id to the id of its matching edge
        Map<Integer, Boolean> seen = new HashMap<>();

        Run(Graph graph) {
            this.graph = graph;
            this.paint = GraphFrame.builder(graph);
        }

        int other(Edge e, int u) {
            return e.from() == u ? e.to() : e.from();
        }

        boolean tryNode(int u) {
            for (Arc arc : graph.arcsFrom(u)) {
                int v = arc.to();
                if (side.get(v).equals(side.get(u)) || seen.getOrDefault(v, false)) {
                    continue;
                }
                seen.put(v, true);
                int edgeId = arc.edge().id();
                paint.edge(edgeId, State.CHOSEN);
                paint.node(v, State.FRONTIER);
                Integer partnerEdge = matchEdge.get(v);
                if (partnerEdge == null) {
                    rec.step(paint.build(), 5, "Try " + graph.nodeName(u) + " with " + graph.nodeName(v) + ": " + graph.nodeName(v)
                            + " is free, so match them");
                    match(u, v, edgeId);
                    return true;
                }
                int partner = other(graph.edge(partnerEdge), v);
                rec.step(paint.build(), 5, "Try " + graph.nodeName(u) + " with " + graph.nodeName(v) + ": " + graph.nodeName(v)
                        + " is matched with " + graph.nodeName(partner) + ", so ask " + graph.nodeName(partner)
                        + " to move to another node");
                if (tryNode(partner)) {
                    match(u, v, edgeId);
                    return true;
                }
                paint.edge(edgeId, State.IDLE);
                paint.node(v, State.IDLE);
                rec.step(paint.build(), 6, graph.nodeName(partner) + " cannot move, so " + graph.nodeName(u) + " cannot use "
                        + graph.nodeName(v) + " now");
            }
            return false;
        }

        void match(int u, int v, int edgeId) {
            Integer oldU = matchEdge.get(u);
            Integer oldV = matchEdge.get(v);
            if (oldU != null) {
                paint.edge(oldU, State.IDLE);
            }
            if (oldV != null) {
                paint.edge(oldV, State.IDLE);
            }
            matchEdge.put(u, edgeId);
            matchEdge.put(v, edgeId);
            paint.edge(edgeId, State.PATH);
            paint.node(u, State.VISITED).node(v, State.VISITED);
        }
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireUndirected(graph, name());
        Run run = new Run(graph);
        for (Edge e : graph.edges()) {
            if (e.from() == e.to()) {
                throw new IllegalArgumentException(name() + " needs a graph with no loops: " + graph.nodeName(e.from())
                        + " has an edge to itself.");
            }
        }
        // Two-colour every component with breadth-first search.
        for (Node start : graph.nodes()) {
            if (run.side.containsKey(start.id())) {
                continue;
            }
            run.side.put(start.id(), 0);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start.id());
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (Arc arc : graph.arcsFrom(u)) {
                    int v = arc.to();
                    if (!run.side.containsKey(v)) {
                        run.side.put(v, 1 - run.side.get(u));
                        queue.add(v);
                    } else if (run.side.get(v).equals(run.side.get(u))) {
                        throw new IllegalArgumentException(name() + " needs a bipartite graph, but the edge "
                                + graph.arcName(arc) + " joins two nodes that must be on the same side (an odd cycle).");
                    }
                }
            }
        }
        for (Node node : graph.nodes()) {
            run.paint.label(node.id(), run.side.get(node.id()) == 0 ? "L" : "R");
        }
        run.rec.step(run.paint.build(), 1, "Split the nodes into a left side (L) and a right side (R): every edge joins an L and an R node");
        run.rec.step(run.paint.build(), 2, "Nothing is matched yet");

        int matched = 0;
        int searches = 0;
        for (Node node : graph.nodes()) {
            int u = node.id();
            if (run.side.get(u) != 0) {
                continue;
            }
            run.seen = new HashMap<>();
            searches++;
            run.rec.set("searches", searches);
            run.paint.node(u, State.CHOSEN);
            run.rec.step(run.paint.build(), 3, "Look for a partner for " + graph.nodeName(u));
            if (run.tryNode(u)) {
                matched++;
                run.rec.set("matched", matched);
                run.rec.step(run.paint.build(), 6, graph.nodeName(u) + " is matched. The matching has " + matched + " edges");
            } else {
                run.paint.node(u, State.FAIL);
                run.rec.step(run.paint.build(), 7, "No partner can be found for " + graph.nodeName(u));
            }
            // clear search colours, keep the matching
            run.paint.recolourEdges(State.CHOSEN, State.IDLE);
            run.paint.recolourNodes(State.FRONTIER, State.IDLE);
        }
        Result.Builder result = Result.builder().value(matched);
        List<Integer> matchedEdges = new ArrayList<>();
        for (Edge e : graph.edges()) {
            if (run.matchEdge.get(e.from()) != null && run.matchEdge.get(e.from()) == e.id()
                    && run.matchEdge.get(e.to()) != null && run.matchEdge.get(e.to()) == e.id()) {
                matchedEdges.add(e.id());
                result.treeEdge(e.id());
            }
        }
        run.rec.step(run.paint.build(), 8, "Done: the largest matching has " + matched + (matched == 1 ? " edge" : " edges"));
        return new Trace(ID, params, run.rec.steps(), result.build());
    }
}

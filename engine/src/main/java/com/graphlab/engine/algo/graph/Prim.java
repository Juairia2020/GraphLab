package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Graph;
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
 * Prim's minimum spanning tree grown from one node (undirected graphs only). Nodes in the tree
 * are VISITED; nodes with a waiting edge are FRONTIER and labelled with the cheapest edge weight
 * that reaches them. Waiting edges are FRONTIER, the edge being taken is CHOSEN, accepted edges
 * are PATH, and a waiting edge whose far end joined the tree meanwhile is skipped as FAIL. Ties go
 * to the lower edge id. Only the start node's component is spanned.
 */
public final class Prim implements Algorithm {
    public static final String ID = "prim";

    private static final List<String> PSEUDOCODE = List.of(
            "put the start node in the tree, queue its edges",
            "while the queue is not empty and the tree is not complete",
            "    take the cheapest edge (u, v, w) from the queue",
            "    if v is already in the tree: skip the edge",
            "    else add v and the edge (u, v) to the tree",
            "    queue every edge from v to a node outside the tree");

    private record Candidate(double weight, int edgeId, int from, int to) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Prim";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireSource(graph, params, name());
        AlgoSupport.requireUndirected(graph, name());
        int source = params.source();

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("inTree", 1).counter("queued", 0);
        Result.Builder result = Result.builder();

        Set<Integer> inTree = new HashSet<>();
        Map<Integer, Double> key = new HashMap<>();
        PriorityQueue<Candidate> queue = new PriorityQueue<>(
                Comparator.comparingDouble(Candidate::weight).thenComparingInt(Candidate::edgeId));

        inTree.add(source);
        result.visit(source);
        paint.node(source, State.VISITED).label(source, "0");
        int queuedNow = queueEdgesFrom(graph, source, inTree, key, queue, paint);
        rec.set("queued", queue.size());
        rec.step(paint.build(), 1, "Start the tree at " + graph.nodeName(source) + " and queue its "
                + queuedNow + (queuedNow == 1 ? " edge" : " edges"));

        double total = 0;
        while (!queue.isEmpty() && inTree.size() < graph.nodeCount()) {
            Candidate c = queue.poll();
            rec.set("queued", queue.size());
            String name = graph.nodeName(c.from()) + "-" + graph.nodeName(c.to());
            paint.edge(c.edgeId(), State.CHOSEN);
            rec.step(paint.build(), 3, "The cheapest waiting edge is " + name + " (weight "
                    + Labels.number(c.weight()) + ")");

            if (inTree.contains(c.to())) {
                paint.edge(c.edgeId(), State.FAIL);
                rec.step(paint.build(), 4, graph.nodeName(c.to())
                        + " is already in the tree, skip the edge");
                continue;
            }

            inTree.add(c.to());
            total += c.weight();
            result.visit(c.to()).parent(c.to(), c.edgeId()).treeEdge(c.edgeId());
            paint.node(c.to(), State.VISITED).edge(c.edgeId(), State.PATH)
                    .label(c.to(), Labels.number(c.weight()));
            rec.set("inTree", inTree.size());
            rec.step(paint.build(), 5, "Add " + graph.nodeName(c.to()) + " to the tree via " + name
                    + ". Total weight so far " + Labels.number(total));

            int added = queueEdgesFrom(graph, c.to(), inTree, key, queue, paint);
            rec.set("queued", queue.size());
            rec.step(paint.build(), 6, added == 0
                    ? graph.nodeName(c.to()) + " has no edges to nodes outside the tree"
                    : "Queue " + added + (added == 1 ? " edge" : " edges") + " from "
                            + graph.nodeName(c.to()));
        }

        paint.recolourEdges(State.FRONTIER, State.IDLE);
        result.totalWeight(total);
        rec.step(paint.build(), 2, inTree.size() == graph.nodeCount()
                ? "Done: a minimum spanning tree, total weight " + Labels.number(total)
                : "Done: only " + inTree.size() + " of " + graph.nodeCount() + " nodes are reachable from "
                        + graph.nodeName(source) + ", total weight " + Labels.number(total));
        return new Trace(ID, params, rec.steps(), result.build());
    }

    /** Queues the edges from {@code node} to nodes outside the tree; returns how many. */
    private static int queueEdgesFrom(Graph graph, int node, Set<Integer> inTree,
            Map<Integer, Double> key, PriorityQueue<Candidate> queue, GraphFrame.Builder paint) {
        int count = 0;
        for (Arc arc : graph.arcsFrom(node)) {
            if (inTree.contains(arc.to())) {
                continue;
            }
            queue.add(new Candidate(arc.weight(), arc.edge().id(), arc.from(), arc.to()));
            paint.edge(arc.edge().id(), State.FRONTIER);
            Double best = key.get(arc.to());
            if (best == null || arc.weight() < best) {
                key.put(arc.to(), arc.weight());
                paint.node(arc.to(), State.FRONTIER).label(arc.to(), Labels.number(arc.weight()));
            }
            count++;
        }
        return count;
    }
}

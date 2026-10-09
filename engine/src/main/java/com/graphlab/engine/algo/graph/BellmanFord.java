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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bellman-Ford shortest paths from one node. Handles negative weights and detects a negative
 * cycle reachable from the start. Labels show the best distance known so far. Node states:
 * IDLE = not reached, FRONTIER = reached, CHOSEN = just improved, VISITED = final. The current
 * best parent edge of each node is shown as PATH.
 *
 * <p>Every edge is tried in edge order in each pass (an undirected edge is tried both ways), and a
 * pass that changes nothing ends the search early. A negative cycle ends in a FAIL edge and an
 * empty result: distances do not exist.
 */
public final class BellmanFord implements Algorithm {
    public static final String ID = "bellman-ford";

    private static final List<String> PSEUDOCODE = List.of(
            "dist[all] = infinity, dist[start] = 0",
            "repeat up to |V| - 1 times (one pass)",
            "    for each edge (u, v, w) with dist[u] known",
            "        if dist[u] + w is not less than dist[v]: no change",
            "        else dist[v] = dist[u] + w, parent[v] = u",
            "    if nothing changed in this pass: stop early",
            "for each edge (u, v, w): is there still an improvement?",
            "    if yes: a negative cycle exists. If no: the distances are final");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Bellman-Ford";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireSource(graph, params, name());
        int source = params.source();
        int n = graph.nodeCount();

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("pass", 0).counter("relaxed", 0);

        Map<Integer, Double> dist = new HashMap<>();
        for (Node node : graph.nodes()) {
            dist.put(node.id(), Double.POSITIVE_INFINITY);
            paint.label(node.id(), Labels.INFINITY);
        }
        dist.put(source, 0.0);
        paint.label(source, "0").node(source, State.FRONTIER);
        List<Integer> reachedOrder = new ArrayList<>();
        reachedOrder.add(source);
        Map<Integer, Integer> parentEdge = new HashMap<>();
        rec.step(paint.build(), 1, "Every distance starts at infinity, except "
                + graph.nodeName(source) + " which is 0");

        int relaxed = 0;
        for (int pass = 1; pass <= n - 1; pass++) {
            rec.set("pass", pass);
            rec.step(paint.build(), 2, "Pass " + pass + " of at most " + (n - 1));
            boolean changed = false;

            for (Arc arc : graph.arcs()) {
                int u = arc.from();
                int v = arc.to();
                double du = dist.get(u);
                if (Double.isInfinite(du)) {
                    continue;   // u has no distance yet, nothing to offer
                }
                int edgeId = arc.edge().id();
                double candidate = du + arc.weight();
                State before = paint.edgeState(edgeId);

                if (candidate < dist.get(v)) {
                    Integer old = parentEdge.get(v);
                    boolean firstTime = Double.isInfinite(dist.get(v));
                    dist.put(v, candidate);
                    parentEdge.put(v, edgeId);
                    if (firstTime) {
                        reachedOrder.add(v);
                    }
                    if (old != null && !parentEdge.containsValue(old)) {
                        paint.edge(old, State.IDLE);
                    }
                    changed = true;
                    relaxed++;
                    rec.set("relaxed", relaxed);
                    paint.edge(edgeId, State.PATH).label(v, Labels.number(candidate))
                            .node(v, State.CHOSEN);
                    rec.step(paint.build(), 5, "Relax " + graph.arcName(arc) + ": "
                            + Labels.number(du) + " + " + Labels.number(arc.weight()) + " = "
                            + Labels.number(candidate) + " is better, so " + graph.nodeName(v)
                            + " becomes " + Labels.number(candidate));
                    paint.node(v, State.FRONTIER);
                } else {
                    paint.edge(edgeId, State.CHOSEN);
                    rec.step(paint.build(), 4, graph.arcName(arc) + ": " + Labels.number(candidate)
                            + " is not less than " + Labels.number(dist.get(v)) + ", no change");
                    paint.edge(edgeId, before);
                }
            }

            if (!changed) {
                rec.step(paint.build(), 6, "Nothing changed in pass " + pass
                        + ", so the distances are final: stop early");
                break;
            }
        }

        rec.step(paint.build(), 7, "Check every edge once more for a possible improvement");
        Arc witness = null;
        for (Arc arc : graph.arcs()) {
            double du = dist.get(arc.from());
            if (!Double.isInfinite(du) && du + arc.weight() < dist.get(arc.to())) {
                witness = arc;
                break;
            }
        }

        Result.Builder result = Result.builder();
        for (int node : reachedOrder) {
            result.visit(node);
        }
        paint.recolourNodes(State.FRONTIER, State.VISITED).recolourNodes(State.CHOSEN, State.VISITED);

        if (witness != null) {
            paint.edge(witness.edge().id(), State.FAIL);
            rec.step(paint.build(), 8, graph.arcName(witness) + " can still be improved after "
                    + (n - 1) + " passes: there is a negative cycle");
            paint.recolourEdges(State.PATH, State.IDLE);
            result.negativeCycle(true);
            rec.step(paint.build(), 8, "A negative cycle is reachable from " + graph.nodeName(source)
                    + ", so shortest distances do not exist");
        } else {
            double total = 0;
            for (int node : reachedOrder) {
                Integer edgeId = parentEdge.get(node);
                if (edgeId != null) {
                    result.parent(node, edgeId).treeEdge(edgeId);
                    total += graph.edge(edgeId).weight();
                }
            }
            for (Node node : graph.nodes()) {
                result.distance(node.id(), dist.get(node.id()));
            }
            result.totalWeight(total);
            rec.step(paint.build(), 8, "No edge can be improved: there is no negative cycle and "
                    + reachedOrder.size() + " of " + n + " nodes are reached");
        }
        return new Trace(ID, params, rec.steps(), result.build());
    }
}

package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Breadth-first search from one node. Node states: FRONTIER while waiting in the queue, CHOSEN
 * while being processed, VISITED when done. Labels show the number of hops from the start.
 * Tree edges turn PATH.
 */
public final class Bfs implements Algorithm {
    public static final String ID = "bfs";

    private static final List<String> PSEUDOCODE = List.of(
            "mark the start node discovered, hops[start] = 0, enqueue it",
            "while the queue is not empty",
            "    u = dequeue",
            "    for each edge (u, v)",
            "        if v is already discovered: skip it",
            "        else mark v discovered, hops[v] = hops[u] + 1, enqueue v",
            "    mark u finished");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Breadth-first search";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireSource(graph, params, name());
        int source = params.source();

        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("queue", 0).counter("finished", 0);
        Result.Builder result = Result.builder();

        Map<Integer, Integer> hops = new HashMap<>();
        Map<Integer, Integer> parentEdge = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        hops.put(source, 0);
        queue.add(source);
        result.visit(source).distance(source, 0);
        paint.node(source, State.FRONTIER).label(source, "0");
        rec.set("queue", queue.size());
        rec.step(paint.build(), 1,
                "Start at " + graph.nodeName(source) + ": it is 0 hops from itself");

        int finished = 0;
        double total = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            paint.node(u, State.CHOSEN);
            rec.set("queue", queue.size());
            rec.step(paint.build(), 3, "Take " + graph.nodeName(u) + " from the queue");

            for (Arc arc : graph.arcsFrom(u)) {
                int edgeId = arc.edge().id();
                int v = arc.to();
                State before = paint.edgeState(edgeId);
                paint.edge(edgeId, State.CHOSEN);
                rec.step(paint.build(), 4, "Look at edge " + graph.arcName(arc));

                if (hops.containsKey(v)) {
                    rec.step(paint.build(), 5, graph.nodeName(v) + " is already discovered, skip it");
                    paint.edge(edgeId, before);
                } else {
                    int h = hops.get(u) + 1;
                    hops.put(v, h);
                    parentEdge.put(v, edgeId);
                    queue.add(v);
                    result.visit(v).distance(v, h).parent(v, edgeId).treeEdge(edgeId);
                    total += arc.weight();
                    paint.node(v, State.FRONTIER).label(v, Integer.toString(h)).edge(edgeId, State.PATH);
                    rec.set("queue", queue.size());
                    rec.step(paint.build(), 6, "Discover " + graph.nodeName(v) + " at " + h
                            + (h == 1 ? " hop" : " hops") + " and add it to the queue");
                }
            }

            paint.node(u, State.VISITED);
            finished++;
            rec.set("finished", finished);
            rec.step(paint.build(), 7, "Finished with " + graph.nodeName(u));
        }

        for (var node : graph.nodes()) {
            if (!hops.containsKey(node.id())) {
                result.distance(node.id(), Double.POSITIVE_INFINITY);
            }
        }
        result.totalWeight(total);
        rec.step(paint.build(), 2, "The queue is empty: reached " + hops.size() + " of "
                + graph.nodeCount() + " nodes");
        return new Trace(ID, params, rec.steps(), result.build());
    }
}

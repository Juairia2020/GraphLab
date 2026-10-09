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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Depth-first search from one node. Node states: CHOSEN for the node being explored right now,
 * FRONTIER for nodes further up the stack that are waiting for their children, VISITED when
 * finished. Labels show discovery time while open and "discovery/finish" once finished.
 * Tree edges turn PATH.
 *
 * <p>Recursive on purpose: graphs are capped at {@link Graph#MAX_NODES} nodes, so the depth is
 * at most 60.
 */
public final class Dfs implements Algorithm {
    public static final String ID = "dfs";

    private static final List<String> PSEUDOCODE = List.of(
            "time = 0",
            "visit(start)",
            "visit(u):",
            "    mark u discovered, discovery[u] = ++time",
            "    for each edge (u, v)",
            "        if v is not discovered: parent[v] = u, visit(v)",
            "    mark u finished, finish[u] = ++time");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Depth-first search";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireSource(graph, params, name());
        Run run = new Run(graph);
        run.rec.step(run.paint.build(), 1, "Start the clock at 0");
        run.rec.step(run.paint.build(), 2, "Begin the search at " + graph.nodeName(params.source()));
        run.visit(params.source());
        run.rec.step(run.paint.build(), 2, "The search is finished: reached " + run.discovery.size()
                + " of " + graph.nodeCount() + " nodes");
        run.result.totalWeight(run.total);
        return new Trace(ID, params, run.rec.steps(), run.result.build());
    }

    /** State of one run, so the recursive visit() can reach it. */
    private static final class Run {
        final Graph graph;
        final GraphFrame.Builder paint;
        final TraceRecorder rec = new TraceRecorder().counter("time", 0).counter("finished", 0);
        final Result.Builder result = Result.builder();
        final Map<Integer, Integer> discovery = new HashMap<>();
        int time;
        int finished;
        double total;

        Run(Graph graph) {
            this.graph = graph;
            this.paint = GraphFrame.builder(graph);
        }

        void visit(int u) {
            discovery.put(u, ++time);
            result.visit(u);
            paint.node(u, State.CHOSEN).label(u, Integer.toString(time));
            rec.set("time", time);
            rec.step(paint.build(), 4, "Discover " + graph.nodeName(u) + " at time " + time);

            for (Arc arc : graph.arcsFrom(u)) {
                int edgeId = arc.edge().id();
                int v = arc.to();
                State before = paint.edgeState(edgeId);
                paint.edge(edgeId, State.CHOSEN);
                rec.step(paint.build(), 5, "Look at edge " + graph.arcName(arc));

                if (discovery.containsKey(v)) {
                    paint.edge(edgeId, before);
                    continue;
                }
                paint.edge(edgeId, State.PATH);
                result.parent(v, edgeId).treeEdge(edgeId);
                total += arc.weight();
                paint.node(u, State.FRONTIER);
                rec.step(paint.build(), 6, graph.nodeName(v) + " is new: go deeper along "
                        + graph.arcName(arc));
                visit(v);
                paint.node(u, State.CHOSEN);
            }

            ++time;
            finished++;
            result.finish(u);
            paint.node(u, State.VISITED).label(u, discovery.get(u) + "/" + time);
            rec.set("time", time);
            rec.set("finished", finished);
            rec.step(paint.build(), 7, "Finish " + graph.nodeName(u) + " at time " + time);
        }
    }
}

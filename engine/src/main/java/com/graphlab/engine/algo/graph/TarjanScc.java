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

/**
 * Tarjan's strongly connected components (MS18). One depth-first search gives every node an index (discovery order) and a
 * low-link (the smallest index it can reach through the search tree and one back edge to a node still on the stack). A node whose
 * low-link equals its own index is the root of a component: everything above it on the stack leaves with it.
 *
 * <p>Works on directed graphs. Labels show "index/low". Result: {@link Result#value()} is the number of components,
 * {@link Result#colours()} gives each node its component number (1, 2, ... in the order the components were found; the first one found
 * is a sink of the condensation), {@link Result#visitOrder()} the discovery order.
 */
public final class TarjanScc implements Algorithm {
    public static final String ID = "tarjan-scc";
    private static final int MAX_STEPS = 4000;

    private static final List<String> PSEUDOCODE = List.of(
            "index = 0; the stack is empty",
            "for each node v that has no index: visit(v)",
            "visit(v): give v the next index, low[v] = index[v], push v",
            "    for each edge (v, w)",
            "        if w has no index: visit(w), then low[v] = min(low[v], low[w])",
            "        else if w is on the stack: low[v] = min(low[v], index[w])",
            "    if low[v] = index[v]: pop the stack down to v; those nodes are one component");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Strongly connected components (Tarjan)";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public Graph exampleGraph() {
        return com.graphlab.engine.model.SampleGraph.sixNodeDirected();
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.GRAPH_SEARCH;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    private final class Run {
        final Graph graph;
        final GraphFrame.Builder paint;
        final TraceRecorder rec = new TraceRecorder().counter("components", 0).counter("stack", 0)
                .limit(MAX_STEPS, "This graph needs too many steps to show. Try a smaller one.");
        final Result.Builder result = Result.builder();
        final Map<Integer, Integer> index = new HashMap<>();
        final Map<Integer, Integer> low = new HashMap<>();
        final List<Integer> stack = new ArrayList<>();
        final Map<Integer, Boolean> onStack = new HashMap<>();
        int next;
        int components;

        Run(Graph graph) {
            this.graph = graph;
            this.paint = GraphFrame.builder(graph);
        }

        void label(int v) {
            paint.label(v, index.get(v) + "/" + low.get(v));
        }

        void stackPanel() {
            List<String> items = new ArrayList<>();
            for (int i = stack.size() - 1; i >= 0; i--) {
                items.add(graph.nodeName(stack.get(i)));
            }
            paint.panel("Stack (top first)", items);
            rec.set("stack", stack.size());
        }

        void visit(int v) {
            index.put(v, next);
            low.put(v, next);
            next++;
            stack.add(v);
            onStack.put(v, true);
            result.visit(v);
            paint.node(v, State.CHOSEN);
            label(v);
            stackPanel();
            rec.step(paint.build(), 3, "Visit " + graph.nodeName(v) + ": index " + index.get(v) + ", low-link " + low.get(v)
                    + ", pushed on the stack");
            for (Arc arc : graph.arcsFrom(v)) {
                int w = arc.to();
                int edgeId = arc.edge().id();
                if (!index.containsKey(w)) {
                    paint.edge(edgeId, State.CHOSEN);
                    rec.step(paint.build(), 5, "Edge " + graph.arcName(arc) + " leads to a new node: go down");
                    visit(w);
                    paint.node(v, State.CHOSEN);
                    paint.edge(edgeId, State.PATH);
                    int before = low.get(v);
                    low.put(v, Math.min(low.get(v), low.get(w)));
                    label(v);
                    rec.step(paint.build(), 5, "Back at " + graph.nodeName(v) + ": low-link " + before + (before == low.get(v)
                            ? " stays (the child reaches nothing lower)" : " becomes " + low.get(v) + " (the child reaches lower)"));
                } else if (onStack.get(w)) {
                    paint.edge(edgeId, State.FRONTIER);
                    int before = low.get(v);
                    low.put(v, Math.min(low.get(v), index.get(w)));
                    label(v);
                    rec.step(paint.build(), 6, "Edge " + graph.arcName(arc) + " goes back to " + graph.nodeName(w)
                            + ", which is on the stack: low-link " + (before == low.get(v) ? "stays " + before : "becomes " + low.get(v)));
                } else {
                    paint.edge(edgeId, State.FAIL);
                    rec.step(paint.build(), 6, "Edge " + graph.arcName(arc) + " leads to " + graph.nodeName(w)
                            + ", which is in a finished component: ignore it");
                }
            }
            if (low.get(v).equals(index.get(v))) {
                components++;
                List<String> names = new ArrayList<>();
                while (true) {
                    int w = stack.remove(stack.size() - 1);
                    onStack.put(w, false);
                    result.colour(w, components);
                    result.finish(w);
                    paint.node(w, State.VISITED).label(w, "C" + components);
                    names.add(graph.nodeName(w));
                    if (w == v) {
                        break;
                    }
                }
                rec.set("components", components);
                stackPanel();
                rec.step(paint.build(), 7, "low-link equals index at " + graph.nodeName(v) + ": component C" + components + " = {"
                        + String.join(", ", names) + "}");
            } else {
                paint.node(v, State.FRONTIER);
                rec.step(paint.build(), 7, graph.nodeName(v) + " is not a root (low-link " + low.get(v) + " < index " + index.get(v)
                        + "): it stays on the stack");
            }
        }
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        if (!graph.isDirected()) {
            throw new IllegalArgumentException(name() + " needs a directed graph.");
        }
        Run run = new Run(graph);
        run.rec.step(run.paint.build(), 1, "Find the groups of nodes that can all reach each other");
        for (Node node : graph.nodes()) {
            if (!run.index.containsKey(node.id())) {
                run.rec.step(run.paint.build(), 2, "Start a new search at " + graph.nodeName(node.id()));
                run.visit(node.id());
            }
        }
        run.result.value(run.components);
        run.rec.step(run.paint.build(), 2, "All nodes have an index: " + run.components + (run.components == 1
                ? " strongly connected component" : " strongly connected components"));
        return new Trace(ID, params, run.rec.steps(), run.result.build());
    }
}

package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Graph colouring by backtracking: give every node a colour so that no two neighbours share one,
 * using as few colours as possible. It tries 2, 3, 4 ... colours in turn. For each number of
 * colours it colours the nodes one at a time (the best-connected first), and when a node has no
 * colour left it goes back and changes an earlier choice.
 *
 * <p>To avoid trying colourings that are the same up to renaming, a node may only use a colour
 * that is already in use or the next new one. Nodes show their colour number above them: CHOSEN is
 * the node being coloured, VISITED has a colour, FAIL are the neighbours that block a node.
 * Undirected graphs only.
 */
public final class GraphColouring implements Algorithm {
    public static final String ID = "graph-colouring";
    private static final int MAX_STEPS = 5000;

    private static final List<String> PSEUDOCODE = List.of(
            "sort the nodes by number of neighbours, most first",
            "for k = 2, 3, 4 ... colours  (1 if there are no edges)",
            "    colour the nodes one at a time, in that order",
            "    give a node the smallest colour 1..k that none of its neighbours has",
            "    if no colour fits: go back to the previous node and give it its next colour",
            "    if every node is coloured: stop, k colours are enough",
            "    if we went back past the first node: k colours are not enough, try k + 1");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Graph colouring";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.BACKTRACKING;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireUndirected(graph, name());
        return new Search(graph, params).go();
    }

    /** One run: the shared state of the backtracking search. */
    private static final class Search {
        private final Graph graph;
        private final Params params;
        private final int n;
        private final List<List<Integer>> neighbours = new ArrayList<>();
        private final int[] order;
        private final int[] colour;
        private final GraphFrame.Builder paint;
        private final TraceRecorder rec;
        private int k;
        private long assignments;
        private long backtracks;

        Search(Graph graph, Params params) {
            this.graph = graph;
            this.params = params;
            this.n = graph.nodeCount();
            Map<Integer, Integer> index = new HashMap<>();
            for (int i = 0; i < n; i++) {
                index.put(graph.nodes().get(i).id(), i);
                neighbours.add(new ArrayList<>());
            }
            for (Edge edge : graph.edges()) {
                int u = index.get(edge.from());
                int v = index.get(edge.to());
                neighbours.get(u).add(v);
                neighbours.get(v).add(u);
            }
            List<Integer> sorted = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sorted.add(i);
            }
            sorted.sort(Comparator.comparingInt((Integer i) -> -neighbours.get(i).size()));   // stable: ties keep node order
            this.order = sorted.stream().mapToInt(Integer::intValue).toArray();
            this.colour = new int[n];
            this.paint = GraphFrame.builder(graph);
            this.rec = new TraceRecorder().counter("colours tried", 0).counter("assignments", 0).counter("backtracks", 0)
                    .limit(MAX_STEPS, "Backtracking needs too many steps on this graph to show them all. Try a smaller or sparser graph.");
        }

        Trace go() {
            int edges = graph.edgeCount();
            repaint(-1, List.of());
            StringBuilder ranking = new StringBuilder();
            for (int pos = 0; pos < Math.min(n, 6); pos++) {
                ranking.append(pos == 0 ? "" : ", ").append(graph.nodes().get(order[pos]).label())
                        .append('(').append(neighbours.get(order[pos]).size()).append(')');
            }
            rec.step(paint.build(), 1, "Order the nodes by number of neighbours, most first: " + ranking
                    + (n > 6 ? ", ..." : "") + ". Then try to colour with as few colours as possible.");
            for (k = edges == 0 ? 1 : 2; ; k++) {
                java.util.Arrays.fill(colour, 0);
                rec.set("colours tried", k);
                repaint(-1, List.of());
                rec.step(paint.build(), 2, "Try to colour the graph with " + k + (k == 1 ? " colour" : " colours"));
                if (place(0, 0)) {
                    break;
                }
                repaint(-1, List.of());
                rec.step(paint.build(), 7, k + (k == 1 ? " colour is" : " colours are") + " not enough: every way of colouring ran into a clash. Try "
                        + (k + 1) + ".");
            }
            Result.Builder result = Result.builder().value(k);
            for (int i = 0; i < n; i++) {
                result.colour(graph.nodes().get(i).id(), colour[i]);
            }
            repaint(-1, List.of());
            paint.recolourNodes(State.IDLE, State.VISITED);
            rec.step(paint.build(), 6, "Done: " + k + (k == 1 ? " colour is" : " colours are") + " enough, and no two neighbours share a colour. "
                    + "This is the fewest possible (the chromatic number is " + k + ").");
            return new Trace(ID, params, rec.steps(), result.build());
        }

        /** Colours the node at {@code pos} in {@link #order} and the ones after it. Returns false if that cannot be done. */
        private boolean place(int pos, int usedMax) {
            if (pos == n) {
                return true;
            }
            int node = order[pos];
            boolean retry = false;
            for (int c = 1; c <= Math.min(k, usedMax + 1); c++) {
                List<Integer> blockers = new ArrayList<>();
                for (int nb : neighbours.get(node)) {
                    if (colour[nb] == c) {
                        blockers.add(nb);
                    }
                }
                if (!blockers.isEmpty()) {
                    continue;
                }
                colour[node] = c;
                assignments++;
                rec.set("assignments", assignments);
                repaint(node, List.of());
                String label = graph.nodes().get(node).label();
                rec.step(paint.build(), 4, (retry ? "Try the next colour: " : "") + "give " + label + " colour " + c
                        + (c > usedMax ? " (a new colour)" : " (no neighbour has it)"));
                if (place(pos + 1, Math.max(usedMax, c))) {
                    return true;
                }
                colour[node] = 0;
                retry = true;
            }
            // No colour from 1..k fits this node.
            List<Integer> blockers = new ArrayList<>();
            for (int nb : neighbours.get(node)) {
                if (colour[nb] != 0) {
                    blockers.add(nb);
                }
            }
            backtracks++;
            rec.set("backtracks", backtracks);
            repaint(node, blockers);
            String label = graph.nodes().get(node).label();
            if (pos == 0) {
                rec.step(paint.build(), 7, "No colour left for " + label + ", and there is no earlier node to change");
            } else {
                rec.step(paint.build(), 5, "No colour from 1 to " + k + " fits " + label + " (its neighbours use them all). Go back and change "
                        + graph.nodes().get(order[pos - 1]).label());
            }
            return false;
        }

        /** Colours as numbers above the nodes, coloured nodes VISITED, {@code current} CHOSEN, {@code blockers} FAIL. */
        private void repaint(int current, List<Integer> blockers) {
            paint.recolourNodes(State.VISITED, State.IDLE).recolourNodes(State.CHOSEN, State.IDLE)
                    .recolourNodes(State.FAIL, State.IDLE);
            paint.clearLabels();
            for (int i = 0; i < n; i++) {
                if (colour[i] != 0) {
                    paint.node(graph.nodes().get(i).id(), State.VISITED);
                    paint.label(graph.nodes().get(i).id(), Integer.toString(colour[i]));
                }
            }
            for (int b : blockers) {
                paint.node(graph.nodes().get(b).id(), State.FAIL);
            }
            if (current >= 0) {
                paint.node(graph.nodes().get(current).id(), State.CHOSEN);
            }
        }
    }
}

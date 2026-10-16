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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Karger's randomized minimum cut. Pick a random edge, merge its two ends into one node, and
 * repeat until two nodes are left: the edges between them are a cut. One run finds a minimum cut
 * with a fair chance (at least 2 / (n * (n - 1))), so the real algorithm repeats it many times
 * and keeps the smallest. The random choices come from the run's seed, so a run can be repeated.
 *
 * <p>Edges are counted, not weighted, and each edge has weight 1 here. The picture keeps all the
 * original nodes; nodes that have been merged show the same name above them (like "A+C") and the
 * same state. Merged nodes: CHOSEN the node just formed, VISITED the older merged ones. Edges:
 * VISITED are inside a merged node (gone), CHOSEN the one just contracted, FAIL (dashed) the final
 * cut. Undirected graphs only.
 */
public final class Karger implements Algorithm {
    public static final String ID = "karger";

    private static final List<String> PSEUDOCODE = List.of(
            "while there are more than 2 nodes",
            "    pick one of the edges between different nodes at random",
            "    merge its two ends into one node (edges between them disappear)",
            "the 2 nodes left stand for the 2 sides of a cut",
            "the cut is every edge between the two sides",
            "repeat the whole thing many times and keep the smallest cut");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Karger min cut";
    }

    @Override
    public boolean needsSource() {
        return false;
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.RANDOMIZED;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    public Trace run(Graph graph, Params params) {
        AlgoSupport.requireNonEmpty(graph, name());
        AlgoSupport.requireUndirected(graph, name());
        int n = graph.nodeCount();
        if (n < 2) {
            throw new IllegalArgumentException(name() + " needs at least 2 nodes to cut between");
        }
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(graph.nodes().get(i).id(), i);
        }
        int[] group = new int[n];
        for (int i = 0; i < n; i++) {
            group[i] = i;
        }
        int groups = n;
        Random random = new Random(params.seed());
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        TraceRecorder rec = new TraceRecorder().counter("contractions", 0).counter("nodes left", n);

        paint(graph, paint, group, index, -1, -1);
        rec.step(paint.build(), 1, "Start with " + n + " nodes. Karger picks a random edge and merges its two ends, again and again, until two nodes are left.");

        if (!connected(graph, index)) {
            return disconnected(graph, params, rec, paint, index);
        }

        int contractions = 0;
        while (groups > 2) {
            List<Integer> candidates = new ArrayList<>();
            for (int e = 0; e < graph.edgeCount(); e++) {
                Edge edge = graph.edges().get(e);
                if (group[index.get(edge.from())] != group[index.get(edge.to())]) {
                    candidates.add(e);
                }
            }
            int pickedEdge = candidates.get(random.nextInt(candidates.size()));
            Edge edge = graph.edges().get(pickedEdge);
            int keep = group[index.get(edge.from())];
            int drop = group[index.get(edge.to())];
            String before = groupName(graph, group, keep) + " and " + groupName(graph, group, drop);
            for (int i = 0; i < n; i++) {
                if (group[i] == drop) {
                    group[i] = keep;
                }
            }
            groups--;
            contractions++;
            rec.set("contractions", contractions);
            rec.set("nodes left", groups);
            paint(graph, paint, group, index, keep, edge.id());
            rec.step(paint.build(), 3, "Pick the edge " + graph.nodeName(edge.from()) + "-" + graph.nodeName(edge.to())
                    + " at random (" + candidates.size() + " edges still join different nodes). Merge " + before
                    + " into " + groupName(graph, group, keep) + ". " + groups + (groups == 1 ? " node is" : " nodes are") + " left");
        }

        // Two groups remain: the cut is every edge between them.
        int sideA = group[0];
        Result.Builder result = Result.builder();
        paint(graph, paint, group, index, -1, -1);
        int cut = 0;
        List<String> cutNames = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            if (group[index.get(edge.from())] != group[index.get(edge.to())]) {
                paint.edge(edge.id(), State.FAIL);
                result.cutEdge(edge.id()).treeEdge(edge.id());
                cutNames.add(graph.nodeName(edge.from()) + "-" + graph.nodeName(edge.to()));
                cut++;
            }
        }
        for (int i = 0; i < n; i++) {
            boolean a = group[i] == sideA;
            result.colour(graph.nodes().get(i).id(), a ? 0 : 1);
            paint.node(graph.nodes().get(i).id(), a ? State.VISITED : State.FRONTIER);
        }
        result.value(cut);
        rec.step(paint.build(), 5, "Two nodes are left. The edges between them (dashed) are a cut of size " + cut + ": " + String.join(", ", cutNames)
                + ". This run found one cut; another seed may find a smaller one.");
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static Trace disconnected(Graph graph, Params params, TraceRecorder rec, GraphFrame.Builder paint, Map<Integer, Integer> index) {
        // A graph in pieces can be split with no edges cut at all.
        boolean[] seen = new boolean[graph.nodeCount()];
        List<Integer> stack = new ArrayList<>();
        stack.add(0);
        seen[0] = true;
        while (!stack.isEmpty()) {
            int u = stack.remove(stack.size() - 1);
            for (Edge edge : graph.edges()) {
                int a = index.get(edge.from());
                int b = index.get(edge.to());
                if (a == u && !seen[b]) {
                    seen[b] = true;
                    stack.add(b);
                } else if (b == u && !seen[a]) {
                    seen[a] = true;
                    stack.add(a);
                }
            }
        }
        Result.Builder result = Result.builder().value(0);
        paint.clearLabels();
        for (int i = 0; i < graph.nodeCount(); i++) {
            int id = graph.nodes().get(i).id();
            result.colour(id, seen[i] ? 0 : 1);
            paint.node(id, seen[i] ? State.VISITED : State.FRONTIER);
        }
        rec.step(paint.build(), 4, "The graph is not connected: the pieces are already separate, so the minimum cut has 0 edges. "
                + "Karger's contractions only make sense on a connected graph.");
        return new Trace(ID, params, rec.steps(), result.build());
    }

    private static boolean connected(Graph graph, Map<Integer, Integer> index) {
        int n = graph.nodeCount();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        int components = n;
        for (Edge edge : graph.edges()) {
            int a = find(parent, index.get(edge.from()));
            int b = find(parent, index.get(edge.to()));
            if (a != b) {
                parent[a] = b;
                components--;
            }
        }
        return components == 1;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** Merged nodes share a name and a colour; {@code fresh} is the group just formed; {@code justEdge} the edge just contracted. */
    private static void paint(Graph graph, GraphFrame.Builder paint, int[] group, Map<Integer, Integer> index, int fresh, int justEdge) {
        int n = graph.nodeCount();
        paint.clearLabels();
        int[] size = new int[n];
        for (int g : group) {
            size[g]++;
        }
        for (int i = 0; i < n; i++) {
            int id = graph.nodes().get(i).id();
            if (size[group[i]] == 1) {
                paint.node(id, State.IDLE);
            } else {
                paint.node(id, group[i] == fresh ? State.CHOSEN : State.VISITED);
                paint.label(id, groupName(graph, group, group[i]));
            }
        }
        for (Edge edge : graph.edges()) {
            boolean inside = group[index.get(edge.from())] == group[index.get(edge.to())];
            paint.edge(edge.id(), edge.id() == justEdge ? State.CHOSEN : inside ? State.VISITED : State.IDLE);
        }
    }

    /** "A+C" for up to three members; "A+C+3 more" beyond that. */
    private static String groupName(Graph graph, int[] group, int g) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < group.length; i++) {
            if (group[i] == g) {
                names.add(graph.nodes().get(i).label());
            }
        }
        if (names.size() <= 3) {
            return String.join("+", names);
        }
        return names.get(0) + "+" + names.get(1) + "+" + (names.size() - 2) + " more";
    }
}

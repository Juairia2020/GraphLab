package com.graphlab.engine.experiment;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Many fast runs of Karger's minimum cut. {@code Karger.run} builds a picture for every step so it can be
 * replayed; an experiment of thousands of runs only needs each run's cut size, so this class repeats the same
 * contractions without any pictures. It makes the same random choices in the same order as {@code Karger.run},
 * so trial(seed) equals the cut that {@code Karger.run} finds for that seed (a test checks this).
 * Plain Java, no Android. One instance can be used by one thread at a time.
 */
public final class KargerTrials implements Trials {
    private final int n;
    private final int[] from;
    private final int[] to;
    private final boolean connected;

    /**
     * @throws IllegalArgumentException for an empty graph, a directed graph or fewer than 2 nodes,
     *                                  the same inputs {@code Karger.run} refuses
     */
    public KargerTrials(Graph graph) {
        if (graph.nodeCount() == 0) {
            throw new IllegalArgumentException("Karger min cut needs a graph with at least one node");
        }
        if (graph.isDirected()) {
            throw new IllegalArgumentException("Karger min cut works on undirected graphs only");
        }
        if (graph.nodeCount() < 2) {
            throw new IllegalArgumentException("Karger min cut needs at least 2 nodes to cut between");
        }
        n = graph.nodeCount();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(graph.nodes().get(i).id(), i);
        }
        int m = graph.edgeCount();
        from = new int[m];
        to = new int[m];
        for (int e = 0; e < m; e++) {
            Edge edge = graph.edges().get(e);
            from[e] = index.get(edge.from());
            to[e] = index.get(edge.to());
        }
        connected = isConnected();
    }

    public boolean isGraphConnected() {
        return connected;
    }

    /** One run with its own random seed; returns the size of the cut it found (0 for a disconnected graph). */
    @Override
    public int trial(long seed) {
        if (!connected) {
            return 0;
        }
        int[] group = new int[n];
        for (int i = 0; i < n; i++) {
            group[i] = i;
        }
        int groups = n;
        Random random = new Random(seed);
        List<Integer> candidates = new ArrayList<>(from.length);
        while (groups > 2) {
            candidates.clear();
            for (int e = 0; e < from.length; e++) {
                if (group[from[e]] != group[to[e]]) {
                    candidates.add(e);
                }
            }
            int picked = candidates.get(random.nextInt(candidates.size()));
            int keep = group[from[picked]];
            int drop = group[to[picked]];
            for (int i = 0; i < n; i++) {
                if (group[i] == drop) {
                    group[i] = keep;
                }
            }
            groups--;
        }
        int cut = 0;
        for (int e = 0; e < from.length; e++) {
            if (group[from[e]] != group[to[e]]) {
                cut++;
            }
        }
        return cut;
    }

    /** The seed for trial number {@code index} (0-based) of an experiment that started from {@code baseSeed}. */
    public static long seedFor(long baseSeed, int index) {
        return baseSeed + index;
    }

    private boolean isConnected() {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        int components = n;
        for (int e = 0; e < from.length; e++) {
            int a = find(parent, from[e]);
            int b = find(parent, to[e]);
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
}

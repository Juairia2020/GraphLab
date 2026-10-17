package com.graphlab.engine.experiment;

import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphLayouts;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The seeded random graph of the Experiment screen (MS10 S3): how many nodes, how dense, and which weights. The same
 * numbers and seed always give the same graph, so an experiment can be repeated. The graph is undirected, connected
 * (a random spanning tree is laid down first), has no self-loops and no repeated pair, and its nodes sit on a circle.
 * Plain Java, no Android.
 */
public final class GraphGenerator {
    public static final int MIN_NODES = 2;
    public static final int MAX_NODES = Graph.MAX_NODES;
    public static final int MAX_WEIGHT = 1000;

    private GraphGenerator() {
    }

    /** How many edges a graph with these numbers has: at least the {@code n - 1} of a tree, at most every pair. */
    public static int edgeCount(int nodes, int densityPercent) {
        int possible = nodes * (nodes - 1) / 2;
        int wanted = (int) Math.round(possible * densityPercent / 100.0);
        return Math.max(nodes - 1, Math.min(possible, wanted));
    }

    /**
     * @param nodes          {@value #MIN_NODES} to {@link Graph#MAX_NODES}
     * @param densityPercent 0 to 100: the share of all possible pairs that get an edge (never fewer than a tree needs)
     * @param minWeight      smallest weight, 1 or more
     * @param maxWeight      largest weight, at most {@value #MAX_WEIGHT}
     * @throws IllegalArgumentException with a plain message when a number is out of range
     */
    public static GraphDocument generate(int nodes, int densityPercent, int minWeight, int maxWeight, long seed) {
        if (nodes < MIN_NODES || nodes > MAX_NODES) {
            throw new IllegalArgumentException("Nodes must be between " + MIN_NODES + " and " + MAX_NODES + ".");
        }
        if (densityPercent < 0 || densityPercent > 100) {
            throw new IllegalArgumentException("Density must be between 0 and 100 percent.");
        }
        if (minWeight < 1) {
            throw new IllegalArgumentException("The smallest weight must be at least 1.");
        }
        if (maxWeight > MAX_WEIGHT) {
            throw new IllegalArgumentException("The largest weight must be at most " + MAX_WEIGHT + ".");
        }
        if (minWeight > maxWeight) {
            throw new IllegalArgumentException("The smallest weight must not be larger than the largest.");
        }
        Random random = new Random(seed);
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            list.add(new Node(i, 0, 0, i < 26 ? String.valueOf((char) ('A' + i)) : "N" + i));
        }
        List<int[]> pairs = new ArrayList<>();
        boolean[][] used = new boolean[nodes][nodes];
        for (int i = 1; i < nodes; i++) {                       // a random spanning tree keeps the graph connected
            int parent = random.nextInt(i);
            pairs.add(new int[] {parent, i});
            used[parent][i] = true;
        }
        List<int[]> spare = new ArrayList<>();
        for (int a = 0; a < nodes; a++) {
            for (int b = a + 1; b < nodes; b++) {
                if (!used[a][b]) {
                    spare.add(new int[] {a, b});
                }
            }
        }
        Collections.shuffle(spare, random);
        int extra = edgeCount(nodes, densityPercent) - pairs.size();
        for (int i = 0; i < extra; i++) {
            pairs.add(spare.get(i));
        }
        List<Edge> edges = new ArrayList<>();
        for (int e = 0; e < pairs.size(); e++) {
            int weight = minWeight + random.nextInt(maxWeight - minWeight + 1);
            edges.add(new Edge(e, pairs.get(e)[0], pairs.get(e)[1], weight));
        }
        Graph.Builder builder = Graph.builder(false);
        for (Node node : GraphLayouts.circle(list)) {
            builder.node(node.id(), node.x(), node.y(), node.label());
        }
        for (Edge edge : edges) {
            builder.edge(edge.id(), edge.from(), edge.to(), edge.weight());
        }
        return new GraphDocument("Random " + nodes + " nodes, seed " + seed, true, builder.build());
    }
}

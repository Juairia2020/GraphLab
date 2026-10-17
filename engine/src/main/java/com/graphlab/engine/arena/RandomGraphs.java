package com.graphlab.engine.arena;

import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.io.GraphLayouts;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Makes the random graphs of the Arena. Every graph is connected (a spanning tree is laid down first),
 * has no self-loops and no parallel edges, and has its edges in <em>canonical order</em>:
 * sorted by (from, to), with the smaller node first in an undirected graph. That order makes
 * every node list its neighbours alphabetically, so "visit neighbours in alphabetical order" is the
 * same rule as the engine's, and a player can follow the algorithm without guessing.
 *
 * <p>The same {@link Random} state always gives the same graph. Plain Java, no Android.
 */
final class RandomGraphs {
    private RandomGraphs() {
    }

    /**
     * @param extra       edges to add on top of the {@code n - 1} tree edges (fewer if the graph is already full)
     * @param directed    whether edges have a direction
     * @param forwardOnly directed only: every edge goes from a lower to a higher node id, so there is no cycle
     *                    and every node can be reached from node 0
     * @param minWeight   smallest weight (may be negative)
     * @param maxWeight   largest weight
     * @param distinct    every edge gets a different weight (needs {@code maxWeight - minWeight + 1} to be at
     *                    least the number of edges; otherwise weights repeat)
     */
    static Graph connected(Random random, int n, int extra, boolean directed, boolean forwardOnly,
            int minWeight, int maxWeight, boolean distinct) {
        List<int[]> pairs = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (int i = 1; i < n; i++) {
            addPair(pairs, used, random.nextInt(i), i, directed);   // from < to: also the canonical form
        }
        int attempts = 0;
        int added = 0;
        while (added < extra && attempts < 400) {
            attempts++;
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            if (a == b) {
                continue;
            }
            if (!directed || forwardOnly) {
                int low = Math.min(a, b);
                int high = Math.max(a, b);
                a = low;
                b = high;
            }
            if (addPair(pairs, used, a, b, directed)) {
                added++;
            }
        }
        pairs.sort(Comparator.<int[]>comparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
        double[] weights = weights(random, pairs.size(), minWeight, maxWeight, distinct);
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            edges.add(new Edge(i, pairs.get(i)[0], pairs.get(i)[1], weights[i]));
        }
        return new Graph(directed, nodes(n), edges);
    }

    /** The same graph with one more edge (the next free id, added last). */
    static Graph withEdge(Graph graph, int from, int to, double weight) {
        List<Edge> edges = new ArrayList<>(graph.edges());
        edges.add(new Edge(edges.size(), from, to, weight));
        return new Graph(graph.isDirected(), graph.nodes(), edges);
    }

    /** The same graph with every weight replaced by its size (no minus signs). */
    static Graph absolute(Graph graph) {
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            edges.add(new Edge(edge.id(), edge.from(), edge.to(), Math.abs(edge.weight())));
        }
        return new Graph(graph.isDirected(), graph.nodes(), edges);
    }

    private static boolean addPair(List<int[]> pairs, Set<Long> used, int from, int to, boolean directed) {
        int a = directed ? from : Math.min(from, to);
        int b = directed ? to : Math.max(from, to);
        if (!used.add((long) a * 1000 + b)) {
            return false;
        }
        pairs.add(new int[] {a, b});
        return true;
    }

    private static List<Node> nodes(int n) {
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Node(i, 0, 0, GraphEditor.labelFor(i)));
        }
        return GraphLayouts.circle(list);
    }

    private static double[] weights(Random random, int count, int min, int max, boolean distinct) {
        double[] out = new double[count];
        int span = max - min + 1;
        if (distinct && span >= count) {
            List<Integer> pool = new ArrayList<>();
            for (int w = min; w <= max; w++) {
                pool.add(w);
            }
            Collections.shuffle(pool, random);
            for (int i = 0; i < count; i++) {
                out[i] = pool.get(i);
            }
        } else {
            for (int i = 0; i < count; i++) {
                out[i] = min + random.nextInt(span);
            }
        }
        return out;
    }
}

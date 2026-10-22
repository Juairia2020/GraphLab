package com.graphlab.engine.arena;

import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Slow, simple solvers written independently of the engine's algorithms. The Arena tests compare the
 * right answer of every generated question with these, so a bug has to appear in both to go unnoticed.
 */
final class Reference {
    private Reference() {
    }

    static List<Integer> bfs(Graph g, int start) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>(Set.of(start));
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            int u = queue.poll();
            order.add(u);
            for (int v : neighboursAscending(g, u)) {
                if (seen.add(v)) {
                    queue.add(v);
                }
            }
        }
        return order;
    }

    /** discovery[0] is the discovery order, finish[0] the finishing order. */
    static void dfs(Graph g, int start, List<Integer> discovery, List<Integer> finish) {
        visit(g, start, new HashSet<>(), discovery, finish);
    }

    private static void visit(Graph g, int u, Set<Integer> seen, List<Integer> discovery, List<Integer> finish) {
        seen.add(u);
        discovery.add(u);
        for (int v : neighboursAscending(g, u)) {
            if (!seen.contains(v)) {
                visit(g, v, seen, discovery, finish);
            }
        }
        finish.add(u);
    }

    /** Neighbours by node id, ascending, ignoring the edge order the engine uses. */
    static List<Integer> neighboursAscending(Graph g, int u) {
        Set<Integer> set = new HashSet<>();
        for (Edge e : g.edges()) {
            if (e.from() == u) {
                set.add(e.to());
            } else if (!g.isDirected() && e.to() == u) {
                set.add(e.from());
            }
        }
        List<Integer> out = new ArrayList<>(set);
        out.sort(null);
        return out;
    }

    /** All-pairs shortest distances by Floyd-Warshall on a matrix (node id = index, ids are 0..n-1). */
    static double[][] allPairs(Graph g) {
        int n = g.nodeCount();
        double[][] d = new double[n][n];
        for (double[] row : d) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < n; i++) {
            d[i][i] = 0;
        }
        for (Edge e : g.edges()) {
            d[e.from()][e.to()] = Math.min(d[e.from()][e.to()], e.weight());
            if (!g.isDirected()) {
                d[e.to()][e.from()] = Math.min(d[e.to()][e.from()], e.weight());
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (d[i][k] + d[k][j] < d[i][j]) {
                        d[i][j] = d[i][k] + d[k][j];
                    }
                }
            }
        }
        return d;
    }

    /** True when a cycle with a negative total can be reached from {@code start} (Floyd diagonal check). */
    static boolean negativeCycleFrom(Graph g, int start) {
        double[][] d = allPairs(g);
        for (int v = 0; v < g.nodeCount(); v++) {
            if (d[v][v] < 0 && Double.isFinite(d[start][v])) {
                return true;
            }
        }
        return false;
    }

    /** Weight of a minimum spanning tree by Prim on an adjacency matrix. */
    static double mstWeight(Graph g) {
        int n = g.nodeCount();
        double[][] w = new double[n][n];
        for (double[] row : w) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (Edge e : g.edges()) {
            w[e.from()][e.to()] = Math.min(w[e.from()][e.to()], e.weight());
            w[e.to()][e.from()] = w[e.from()][e.to()];
        }
        boolean[] in = new boolean[n];
        in[0] = true;
        double total = 0;
        for (int step = 1; step < n; step++) {
            double best = Double.POSITIVE_INFINITY;
            int pick = -1;
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    if (in[u] && !in[v] && w[u][v] < best) {
                        best = w[u][v];
                        pick = v;
                    }
                }
            }
            in[pick] = true;
            total += best;
        }
        return total;
    }

    /** The edges Kruskal adds, by sorting and a union-find, in the order they are added. */
    static List<Edge> kruskalEdges(Graph g) {
        List<Edge> sorted = new ArrayList<>(g.edges());
        sorted.sort(Comparator.comparingDouble(Edge::weight).thenComparingInt(Edge::id));
        int[] parent = new int[g.nodeCount()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        List<Edge> chosen = new ArrayList<>();
        for (Edge e : sorted) {
            int a = find(parent, e.from());
            int b = find(parent, e.to());
            if (a != b) {
                parent[a] = b;
                chosen.add(e);
            }
        }
        return chosen;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** The edges Prim adds from {@code start}: each time the lightest edge from the tree to a new node (weights all different). */
    static List<Edge> primEdges(Graph g, int start) {
        Set<Integer> in = new HashSet<>(Set.of(start));
        List<Edge> chosen = new ArrayList<>();
        while (in.size() < g.nodeCount()) {
            Edge best = null;
            for (Edge e : g.edges()) {
                if (in.contains(e.from()) != in.contains(e.to()) && (best == null || e.weight() < best.weight())) {
                    best = e;
                }
            }
            chosen.add(best);
            in.add(in.contains(best.from()) ? best.to() : best.from());
        }
        return chosen;
    }

    /** Maximum flow by repeated depth-first search on a capacity matrix (Ford-Fulkerson; small integer capacities). */
    static double maxFlow(Graph g, int source, int sink) {
        int n = g.nodeCount();
        double[][] cap = new double[n][n];
        for (Edge e : g.edges()) {
            cap[e.from()][e.to()] += e.weight();
        }
        double total = 0;
        while (true) {
            double pushed = augment(cap, source, sink, Double.POSITIVE_INFINITY, new boolean[n]);
            if (pushed <= 0) {
                return total;
            }
            total += pushed;
        }
    }

    private static double augment(double[][] cap, int u, int sink, double limit, boolean[] seen) {
        if (u == sink) {
            return limit;
        }
        seen[u] = true;
        for (int v = 0; v < cap.length; v++) {
            if (!seen[v] && cap[u][v] > 0) {
                double pushed = augment(cap, v, sink, Math.min(limit, cap[u][v]), seen);
                if (pushed > 0) {
                    cap[u][v] -= pushed;
                    cap[v][u] += pushed;
                    return pushed;
                }
            }
        }
        return 0;
    }

    /** Smallest number of colours for a proper colouring, by trying every assignment. */
    static int chromaticNumber(Graph g) {
        int n = g.nodeCount();
        for (int k = 1; k <= n; k++) {
            if (colour(g, new int[n], 0, k)) {
                return k;
            }
        }
        return n;
    }

    private static boolean colour(Graph g, int[] colour, int node, int k) {
        if (node == colour.length) {
            return true;
        }
        for (int c = 1; c <= k; c++) {
            boolean clash = false;
            for (Edge e : g.edges()) {
                int other = e.from() == node ? e.to() : e.to() == node ? e.from() : -1;
                if (other >= 0 && other < node && colour[other] == c) {
                    clash = true;
                    break;
                }
            }
            if (!clash) {
                colour[node] = c;
                if (colour(g, colour, node + 1, k)) {
                    return true;
                }
                colour[node] = 0;
            }
        }
        return false;
    }

    /** 0/1 knapsack by the usual table. */
    static int knapsack01(KnapsackInput in) {
        int[] best = new int[in.capacity() + 1];
        for (Item item : in.items()) {
            for (int w = in.capacity(); w >= item.weight(); w--) {
                best[w] = Math.max(best[w], best[w - item.weight()] + item.value());
            }
        }
        return best[in.capacity()];
    }

    /** Fractional knapsack: best value per weight first. */
    static double knapsackFractional(KnapsackInput in) {
        List<Item> items = new ArrayList<>(in.items());
        items.sort(Comparator.comparingDouble((Item i) -> -(double) i.value() / i.weight()));
        double room = in.capacity();
        double total = 0;
        for (Item item : items) {
            double take = Math.min(room, item.weight());
            total += take / item.weight() * item.value();
            room -= take;
        }
        return total;
    }

    /** Comparisons of top-down merge sort that splits at (lo + hi) / 2 and compares the two fronts. */
    static int mergeComparisons(List<Integer> values) {
        int[] counter = new int[1];
        mergeSort(values.stream().mapToInt(Integer::intValue).toArray(), 0, values.size() - 1, counter);
        return counter[0];
    }

    private static void mergeSort(int[] a, int lo, int hi, int[] counter) {
        if (lo >= hi) {
            return;
        }
        int mid = (lo + hi) / 2;
        mergeSort(a, lo, mid, counter);
        mergeSort(a, mid + 1, hi, counter);
        int[] left = Arrays.copyOfRange(a, lo, mid + 1);
        int[] right = Arrays.copyOfRange(a, mid + 1, hi + 1);
        int i = 0;
        int j = 0;
        int k = lo;
        while (i < left.length && j < right.length) {
            counter[0]++;
            a[k++] = left[i] <= right[j] ? left[i++] : right[j++];
        }
        while (i < left.length) {
            a[k++] = left[i++];
        }
        while (j < right.length) {
            a[k++] = right[j++];
        }
    }

    /** Comparisons of quick sort with the last number as pivot (Lomuto): every other number of a part meets the pivot once. */
    static int quickComparisons(List<Integer> values) {
        int[] counter = new int[1];
        quickSort(values.stream().mapToInt(Integer::intValue).toArray(), 0, values.size() - 1, counter);
        return counter[0];
    }

    private static void quickSort(int[] a, int lo, int hi, int[] counter) {
        if (lo >= hi) {
            return;
        }
        int pivot = a[hi];
        int i = lo;
        for (int j = lo; j < hi; j++) {
            counter[0]++;
            if (a[j] < pivot) {
                int t = a[i];
                a[i] = a[j];
                a[j] = t;
                i++;
            }
        }
        int t = a[i];
        a[i] = a[hi];
        a[hi] = t;
        quickSort(a, lo, i - 1, counter);
        quickSort(a, i + 1, hi, counter);
    }

    static boolean hasArc(Graph g, int from, int to) {
        for (Arc arc : g.arcsFrom(from)) {
            if (arc.to() == to) {
                return true;
            }
        }
        return false;
    }
}

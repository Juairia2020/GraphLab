package com.graphlab.engine.geo;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* with the great-circle (haversine) distance as the heuristic. A road is never shorter than the straight line, so the
 * heuristic never overestimates and the answer is as short as Dijkstra's, usually with fewer places explored.
 * {@link #admissible} checks that on real data: an edge shorter than the straight line it joins would break the promise.
 */
public final class AStar {
    /** Rounding of edge weights to 0.1 km can be up to 0.05 km short. */
    private static final double TOLERANCE_KM = 0.06;

    public record Answer(List<Integer> path, double km, List<Integer> expanded, boolean reached) {
        public Answer {
            path = List.copyOf(path);
            expanded = List.copyOf(expanded);
        }
    }

    private AStar() {
    }

    /** True when no edge is shorter than the straight line between its two places. Node i is {@code places.get(i)}. */
    public static boolean admissible(Graph graph, List<City> places) {
        for (Edge e : graph.edges()) {
            if (e.weight() + TOLERANCE_KM < Geo.haversineKm(places.get(e.from()), places.get(e.to()))) {
                return false;
            }
        }
        return true;
    }

    public static Answer run(Graph graph, List<City> places, int from, int to) {
        int n = graph.nodeCount();
        double[] g = new double[n];
        java.util.Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -1);
        boolean[] closed = new boolean[n];
        g[from] = 0;
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        open.add(new double[] {Geo.haversineKm(places.get(from), places.get(to)), from});
        List<Integer> expanded = new ArrayList<>();
        while (!open.isEmpty()) {
            int u = (int) open.poll()[1];
            if (closed[u]) {
                continue;
            }
            closed[u] = true;
            expanded.add(u);
            if (u == to) {
                break;
            }
            for (Edge e : graph.edges()) {
                int v = e.from() == u ? e.to() : e.to() == u && !graph.isDirected() ? e.from() : -1;
                if (v < 0 || closed[v]) {
                    continue;
                }
                double cost = g[u] + e.weight();
                if (cost < g[v]) {
                    g[v] = cost;
                    parent[v] = u;
                    open.add(new double[] {cost + Geo.haversineKm(places.get(v), places.get(to)), v});
                }
            }
        }
        if (!closed[to]) {
            return new Answer(List.of(), 0, expanded, false);
        }
        List<Integer> path = new ArrayList<>();
        for (int v = to; v >= 0; v = parent[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        return new Answer(path, g[to], expanded, true);
    }
}

package com.graphlab.engine.geo;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Max flow and min cut on places the person picked on the map. The first place is the source, the last is the sink.
 * Each place is joined to its {@link #NEAREST} nearest places, and the cheapest spanning roads are added so the network is
 * always connected. Every link works in both directions and carries {@code CAPACITY_KM / km} units (rounded, at least 1):
 * a teaching model in which shorter links carry more. The numbers are not real road capacities.
 */
public final class MapFlow {
    public static final int NEAREST = 3;
    public static final double CAPACITY_KM = 1000.0;

    private MapFlow() {
    }

    public record Network(Graph graph, Trace trace, double value, List<Integer> cutEdgeIds, List<Integer> flowEdgeIds) {
        public Network {
            cutEdgeIds = List.copyOf(cutEdgeIds);
            flowEdgeIds = List.copyOf(flowEdgeIds);
        }
    }

    public static Network solve(RouteMatrix m) {
        int n = m.places().size();
        if (n < 3) {
            throw new IllegalArgumentException("A flow network needs at least 3 places");
        }
        Set<Long> links = new HashSet<>();
        for (int i = 0; i < n; i++) {
            List<Integer> order = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    order.add(j);
                }
            }
            final int from = i;
            order.sort((a, b) -> {
                int c = Double.compare(km(m, from, a), km(m, from, b));
                return c != 0 ? c : Integer.compare(a, b);
            });
            for (int k = 0; k < Math.min(NEAREST, order.size()); k++) {
                links.add(key(i, order.get(k)));
            }
        }
        // Kruskal over every pair keeps the network connected.
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs.add(new int[] {i, j});
            }
        }
        pairs.sort((a, b) -> {
            int c = Double.compare(km(m, a[0], a[1]), km(m, b[0], b[1]));
            if (c != 0) {
                return c;
            }
            c = Integer.compare(a[0], b[0]);
            return c != 0 ? c : Integer.compare(a[1], b[1]);
        });
        int[] root = new int[n];
        for (int i = 0; i < n; i++) {
            root[i] = i;
        }
        for (int[] p : pairs) {
            int a = find(root, p[0]);
            int b = find(root, p[1]);
            if (a != b) {
                root[a] = b;
                links.add(key(p[0], p[1]));
            }
        }
        double cLat = 0;
        double cLon = 0;
        for (City c : m.places()) {
            cLat += c.lat();
            cLon += c.lon();
        }
        cLat /= n;
        cLon /= n;
        List<Node> nodes = CityGraph.nodes(m.places(), cLat, cLon);   // spread, so close places do not overlap
        List<Long> sorted = new ArrayList<>(links);
        sorted.sort(Long::compare);
        List<Edge> edges = new ArrayList<>();
        int id = 0;
        for (long link : sorted) {
            int a = (int) (link / 1000);
            int b = (int) (link % 1000);
            double cap = Math.max(1, Math.round(CAPACITY_KM / Math.max(0.1, km(m, a, b))));
            edges.add(new Edge(id++, a, b, cap));
            edges.add(new Edge(id++, b, a, cap));
        }
        Graph g = new Graph(true, nodes, edges);
        Trace trace = Algorithms.byId("edmonds-karp").run(g, new Params(0, 0L, n - 1));
        Result r = trace.result();
        List<Integer> carrying = new ArrayList<>();
        for (Edge e : g.edges()) {
            Double f = r.edgeFlow().get(e.id());
            if (f != null && Math.abs(f) > 1e-9) {
                carrying.add(e.id());
            }
        }
        return new Network(g, trace, r.value(), r.cutEdgeIds(), carrying);
    }

    private static double km(RouteMatrix m, int i, int j) {
        return Math.min(m.between(i, j), m.between(j, i));
    }

    private static long key(int a, int b) {
        return Math.min(a, b) * 1000L + Math.max(a, b);
    }

    private static int find(int[] root, int x) {
        while (root[x] != x) {
            root[x] = root[root[x]];
            x = root[x];
        }
        return x;
    }
}

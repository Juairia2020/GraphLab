package com.graphlab.engine.geo;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * The complete weighted graph of a matrix. Node i is place i; edge weights are km, rounded to 0.1 so labels stay short.
 * An undirected graph uses the smaller of the two directions (a road matrix is almost symmetric). Positions come from the
 * equirectangular projection, so the graph opens in the Lab looking like the map.
 */
public final class CityGraph {
    /** The closest two places may be on the canvas (the editor's minimum is 50; this leaves room for a weight label). */
    public static final double MIN_GAP = 90.0;
    public static final int NEAREST = 3;

    private CityGraph() {
    }

    public static Graph build(RouteMatrix m) {
        List<City> places = m.places();
        double cLat = 0;
        double cLon = 0;
        for (City c : places) {
            cLat += c.lat();
            cLon += c.lon();
        }
        cLat /= places.size();
        cLon /= places.size();
        List<Node> nodes = nodes(places, cLat, cLon);
        List<Edge> edges = new ArrayList<>();
        int id = 0;
        for (int i = 0; i < places.size(); i++) {
            for (int j = i + 1; j < places.size(); j++) {
                double km = Math.min(m.between(i, j), m.between(j, i));
                edges.add(new Edge(id++, i, j, Math.max(0.1, Math.round(km * 10) / 10.0)));
            }
        }
        return new Graph(false, nodes, edges);
    }

    /** Places spread so that no two are closer than {@link #MIN_GAP} on the canvas. */
    static List<Node> nodes(List<City> places, double cLat, double cLon) {
        double[][] xy = new double[places.size()][];
        for (int i = 0; i < xy.length; i++) {
            xy[i] = Geo.project(places.get(i).lat(), places.get(i).lon(), cLat, cLon);
        }
        NodeSpread.spread(xy, MIN_GAP);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < xy.length; i++) {
            nodes.add(new Node(i, xy[i][0], xy[i][1], places.get(i).name()));
        }
        return nodes;
    }

    /**
     * A graph that is easy to read in the Lab: each place is joined to its {@link #NEAREST} nearest places, the cheapest
     * spanning roads keep it connected, and the links in {@code keep} (the answer: a route or a tour, as pairs of place
     * indexes) are always included. Because the answer's own links are present, the shortest route and the cheapest
     * network have the same length here as in the complete graph.
     */
    public static Graph readable(RouteMatrix m, List<int[]> keep) {
        int n = m.places().size();
        java.util.Set<Long> links = new java.util.TreeSet<>();
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
                links.add(link(i, order.get(k)));
            }
        }
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
                links.add(link(p[0], p[1]));
            }
        }
        for (int[] k : keep) {
            if (k[0] != k[1]) {
                links.add(link(k[0], k[1]));
            }
        }
        double cLat = 0;
        double cLon = 0;
        for (City c : m.places()) {
            cLat += c.lat();
            cLon += c.lon();
        }
        List<Edge> edges = new ArrayList<>();
        int id = 0;
        for (long l : links) {
            int a = (int) (l / 1000);
            int b = (int) (l % 1000);
            edges.add(new Edge(id++, a, b, Math.max(0.1, Math.round(km(m, a, b) * 10) / 10.0)));
        }
        return new Graph(false, nodes(m.places(), cLat / n, cLon / n), edges);
    }

    private static double km(RouteMatrix m, int i, int j) {
        return Math.min(m.between(i, j), m.between(j, i));
    }

    private static long link(int a, int b) {
        return Math.min(a, b) * 1000L + Math.max(a, b);
    }

    private static int find(int[] root, int x) {
        while (root[x] != x) {
            root[x] = root[root[x]];
            x = root[x];
        }
        return x;
    }

    /** Smallest distance between two nodes when a map graph is opened in the Lab (canvas units). */
    public static final double LAB_MIN_GAP = 70;

    /**
     * The same graph made readable for the Lab. Places that are close on the map are pushed apart ({@link NodeSpread}).
     * In an undirected graph a link is dropped when some other place gives a path that is no longer (A to C to B is not
     * longer than A to B). Such a link is never needed: every shortest distance and the cheapest network stay exactly
     * the same, so Dijkstra and Kruskal in the Lab give the same km as the map. A directed graph (flow) keeps all links.
     */
    public static Graph forLab(Graph g) {
        List<Node> nodes = new ArrayList<>();
        double[][] xy = new double[g.nodeCount()][];
        for (int i = 0; i < g.nodeCount(); i++) {
            xy[i] = new double[] {g.nodes().get(i).x(), g.nodes().get(i).y()};
        }
        NodeSpread.spread(xy, LAB_MIN_GAP);
        for (int i = 0; i < g.nodeCount(); i++) {
            Node n = g.nodes().get(i);
            nodes.add(new Node(n.id(), xy[i][0], xy[i][1], n.label()));
        }
        List<Edge> edges = new ArrayList<>();
        if (g.isDirected()) {
            edges.addAll(g.edges());
        } else {
            int n = g.nodeCount();
            double[][] w = new double[n][n];
            for (double[] row : w) {
                java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
            }
            for (Edge e : g.edges()) {
                int a = indexOf(g, e.from());
                int b = indexOf(g, e.to());
                w[a][b] = Math.min(w[a][b], e.weight());
                w[b][a] = w[a][b];
            }
            for (Edge e : g.edges()) {
                int a = indexOf(g, e.from());
                int b = indexOf(g, e.to());
                boolean needed = true;
                for (int k = 0; k < n && needed; k++) {
                    if (k != a && k != b && w[a][k] + w[k][b] <= e.weight() + 1e-9) {
                        needed = false;
                    }
                }
                if (needed) {
                    edges.add(new Edge(edges.size(), e.from(), e.to(), e.weight()));
                }
            }
        }
        return new Graph(g.isDirected(), nodes, edges);
    }

    private static int indexOf(Graph g, int nodeId) {
        for (int i = 0; i < g.nodes().size(); i++) {
            if (g.nodes().get(i).id() == nodeId) {
                return i;
            }
        }
        throw new IllegalArgumentException("No node " + nodeId);
    }
}

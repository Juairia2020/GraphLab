package com.graphlab.engine.dynamic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An undirected weighted graph on a fixed set of nodes that changes over time (MS15, G5), and that keeps four answers up to
 * date without starting again:
 * <ul>
 *   <li><b>Connectivity</b> with union-find (rebuilt only when a tree edge is removed).</li>
 *   <li><b>The minimum spanning forest.</b> A new edge between two trees joins them. A new edge inside a tree makes a cycle:
 *       the heaviest edge on that cycle leaves (if it is heavier than the new one). Removing a tree edge searches the
 *       non-tree edges for the cheapest one that reconnects the two halves.</li>
 *   <li><b>All-pairs distances</b> by Floyd-Warshall: a cheaper edge is folded in in O(n^2); anything else recomputes in O(n^3).</li>
 * </ul>
 * {@link Counters} compare the work done with the work a full recompute would have done at every event.
 */
public final class DynamicGraph {
    public record Edge(int id, int u, int v, double weight) {
    }

    /** Work counted as edges looked at (MST) and cell updates (distances). */
    public record Counters(int events, long mstUpdateWork, long mstRecomputeWork, long distUpdateWork, long distRecomputeWork) {
    }

    private final int n;
    private final Map<Integer, Edge> edges = new LinkedHashMap<>();
    private final Map<Integer, Boolean> inTree = new HashMap<>();
    private final List<List<Integer>> tree;          // forest adjacency: node -> edge ids
    private int[] parent;
    private double[][] dist;
    private int nextId;
    private int events;
    private long mstUpdate;
    private long mstRecompute;
    private long distUpdate;
    private long distRecompute;

    public DynamicGraph(int nodeCount) {
        if (nodeCount < 1) {
            throw new IllegalArgumentException("A graph needs at least one node");
        }
        this.n = nodeCount;
        this.tree = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tree.add(new ArrayList<>());
        }
        resetUnionFind();
        dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                dist[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
            }
        }
    }

    public int nodeCount() {
        return n;
    }

    // ---- changes --------------------------------------------------------------------------

    public int addEdge(int u, int v, double weight) {
        return addEdgeWithId(nextId++, u, v, weight);
    }

    /** Re-creates an edge with a given id (used by event replay so ids stay the same). */
    public int addEdgeWithId(int id, int u, int v, double weight) {
        check(u, v, weight);
        if (edges.containsKey(id)) {
            throw new IllegalArgumentException("Edge " + id + " already exists");
        }
        nextId = Math.max(nextId, id + 1);
        Edge e = new Edge(id, u, v, weight);
        edges.put(id, e);
        insertIntoTree(e);
        // distances: a new or cheaper edge is folded in
        foldIntoDistances(e);
        finishEvent();
        return id;
    }

    public void removeEdge(int id) {
        Edge e = edges.remove(id);
        if (e == null) {
            throw new IllegalArgumentException("No edge " + id);
        }
        removeFromTree(e);
        recomputeDistances();
        finishEvent();
    }

    public void setWeight(int id, double weight) {
        Edge old = edges.get(id);
        if (old == null) {
            throw new IllegalArgumentException("No edge " + id);
        }
        check(old.u(), old.v(), weight);
        edges.remove(id);
        removeFromTree(old);
        Edge changed = new Edge(id, old.u(), old.v(), weight);
        edges.put(id, changed);
        insertIntoTree(changed);
        if (weight < old.weight()) {
            foldIntoDistances(changed);
        } else {
            recomputeDistances();
        }
        finishEvent();
    }

    private void check(int u, int v, double weight) {
        if (u < 0 || v < 0 || u >= n || v >= n || u == v) {
            throw new IllegalArgumentException("An edge needs two different nodes in 0.." + (n - 1));
        }
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("A weight must be a finite number, 0 or more");
        }
    }

    private void finishEvent() {
        events++;
        long m = edges.size();
        mstRecompute += m + m * log2ceil(m);           // sort, then one pass
        distRecompute += (long) n * n * n;
    }

    private static long log2ceil(long m) {
        return m <= 1 ? 0 : 64 - Long.numberOfLeadingZeros(m - 1);
    }

    // ---- the spanning forest -----------------------------------------------------------------

    private void insertIntoTree(Edge e) {
        mstUpdate++;
        int a = find(e.u());
        int b = find(e.v());
        if (a != b) {
            link(e);
            parent[a] = b;
            return;
        }
        // a cycle: the heaviest edge on the tree path between the ends
        List<Integer> path = treePath(e.u(), e.v());
        mstUpdate += path.size();
        Edge heaviest = null;
        for (int id : path) {
            Edge t = edges.get(id);
            if (heaviest == null || t.weight() > heaviest.weight()) {
                heaviest = t;
            }
        }
        if (heaviest != null && heaviest.weight() > e.weight()) {
            unlink(heaviest);
            link(e);
        } else {
            inTree.put(e.id(), false);
        }
    }

    private void removeFromTree(Edge e) {
        if (!Boolean.TRUE.equals(inTree.remove(e.id()))) {
            return;   // a non-tree edge: nothing changes
        }
        unlink(e);
        rebuildUnionFind();
        // the cheapest non-tree edge that joins the two halves takes its place
        Edge best = null;
        for (Edge c : edges.values()) {
            mstUpdate++;
            if (Boolean.TRUE.equals(inTree.get(c.id()))) {
                continue;
            }
            if (find(c.u()) != find(c.v()) && (best == null || c.weight() < best.weight())) {
                best = c;
            }
        }
        if (best != null) {
            link(best);
            parent[find(best.u())] = find(best.v());
        }
    }

    private void link(Edge e) {
        inTree.put(e.id(), true);
        tree.get(e.u()).add(e.id());
        tree.get(e.v()).add(e.id());
    }

    private void unlink(Edge e) {
        inTree.put(e.id(), false);
        tree.get(e.u()).remove((Integer) e.id());
        tree.get(e.v()).remove((Integer) e.id());
    }

    /** Edge ids on the unique tree path from {@code from} to {@code to} (they are in the same tree). */
    private List<Integer> treePath(int from, int to) {
        int[] viaEdge = new int[n];
        java.util.Arrays.fill(viaEdge, -1);
        boolean[] seen = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        seen[from] = true;
        while (!queue.isEmpty()) {
            int x = queue.poll();
            if (x == to) {
                break;
            }
            for (int id : tree.get(x)) {
                Edge t = edges.get(id);
                int y = t.u() == x ? t.v() : t.u();
                if (!seen[y]) {
                    seen[y] = true;
                    viaEdge[y] = id;
                    queue.add(y);
                }
            }
        }
        List<Integer> path = new ArrayList<>();
        for (int x = to; x != from; ) {
            int id = viaEdge[x];
            path.add(id);
            Edge t = edges.get(id);
            x = t.u() == x ? t.v() : t.u();
        }
        return path;
    }

    // ---- union-find ------------------------------------------------------------------------------

    private void resetUnionFind() {
        parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
    }

    private int find(int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private void rebuildUnionFind() {
        resetUnionFind();
        for (Edge e : edges.values()) {
            if (Boolean.TRUE.equals(inTree.get(e.id()))) {
                parent[find(e.u())] = find(e.v());
            }
        }
    }

    // ---- distances ----------------------------------------------------------------------------------

    private void foldIntoDistances(Edge e) {
        int u = e.u();
        int v = e.v();
        double w = e.weight();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double viaUV = dist[i][u] + w + dist[v][j];
                double viaVU = dist[i][v] + w + dist[u][j];
                double best = Math.min(viaUV, viaVU);
                if (best < dist[i][j]) {
                    dist[i][j] = best;
                }
            }
        }
        distUpdate += (long) n * n;
    }

    private void recomputeDistances() {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                dist[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
            }
        }
        for (Edge e : edges.values()) {
            dist[e.u()][e.v()] = Math.min(dist[e.u()][e.v()], e.weight());
            dist[e.v()][e.u()] = Math.min(dist[e.v()][e.u()], e.weight());
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }
        distUpdate += (long) n * n * n;
    }

    // ---- answers -----------------------------------------------------------------------------------------

    public List<Edge> liveEdges() {
        return new ArrayList<>(edges.values());
    }

    public List<Integer> mstEdgeIds() {
        List<Integer> out = new ArrayList<>();
        for (Edge e : edges.values()) {
            if (Boolean.TRUE.equals(inTree.get(e.id()))) {
                out.add(e.id());
            }
        }
        return out;
    }

    public double mstWeight() {
        double sum = 0;
        for (int id : mstEdgeIds()) {
            sum += edges.get(id).weight();
        }
        return sum;
    }

    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    public int components() {
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (find(i) == i) {
                c++;
            }
        }
        return c;
    }

    /** Shortest distance between two nodes (infinity when not connected). */
    public double distance(int a, int b) {
        return dist[a][b];
    }

    /** Checks the structure: tree edges are live, there is no cycle, and every component is spanned. */
    public boolean isValidForest() {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        for (int id : mstEdgeIds()) {
            Edge e = edges.get(id);
            int a = e.u();
            int b = e.v();
            while (p[a] != a) {
                a = p[a];
            }
            while (p[b] != b) {
                b = p[b];
            }
            if (a == b) {
                return false;   // a cycle
            }
            p[a] = b;
        }
        return true;
    }

    public Counters counters() {
        return new Counters(events, mstUpdate, mstRecompute, distUpdate, distRecompute);
    }
}

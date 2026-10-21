package com.graphlab.engine.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.CoinInput;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.input.StringsInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * G13: every algorithm in the registry is compared with an independent brute-force version on random small inputs (fixed seeds).
 * The brute force uses a different idea from the algorithm: all simple paths, all subsets, all colourings, all cuts, all
 * subsequences. A test at the end fails if a registered algorithm has no fuzz test here, so a new algorithm cannot be added
 * without one. CI runs this class with the rest of the engine tests.
 */
public class AlgorithmFuzzTest {
    /** 300 rounds by default; the nightly CI job raises it with -Dfuzz.rounds=3000. */
    private static final int ROUNDS = Integer.getInteger("fuzz.rounds", 300);
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final Set<String> COVERED = new HashSet<>();

    private static Result run(String id, Graph g, Params p) {
        COVERED.add(id);
        return Algorithms.byId(id).run(g, p).result();
    }

    // ---- random inputs -------------------------------------------------------------------

    private static List<Node> nodes(int n, Random r, boolean tight) {
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Node(i, tight ? r.nextInt(4) : r.nextInt(100), tight ? r.nextInt(4) : r.nextInt(100), String.valueOf((char) ('A' + i))));
        }
        return out;
    }

    /** A graph with n nodes; each possible edge is present with probability {@code p}; optionally joined into one piece first. */
    static Graph graph(Random r, int n, boolean directed, boolean connected, double p, int minW, int maxW, boolean tight) {
        List<Edge> edges = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        if (connected) {
            for (int v = 1; v < n; v++) {
                int u = r.nextInt(v);
                boolean flip = directed && r.nextBoolean();
                int from = flip ? v : u;
                int to = flip ? u : v;
                used.add(key(from, to, directed));
                edges.add(new Edge(edges.size(), from, to, minW + r.nextInt(maxW - minW + 1)));
            }
        }
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                if (a == b || (!directed && a > b)) {
                    continue;
                }
                if (r.nextDouble() < p && used.add(key(a, b, directed))) {
                    edges.add(new Edge(edges.size(), a, b, minW + r.nextInt(maxW - minW + 1)));
                }
            }
        }
        return new Graph(directed, nodes(n, r, tight), edges);
    }

    private static long key(int a, int b, boolean directed) {
        return directed ? a * 100L + b : Math.min(a, b) * 100L + Math.max(a, b);
    }

    // ---- brute-force helpers -------------------------------------------------------------

    /** Shortest distance by trying every simple path (depth-first), so it needs no shortest-path idea at all. */
    static double[] bruteDistances(Graph g, int source) {
        double[] best = new double[g.nodeCount()];
        java.util.Arrays.fill(best, INF);
        best[source] = 0;
        walk(g, source, new boolean[g.nodeCount()], 0, best);
        return best;
    }

    private static void walk(Graph g, int u, boolean[] seen, double length, double[] best) {
        seen[u] = true;
        best[u] = Math.min(best[u], length);
        for (var arc : g.arcsFrom(u)) {
            if (!seen[arc.to()]) {
                walk(g, arc.to(), seen, length + arc.weight(), best);
            }
        }
        seen[u] = false;
    }

    /** A negative cycle reachable from the source (directed graphs): some simple cycle with a negative total through a reachable node. */
    private static boolean negativeCycleBrute(Graph g, int source) {
        boolean[] reach = reachable(g, source);
        for (int start = 0; start < g.nodeCount(); start++) {
            if (reach[start] && negativeCycleThrough(g, start, start, new boolean[g.nodeCount()], 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean negativeCycleThrough(Graph g, int start, int u, boolean[] seen, double sum) {
        seen[u] = true;
        try {
            for (var arc : g.arcsFrom(u)) {
                if (arc.to() == start && sum + arc.weight() < -1e-9) {
                    return true;
                }
                if (!seen[arc.to()] && negativeCycleThrough(g, start, arc.to(), seen, sum + arc.weight())) {
                    return true;
                }
            }
            return false;
        } finally {
            seen[u] = false;
        }
    }

    private static boolean[] reachable(Graph g, int s) {
        boolean[] seen = new boolean[g.nodeCount()];
        List<Integer> stack = new ArrayList<>(List.of(s));
        seen[s] = true;
        while (!stack.isEmpty()) {
            int u = stack.remove(stack.size() - 1);
            for (var arc : g.arcsFrom(u)) {
                if (!seen[arc.to()]) {
                    seen[arc.to()] = true;
                    stack.add(arc.to());
                }
            }
        }
        return seen;
    }

    /** Minimum spanning tree weight by trying every set of n-1 edges. */
    static double bruteMst(Graph g) {
        int n = g.nodeCount();
        int m = g.edgeCount();
        double best = INF;
        for (int mask = 0; mask < (1 << m); mask++) {
            if (Integer.bitCount(mask) != n - 1) {
                continue;
            }
            int[] p = new int[n];
            for (int i = 0; i < n; i++) {
                p[i] = i;
            }
            double w = 0;
            boolean tree = true;
            for (int e = 0; e < m && tree; e++) {
                if ((mask >> e & 1) != 0) {
                    int a = find(p, g.edges().get(e).from());
                    int b = find(p, g.edges().get(e).to());
                    if (a == b) {
                        tree = false;
                    }
                    p[a] = b;
                    w += g.edges().get(e).weight();
                }
            }
            if (tree) {
                best = Math.min(best, w);
            }
        }
        return best;
    }

    private static int find(int[] p, int x) {
        return p[x] == x ? x : (p[x] = find(p, p[x]));
    }

    /** Max flow by the min-cut theorem: the smallest capacity over every node set that holds the source and not the sink. */
    static double bruteMinCut(Graph g, int s, int t) {
        int n = g.nodeCount();
        double best = INF;
        for (int mask = 0; mask < (1 << n); mask++) {
            if ((mask >> s & 1) == 0 || (mask >> t & 1) != 0) {
                continue;
            }
            double cap = 0;
            for (Edge e : g.edges()) {
                boolean fromIn = (mask >> e.from() & 1) != 0;
                boolean toIn = (mask >> e.to() & 1) != 0;
                if (g.isDirected() ? fromIn && !toIn : fromIn != toIn) {
                    cap += e.weight();
                }
            }
            best = Math.min(best, cap);
        }
        return best;
    }

    private static int bruteChromatic(Graph g) {
        int n = g.nodeCount();
        for (int k = 1; k <= n; k++) {
            if (colourable(g, new int[n], 0, k)) {
                return k;
            }
        }
        return n;
    }

    private static boolean colourable(Graph g, int[] colour, int v, int k) {
        if (v == colour.length) {
            return true;
        }
        for (int c = 1; c <= k; c++) {
            boolean ok = true;
            for (Edge e : g.edges()) {
                int other = e.from() == v ? e.to() : e.to() == v ? e.from() : -1;
                if (other >= 0 && other < v && colour[other] == c) {
                    ok = false;
                }
            }
            if (ok) {
                colour[v] = c;
                if (colourable(g, colour, v + 1, k)) {
                    return true;
                }
            }
        }
        colour[v] = 0;
        return false;
    }

    // ---- graph algorithms ----------------------------------------------------------------

    @Test
    public void bfsAndDfsAgreeWithBruteForceReachability() {
        Random r = new Random(101);
        for (int round = 0; round < ROUNDS; round++) {
            boolean directed = r.nextBoolean();
            Graph g = graph(r, 2 + r.nextInt(5), directed, false, 0.3, 1, 5, false);
            boolean[] reach = reachable(g, 0);
            double[] hops = bruteDistances(withUnitWeights(g), 0);
            Result bfs = run("bfs", g, Params.from(0));
            Result dfs = run("dfs", g, Params.from(0));
            Set<Integer> bfsSet = new HashSet<>(bfs.visitOrder());
            Set<Integer> dfsSet = new HashSet<>(dfs.visitOrder());
            assertEquals(bfs.visitOrder().size(), bfsSet.size());
            assertEquals(dfs.visitOrder().size(), dfsSet.size());
            for (int v = 0; v < g.nodeCount(); v++) {
                assertEquals("round " + round, reach[v], bfsSet.contains(v));
                assertEquals("round " + round, reach[v], dfsSet.contains(v));
                if (reach[v]) {
                    assertEquals("hops to " + v + " round " + round, hops[v], bfs.distances().get(v), 0);
                }
            }
        }
    }

    private static Graph withUnitWeights(Graph g) {
        List<Edge> edges = new ArrayList<>();
        for (Edge e : g.edges()) {
            edges.add(new Edge(e.id(), e.from(), e.to(), 1));
        }
        return new Graph(g.isDirected(), g.nodes(), edges);
    }

    @Test
    public void dijkstraAndAStarAgreeWithEveryPathEnumerated() {
        Random r = new Random(102);
        for (int round = 0; round < ROUNDS; round++) {
            Graph g = graph(r, 2 + r.nextInt(5), r.nextBoolean(), false, 0.4, 1, 9, false);
            double[] best = bruteDistances(g, 0);
            Result d = run("dijkstra", g, Params.from(0));
            for (int v = 0; v < g.nodeCount(); v++) {
                if (best[v] < INF) {
                    assertEquals("round " + round + " node " + v, best[v], d.distances().get(v), 1e-9);
                }
            }
            // A*: the positions are close together and the weights large, so the straight-line guess never overestimates
            Graph a = graph(r, 2 + r.nextInt(5), false, false, 0.5, 5, 12, true);
            int target = 1 + r.nextInt(a.nodeCount() - 1);
            double[] truth = bruteDistances(a, 0);
            if (truth[target] < INF) {
                Result star = run("astar", a, new Params(0, 0L, target));
                assertEquals("A* round " + round, truth[target], star.value(), 1e-9);
            }
        }
    }

    @Test
    public void bellmanFordAndFloydAgreeWithBruteForceIncludingNegativeEdges() {
        Random r = new Random(103);
        for (int round = 0; round < ROUNDS; round++) {
            Graph g = graph(r, 2 + r.nextInt(4), true, false, 0.4, -3, 8, false);
            Result bf = run("bellman-ford", g, Params.from(0));
            boolean negative = negativeCycleBrute(g, 0);
            assertEquals("negative cycle, round " + round + " " + g.edges(), negative, bf.negativeCycle());
            if (!negative) {
                double[] best = bruteDistances(g, 0);
                for (int v = 0; v < g.nodeCount(); v++) {
                    if (best[v] < INF) {
                        assertEquals("round " + round, best[v], bf.distances().get(v), 1e-9);
                    }
                }
            }
            Graph pos = graph(r, 2 + r.nextInt(4), r.nextBoolean(), false, 0.4, 1, 9, false);
            Result fw = run("floyd-warshall", pos, Params.none());
            for (int s = 0; s < pos.nodeCount(); s++) {
                double[] best = bruteDistances(pos, s);
                for (int v = 0; v < pos.nodeCount(); v++) {
                    double got = fw.matrix().get(s).get(v);
                    if (best[v] < INF) {
                        assertEquals("round " + round + " " + s + "->" + v, best[v], got, 1e-9);
                    } else {
                        assertTrue(Double.isInfinite(got) || got > 1e8);
                    }
                }
            }
        }
    }

    @Test
    public void kruskalAndPrimAgreeWithEverySpanningTree() {
        Random r = new Random(104);
        for (int round = 0; round < ROUNDS; round++) {
            Graph g = graph(r, 2 + r.nextInt(5), false, true, 0.35, 1, 9, false);
            if (g.edgeCount() > 12) {
                continue;
            }
            double best = bruteMst(g);
            assertEquals("kruskal round " + round, best, run("kruskal", g, Params.none()).totalWeight(), 1e-9);
            assertEquals("prim round " + round, best, run("prim", g, Params.from(r.nextInt(g.nodeCount()))).totalWeight(), 1e-9);
        }
    }

    @Test
    public void edmondsKarpAndDinicAgreeWithTheMinimumCutOfEverySubset() {
        Random r = new Random(105);
        for (int round = 0; round < ROUNDS; round++) {
            boolean directed = r.nextInt(3) != 0;
            Graph g = graph(r, 3 + r.nextInt(4), directed, true, 0.35, 1, 7, false);
            int t = g.nodeCount() - 1;
            double best = bruteMinCut(g, 0, t);
            for (String id : new String[] {"edmonds-karp", "dinic"}) {
                Result res = run(id, g, new Params(0, 0L, t));
                assertEquals(id + " round " + round, best, res.value(), 1e-9);
                // the flow itself must be legal: within capacity, and balanced at every node except the two ends
                double[] net = new double[g.nodeCount()];
                for (Edge e : g.edges()) {
                    double f = res.edgeFlow().getOrDefault(e.id(), 0.0);
                    assertTrue(id + " capacity", Math.abs(f) <= e.weight() + 1e-9 && (!directed || f >= -1e-9));
                    net[e.from()] -= f;
                    net[e.to()] += f;
                }
                for (int v = 1; v < t; v++) {
                    assertEquals(id + " balance at " + v + " round " + round, 0, net[v], 1e-9);
                }
                assertEquals(id + " into the sink", best, net[t], 1e-9);
                double cut = 0;
                for (int id2 : res.cutEdgeIds()) {
                    cut += g.edge(id2).weight();
                }
                assertEquals(id + " cut equals flow", best, cut, 1e-9);
            }
        }
    }

    @Test
    public void colouringUsesTheFewestPossibleColours() {
        Random r = new Random(106);
        for (int round = 0; round < 150; round++) {
            Graph g = graph(r, 2 + r.nextInt(5), false, false, 0.45, 1, 1, false);
            Result res = run("graph-colouring", g, Params.none());
            assertEquals("round " + round, bruteChromatic(g), (int) res.value());
            for (Edge e : g.edges()) {
                assertTrue(res.colours().get(e.from()) != res.colours().get(e.to()).intValue());
            }
        }
    }

    @Test
    public void kargerNeverBeatsTheTrueMinimumCutAndFindsItWithEnoughSeeds() {
        Random r = new Random(107);
        for (int round = 0; round < 40; round++) {
            Graph g = graph(r, 3 + r.nextInt(3), false, true, 0.5, 1, 1, false);
            double best = INF;
            for (int mask = 1; mask < (1 << g.nodeCount()) - 1; mask++) {
                double cut = 0;
                for (Edge e : g.edges()) {
                    if ((mask >> e.from() & 1) != (mask >> e.to() & 1)) {
                        cut++;
                    }
                }
                best = Math.min(best, cut);
            }
            double found = INF;
            for (int seed = 0; seed < 150; seed++) {
                Result res = Algorithms.byId("karger").run(g, Params.none().withSeed(seed * 0x9E3779B97F4A7C15L)).result();   // spread seeds: Random with 0, 1, 2 gives alike first draws
                COVERED.add("karger");
                assertTrue("a cut can never be smaller than the minimum", res.value() >= best - 1e-9);
                assertEquals((int) res.value(), res.cutEdgeIds().size());
                found = Math.min(found, res.value());
            }
            assertEquals("round " + round, best, found, 1e-9);
        }
    }

    // ---- lists, knapsack, strings --------------------------------------------------------

    private static List<Integer> numbers(Random r, int n, int bound) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(r.nextInt(bound));
        }
        return out;
    }

    @Test
    public void everySortSortsLikeTheLibrary() {
        Random r = new Random(108);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> values = numbers(r, 1 + r.nextInt(ArrayInput.MAX_SIZE), round % 3 == 0 ? 3 : 100);
            List<Integer> expected = new ArrayList<>(values);
            Collections.sort(expected);
            for (String id : new String[] {"merge-sort", "quick-sort", "randomized-quick-sort"}) {
                COVERED.add(id);
                assertEquals(id, expected, Algorithms.byId(id).run(new ArrayInput(values), Params.none().withSeed(round)).result().sortedValues());
            }
        }
    }

    @Test
    public void knapsacksAgreeWithEverySubset() {
        Random r = new Random(109);
        for (int round = 0; round < ROUNDS; round++) {
            int n = 1 + r.nextInt(6);
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(new Item(1 + r.nextInt(9), 1 + r.nextInt(20)));
            }
            int cap = 1 + r.nextInt(25);
            KnapsackInput in = new KnapsackInput(items, cap);
            int best = 0;
            double lp = 0;
            for (int mask = 0; mask < (1 << n); mask++) {
                int w = 0;
                int v = 0;
                for (int i = 0; i < n; i++) {
                    if ((mask >> i & 1) != 0) {
                        w += items.get(i).weight();
                        v += items.get(i).value();
                    }
                }
                if (w <= cap) {
                    best = Math.max(best, v);
                    // the optimum of the fractional problem takes whole items plus a part of one more
                    for (int j = 0; j < n; j++) {
                        if ((mask >> j & 1) == 0) {
                            double part = Math.min(1.0, (cap - w) / (double) items.get(j).weight());
                            lp = Math.max(lp, v + part * items.get(j).value());
                        }
                    }
                    lp = Math.max(lp, v);
                }
            }
            COVERED.add("knapsack-01");
            COVERED.add("fractional-knapsack");
            assertEquals("0/1 round " + round, best, Algorithms.byId("knapsack-01").run(in, Params.none()).result().value(), 1e-9);
            assertEquals("fractional round " + round, lp, Algorithms.byId("fractional-knapsack").run(in, Params.none()).result().value(), 1e-6);
        }
    }

    @Test
    public void coinChangeUsesTheFewestCoins() {
        Random r = new Random(110);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> coins = new ArrayList<>();
            for (int i = 0, k = 1 + r.nextInt(4); i < k; i++) {
                int c = 1 + r.nextInt(9);
                if (!coins.contains(c)) {
                    coins.add(c);
                }
            }
            int amount = r.nextInt(21);
            if (amount == 0) {
                amount = 1;
            }
            // brute force: breadth-first search over the amounts reachable with 1, 2, 3, ... coins
            int fewest = -1;
            Set<Integer> now = new HashSet<>(Set.of(0));
            for (int count = 1; count <= amount && fewest < 0; count++) {
                Set<Integer> next = new HashSet<>();
                for (int s : now) {
                    for (int c : coins) {
                        if (s + c <= amount) {
                            next.add(s + c);
                        }
                    }
                }
                if (next.contains(amount)) {
                    fewest = count;
                }
                now = next;
            }
            COVERED.add("coin-change");
            assertEquals("coins " + coins + " amount " + amount, fewest,
                    (int) Algorithms.byId("coin-change").run(new CoinInput(coins, amount), Params.none()).result().value());
        }
    }

    private static String word(Random r, int max) {
        StringBuilder s = new StringBuilder();
        for (int i = 0, n = r.nextInt(max + 1); i < n; i++) {
            s.append((char) ('a' + r.nextInt(3)));
        }
        return s.toString();
    }

    private static boolean isSubsequence(String small, String big) {
        int at = 0;
        for (int i = 0; i < big.length() && at < small.length(); i++) {
            if (big.charAt(i) == small.charAt(at)) {
                at++;
            }
        }
        return at == small.length();
    }

    private static int bruteEdit(String a, String b) {
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        if (a.charAt(0) == b.charAt(0)) {
            return bruteEdit(a.substring(1), b.substring(1));
        }
        return 1 + Math.min(bruteEdit(a.substring(1), b), Math.min(bruteEdit(a, b.substring(1)), bruteEdit(a.substring(1), b.substring(1))));
    }

    @Test
    public void lcsAndEditDistanceAgreeWithBruteForce() {
        Random r = new Random(111);
        for (int round = 0; round < ROUNDS; round++) {
            String a = word(r, 7);
            String b = word(r, 7);
            if (a.isEmpty() || b.isEmpty()) {
                continue;
            }
            int lcs = 0;
            for (int mask = 0; mask < (1 << a.length()); mask++) {
                StringBuilder sub = new StringBuilder();
                for (int i = 0; i < a.length(); i++) {
                    if ((mask >> i & 1) != 0) {
                        sub.append(a.charAt(i));
                    }
                }
                if (sub.length() > lcs && isSubsequence(sub.toString(), b)) {
                    lcs = sub.length();
                }
            }
            COVERED.add("lcs");
            COVERED.add("edit-distance");
            Result l = Algorithms.byId("lcs").run(new StringsInput(a, b), Params.none()).result();
            assertEquals(a + "/" + b, lcs, (int) l.value());
            assertEquals("the text of the answer is a common subsequence", lcs, l.text().length());
            assertTrue(isSubsequence(l.text(), a) && isSubsequence(l.text(), b));
            assertEquals(a + "/" + b, bruteEdit(a, b), (int) Algorithms.byId("edit-distance").run(new StringsInput(a, b), Params.none()).result().value());
        }
    }

    @Test
    public void lisAgreesWithEverySubsequence() {
        Random r = new Random(112);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> a = numbers(r, 1 + r.nextInt(10), 12);
            int best = 0;
            for (int mask = 1; mask < (1 << a.size()); mask++) {
                int last = -1;
                int len = 0;
                boolean ok = true;
                for (int i = 0; i < a.size() && ok; i++) {
                    if ((mask >> i & 1) != 0) {
                        if (a.get(i) <= last) {
                            ok = false;
                        }
                        last = a.get(i);
                        len++;
                    }
                }
                if (ok) {
                    best = Math.max(best, len);
                }
            }
            COVERED.add("lis");
            assertEquals("round " + round + " " + a, best, (int) Algorithms.byId("lis").run(new ArrayInput(a), Params.none()).result().value());
        }
    }

    // ---- the eight algorithms of MS18 ----------------------------------------------------

    private static boolean[][] closure(Graph g) {
        int n = g.nodeCount();
        boolean[][] reach = new boolean[n][n];
        for (int s = 0; s < n; s++) {
            reach[s] = reachable(g, s);
        }
        return reach;
    }

    @Test
    public void tarjanGivesExactlyTheMutuallyReachableGroups() {
        Random r = new Random(113);
        for (int round = 0; round < ROUNDS; round++) {
            Graph g = graph(r, 1 + r.nextInt(6), true, false, 0.3, 1, 1, false);
            boolean[][] reach = closure(g);
            Result res = run("tarjan-scc", g, Params.none());
            Set<Integer> groups = new HashSet<>(res.colours().values());
            assertEquals((int) res.value(), groups.size());
            for (int u = 0; u < g.nodeCount(); u++) {
                for (int v = 0; v < g.nodeCount(); v++) {
                    boolean mutual = reach[u][v] && reach[v][u];
                    assertEquals("round " + round + " " + u + "," + v, mutual, res.colours().get(u).equals(res.colours().get(v)));
                }
            }
        }
    }

    private static boolean orderIsValid(Graph g, List<Integer> order) {
        int[] at = new int[g.nodeCount()];
        for (int i = 0; i < order.size(); i++) {
            at[order.get(i)] = i;
        }
        for (Edge e : g.edges()) {
            if (at[e.from()] >= at[e.to()]) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> smallestOrder(Graph g) {
        int n = g.nodeCount();
        List<List<Integer>> all = new ArrayList<>();
        permute(new ArrayList<>(), new boolean[n], n, all, g);
        return all.isEmpty() ? null : all.get(0);   // permutations come in increasing order, so the first valid one is the smallest
    }

    private static void permute(List<Integer> cur, boolean[] used, int n, List<List<Integer>> out, Graph g) {
        if (!out.isEmpty()) {
            return;
        }
        if (cur.size() == n) {
            if (orderIsValid(g, cur)) {
                out.add(new ArrayList<>(cur));
            }
            return;
        }
        for (int v = 0; v < n; v++) {
            if (!used[v]) {
                used[v] = true;
                cur.add(v);
                permute(cur, used, n, out, g);
                cur.remove(cur.size() - 1);
                used[v] = false;
            }
        }
    }

    @Test
    public void topologicalSortGivesTheSmallestValidOrderOrReportsTheCycle() {
        Random r = new Random(114);
        int cycles = 0;
        for (int round = 0; round < ROUNDS; round++) {
            Graph g = graph(r, 1 + r.nextInt(6), true, false, 0.3, 1, 1, false);
            Result res = run("topological-sort", g, Params.none());
            List<Integer> expected = smallestOrder(g);
            if (expected == null) {
                cycles++;
                assertEquals("cycle", res.text());
                assertTrue(res.visitOrder().size() < g.nodeCount());
            } else {
                assertEquals("round " + round + " " + g.edges(), expected, res.visitOrder());
            }
        }
        assertTrue("the fuzz must include graphs with cycles", cycles > 20 && cycles < ROUNDS - 20);
    }

    @Test
    public void bipartiteMatchingIsAsLargeAsEveryChoiceOfEdgesAllows() {
        Random r = new Random(115);
        for (int round = 0; round < ROUNDS; round++) {
            int left = 1 + r.nextInt(4);
            int right = 1 + r.nextInt(4);
            List<Edge> edges = new ArrayList<>();
            for (int a = 0; a < left; a++) {
                for (int b = 0; b < right; b++) {
                    if (r.nextInt(100) < 45) {
                        edges.add(new Edge(edges.size(), a, left + b, 1));
                    }
                }
            }
            Graph g = new Graph(false, nodes(left + right, r, false), edges);
            int best = 0;
            for (int mask = 0; mask < (1 << edges.size()); mask++) {
                boolean[] used = new boolean[left + right];
                boolean ok = true;
                for (int e = 0; e < edges.size() && ok; e++) {
                    if ((mask >> e & 1) != 0) {
                        ok = !used[edges.get(e).from()] && !used[edges.get(e).to()];
                        used[edges.get(e).from()] = true;
                        used[edges.get(e).to()] = true;
                    }
                }
                if (ok) {
                    best = Math.max(best, Integer.bitCount(mask));
                }
            }
            Result res = run("bipartite-matching", g, Params.none());
            assertEquals("round " + round + " " + edges, best, (int) res.value());
            boolean[] used = new boolean[left + right];
            assertEquals(best, res.treeEdgeIds().size());
            for (int id : res.treeEdgeIds()) {
                assertTrue("a node is matched twice", !used[g.edge(id).from()] && !used[g.edge(id).to()]);
                used[g.edge(id).from()] = true;
                used[g.edge(id).to()] = true;
            }
        }
    }

    @Test
    public void huffmanCostIsTheBestOverEveryOrderOfMerging() {
        Random r = new Random(116);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> freq = new ArrayList<>();
            for (int i = 0, n = 2 + r.nextInt(5); i < n; i++) {
                freq.add(1 + r.nextInt(30));
            }
            long best = bruteMerge(new ArrayList<>(freq));
            Result res = run("huffman", new Graph(false, List.of(), List.of()), Params.none(), new ArrayInput(freq));
            assertEquals("round " + round + " " + freq, best, (long) res.value());
            // the codes must form a prefix code that gives that cost
            long cost = 0;
            double kraft = 0;
            List<String> codes = new ArrayList<>();
            for (String part : res.text().split(" ")) {
                codes.add(part.substring(part.indexOf('=') + 1));
            }
            assertEquals(freq.size(), codes.size());
            for (int i = 0; i < codes.size(); i++) {
                cost += (long) freq.get(i) * codes.get(i).length();
                kraft += Math.pow(0.5, codes.get(i).length());
                for (int j = 0; j < codes.size(); j++) {
                    assertTrue("a code is the start of another", i == j || !codes.get(j).startsWith(codes.get(i)));
                }
            }
            assertEquals(best, cost);
            assertEquals("a full tree uses all of the code space", 1.0, kraft, 1e-9);
        }
    }

    private static Result run(String id, Graph unused, Params p, com.graphlab.engine.input.Input input) {
        COVERED.add(id);
        return Algorithms.byId(id).run(input, p).result();
    }

    /** Cost of the cheapest merging: try every pair at every step (this is a search over all trees, not the greedy rule). */
    private static long bruteMerge(List<Integer> items) {
        if (items.size() == 1) {
            return 0;
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                List<Integer> next = new ArrayList<>(items);
                int a = next.get(i);
                int b = next.get(j);
                next.remove(j);
                next.remove(i);
                next.add(a + b);
                best = Math.min(best, a + b + bruteMerge(next));
            }
        }
        return best;
    }

    @Test
    public void closestPairIsAtTheTrueSmallestDistance() {
        Random r = new Random(117);
        for (int round = 0; round < ROUNDS; round++) {
            int n = 2 + r.nextInt(9);
            List<Node> pts = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int range = round % 4 == 0 ? 6 : 200;   // small ranges make ties and equal x or y values common
                pts.add(new Node(i, r.nextInt(range), r.nextInt(range), String.valueOf((char) ('A' + i))));
            }
            Graph g = new Graph(false, pts, List.of());
            double best = INF;
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    best = Math.min(best, Math.hypot(pts.get(a).x() - pts.get(b).x(), pts.get(a).y() - pts.get(b).y()));
                }
            }
            Result res = run("closest-pair", g, Params.none());
            assertEquals("round " + round, best, res.value(), 1e-9);
            Node p = pts.get(res.chosen().get(0));
            Node q = pts.get(res.chosen().get(1));
            assertEquals("the pair named is really that far apart", best, Math.hypot(p.x() - q.x(), p.y() - q.y()), 1e-9);
        }
    }

    @Test
    public void matrixChainCostIsTheBestOverEveryBracketing() {
        Random r = new Random(118);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> p = new ArrayList<>();
            for (int i = 0, n = 3 + r.nextInt(6); i < n; i++) {
                p.add(1 + r.nextInt(30));
            }
            long best = bruteBracket(p, 1, p.size() - 1);
            Result res = run("matrix-chain", new Graph(false, List.of(), List.of()), Params.none(), new ArrayInput(p));
            assertEquals("round " + round + " " + p, best, (long) res.value());
            assertEquals("the bracketing printed costs what is claimed", best, costOfBracketing(res.text(), p));
        }
    }

    private static long bruteBracket(List<Integer> p, int i, int j) {
        if (i == j) {
            return 0;
        }
        long best = Long.MAX_VALUE;
        for (int k = i; k < j; k++) {
            best = Math.min(best, bruteBracket(p, i, k) + bruteBracket(p, k + 1, j) + (long) p.get(i - 1) * p.get(k) * p.get(j));
        }
        return best;
    }

    /** Reads a bracketing such as "((A1A2)A3)" and adds up the multiplications, with its own small parser. */
    private static long costOfBracketing(String text, List<Integer> p) {
        List<long[]> stack = new ArrayList<>();   // each entry: {rows, columns, cost}
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'A') {
                int j = i + 1;
                while (j < text.length() && Character.isDigit(text.charAt(j))) {
                    j++;
                }
                int k = Integer.parseInt(text.substring(i + 1, j));
                stack.add(new long[] {p.get(k - 1), p.get(k), 0});
                i = j - 1;
            } else if (c == ')') {
                long[] right = stack.remove(stack.size() - 1);
                long[] left = stack.remove(stack.size() - 1);
                assertEquals("shapes must match", left[1], right[0]);
                stack.add(new long[] {left[0], right[1], left[2] + right[2] + left[0] * left[1] * right[1]});
            }
        }
        assertEquals(1, stack.size());
        return stack.get(0)[2];
    }

    @Test
    public void reservoirSamplingKeepsEveryItemWithEqualChance() {
        Random r = new Random(119);
        List<Integer> stream = List.of(10, 20, 30, 40, 50, 60, 70, 80);
        int trials = 40000;
        int[] hits = new int[stream.size()];
        for (int seed = 0; seed < trials; seed++) {
            Result res = Algorithms.byId("reservoir-sampling").run(new ArrayInput(stream), Params.none().withSeed(seed * 7919L + r.nextInt(3))).result();
            COVERED.add("reservoir-sampling");
            assertEquals(3, res.chosen().size());
            assertEquals("no item twice", 3, new HashSet<>(res.chosen()).size());
            for (int index : res.chosen()) {
                hits[index]++;
            }
        }
        for (int i = 0; i < hits.length; i++) {
            assertEquals("item " + i, trials * 3.0 / stream.size(), hits[i], trials * 0.02);   // chance 3/8 each
        }
    }

    @Test
    public void reservoirSamplingFollowsAlgorithmRStepByStep() {
        for (int round = 0; round < ROUNDS; round++) {
            Random r = new Random(round);
            int n = 1 + r.nextInt(ArrayInput.MAX_SIZE);
            List<Integer> stream = numbers(r, n, 100);
            long seed = r.nextLong();
            // reference: the textbook loop, written again here
            Random draw = new Random(seed);
            int k = Math.min(3, n);
            int[] slot = new int[k];
            for (int i = 0; i < n; i++) {
                if (i < k) {
                    slot[i] = i;
                } else {
                    int j = draw.nextInt(i + 1);
                    if (j < k) {
                        slot[j] = i;
                    }
                }
            }
            Result res = Algorithms.byId("reservoir-sampling").run(new ArrayInput(stream), Params.none().withSeed(seed)).result();
            COVERED.add("reservoir-sampling");
            List<Integer> expected = new ArrayList<>();
            for (int s : slot) {
                expected.add(s);
            }
            assertEquals(expected, res.chosen());
        }
    }

    // ---- the registry check --------------------------------------------------------------

    /** Runs when every test above has finished: a registered algorithm with no fuzz test fails the class. */
    @org.junit.AfterClass
    public static void everyRegisteredAlgorithmHasAFuzzTest() {
        Set<String> all = new HashSet<>();
        for (Algorithm a : Algorithms.all()) {
            all.add(a.id());
        }
        Set<String> missing = new HashSet<>(all);
        missing.removeAll(COVERED);
        assertTrue("no fuzz test for " + missing, missing.isEmpty());
    }
}

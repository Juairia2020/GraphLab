package com.graphlab.engine.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.array.MergeSort;
import com.graphlab.engine.algo.array.QuickSort;
import com.graphlab.engine.algo.array.RandomizedQuickSort;
import com.graphlab.engine.algo.graph.EdmondsKarp;
import com.graphlab.engine.algo.graph.FloydWarshall;
import com.graphlab.engine.algo.graph.GraphColouring;
import com.graphlab.engine.algo.graph.Karger;
import com.graphlab.engine.algo.knapsack.FractionalKnapsack;
import com.graphlab.engine.algo.knapsack.Knapsack01;
import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.InputParser;
import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** Checks the nine algorithms added in MS5 against independent brute force and basic invariants. */
public class NewAlgorithmsTest {
    private static final Graph SAMPLE = SampleGraph.sixNodeUndirected();

    // ---- sorting -----------------------------------------------------------------------

    private static final List<Algorithm> SORTS = List.of(new MergeSort(), new QuickSort(), new RandomizedQuickSort());

    @Test
    public void everySortSortsRandomListsAndEveryFrameIsAPermutationOfTheInput() {
        Random random = new Random(1);
        for (Algorithm sort : SORTS) {
            for (int round = 0; round < 150; round++) {
                int n = 1 + random.nextInt(ArrayInput.MAX_SIZE);
                List<Integer> values = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    values.add(random.nextInt(round % 3 == 0 ? 4 : 100));   // every third round has many duplicates
                }
                Trace trace = sort.run(new ArrayInput(values), Params.none().withSeed(round));
                List<Integer> expected = new ArrayList<>(values);
                Collections.sort(expected);
                assertEquals(sort.id() + " " + values, expected, trace.result().sortedValues());
                assertTrue(trace.size() >= 2);
                List<Integer> multiset = new ArrayList<>(expected);
                for (int s = 0; s < trace.size(); s++) {
                    ArrayFrame f = (ArrayFrame) trace.step(s).frame();
                    List<Integer> sorted = new ArrayList<>(f.values());
                    Collections.sort(sorted);
                    assertEquals(sort.id() + " step " + s + " is not a permutation", multiset, sorted);
                    assertTrue(trace.step(s).line() >= 1 && trace.step(s).line() <= sort.pseudocode().size());
                }
                ArrayFrame last = (ArrayFrame) trace.last().frame();
                assertEquals(expected, last.values());
                assertTrue("final frame all sorted", last.states().stream().allMatch(st -> st == State.VISITED));
            }
        }
    }

    @Test
    public void sortsHandleOneElementSortedReversedAndAllEqual() {
        for (Algorithm sort : SORTS) {
            for (List<Integer> values : List.of(List.of(5), List.of(1, 2, 3, 4), List.of(9, 7, 5, 3, 1), Collections.nCopies(6, 4))) {
                Trace t = sort.run(new ArrayInput(values), Params.none());
                List<Integer> expected = new ArrayList<>(values);
                Collections.sort(expected);
                assertEquals(expected, t.result().sortedValues());
            }
        }
    }

    @Test
    public void randomizedQuickSortIsReproducibleAndSeedsChangeTheSteps() {
        ArrayInput input = InputParser.randomArray(3, 12);
        Algorithm sort = new RandomizedQuickSort();
        assertEquals(sort.run(input, Params.none().withSeed(5)), sort.run(input, Params.none().withSeed(5)));
        boolean differs = false;
        for (long seed = 0; seed < 20 && !differs; seed++) {
            differs = !sort.run(input, Params.none().withSeed(seed)).steps().equals(sort.run(input, Params.none().withSeed(seed + 1)).steps());
        }
        assertTrue(differs);
    }

    @Test
    public void mergeSortUsesNoMoreThanNLogNComparisons() {
        for (int n = 1; n <= 16; n++) {
            Trace t = new MergeSort().run(InputParser.randomArray(n, n), Params.none());
            assertTrue((long) t.result().value() <= (long) Math.ceil(n * (Math.log(Math.max(n, 2)) / Math.log(2))));
        }
    }

    @Test
    public void sortsRefuseAGraphAndGraphAlgorithmsRefuseNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new MergeSort().run(SAMPLE, Params.none()));
        assertThrows(IllegalArgumentException.class,
                () -> Algorithms.byId("dijkstra").run(InputParser.defaultArray(), Params.from(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Knapsack01().run(InputParser.defaultArray(), Params.none()));
    }

    // ---- knapsack ----------------------------------------------------------------------

    private static int bruteForceKnapsack(KnapsackInput in) {
        int best = 0;
        int n = in.items().size();
        for (int mask = 0; mask < (1 << n); mask++) {
            int w = 0;
            int v = 0;
            for (int i = 0; i < n; i++) {
                if ((mask >> i & 1) == 1) {
                    w += in.items().get(i).weight();
                    v += in.items().get(i).value();
                }
            }
            if (w <= in.capacity()) {
                best = Math.max(best, v);
            }
        }
        return best;
    }

    @Test
    public void zeroOneKnapsackMatchesBruteForceAndTheChosenItemsReallyFit() {
        Random random = new Random(2);
        for (int round = 0; round < 120; round++) {
            KnapsackInput in = InputParser.randomKnapsack(random.nextLong(), 1 + random.nextInt(KnapsackInput.MAX_ITEMS));
            Trace t = new Knapsack01().run(in, Params.none());
            assertEquals((double) bruteForceKnapsack(in), t.result().value(), 0);
            int weight = 0;
            int value = 0;
            for (int i : t.result().chosen()) {
                weight += in.items().get(i).weight();
                value += in.items().get(i).value();
            }
            assertTrue(weight <= in.capacity());
            assertEquals(t.result().value(), value, 0);
            GridFrame last = (GridFrame) t.last().frame();
            assertEquals(in.items().size() + 1, last.rows());
            assertEquals(in.capacity() + 1, last.cols());
        }
    }

    @Test
    public void theTextbookKnapsackGivesNineAndTheTableIsFilledOnlyFromItsNeighbours() {
        Trace t = new Knapsack01().run(InputParser.defaultKnapsack(), Params.none());
        assertEquals(9.0, t.result().value(), 0);
        assertEquals(List.of(1, 2), t.result().chosen());   // items 2 and 3 (0-based 1 and 2)
        GridFrame last = (GridFrame) t.last().frame();
        assertEquals("9", last.cell(4, 7));
        GridFrame first = (GridFrame) t.step(0).frame();
        assertEquals("", first.cell(1, 0));                 // not computed yet
        assertEquals("0", first.cell(0, 3));
    }

    @Test
    public void fractionalKnapsackBeatsOrEqualsZeroOneAndFillsTheBag() {
        Random random = new Random(3);
        for (int round = 0; round < 120; round++) {
            KnapsackInput in = InputParser.randomKnapsack(random.nextLong(), 1 + random.nextInt(KnapsackInput.MAX_ITEMS));
            Trace f = new FractionalKnapsack().run(in, Params.none());
            assertTrue(f.result().value() + 1e-9 >= bruteForceKnapsack(in));
            double weight = 0;
            for (int i = 0; i < in.items().size(); i++) {
                weight += f.result().amounts().get(i) * in.items().get(i).weight();
            }
            assertTrue(weight <= in.capacity() + 1e-9);
            int total = in.items().stream().mapToInt(Item::weight).sum();
            if (total >= in.capacity()) {
                assertEquals(in.capacity(), weight, 1e-9);   // enough items: the bag ends up exactly full
            }
        }
        Trace textbook = new FractionalKnapsack().run(InputParser.defaultKnapsack(), Params.none());
        assertEquals(7 + 4.0 * 2 / 3, textbook.result().value(), 1e-9);
    }

    // ---- Floyd-Warshall ----------------------------------------------------------------

    private static double[][] dijkstraAll(Graph g) {
        int n = g.nodeCount();
        double[][] out = new double[n][n];
        for (int s = 0; s < n; s++) {
            Result r = Algorithms.byId("dijkstra").run(g, Params.from(g.nodes().get(s).id())).result();
            for (int t = 0; t < n; t++) {
                Double d = r.distances().get(g.nodes().get(t).id());
                out[s][t] = d == null ? Double.POSITIVE_INFINITY : d;
            }
        }
        return out;
    }

    @Test
    public void floydWarshallMatchesDijkstraOnRandomGraphsInFineAndCoarseMode() {
        Random random = new Random(4);
        for (int round = 0; round < 40; round++) {
            int n = 2 + random.nextInt(round % 2 == 0 ? 6 : 12);   // up to 7 (fine) and up to 13 (coarse)
            Graph g = randomGraph(random, n, random.nextBoolean(), false);
            Trace t = new FloydWarshall().run(g, Params.none());
            double[][] expected = dijkstraAll(g);
            List<List<Double>> got = t.result().matrix();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    assertEquals("round " + round + " " + i + "," + j, expected[i][j], got.get(i).get(j), 1e-9);
                }
            }
            assertTrue(t.last().frame() instanceof GridFrame);
        }
    }

    @Test
    public void floydWarshallFindsNegativeCyclesAndHandlesNegativeEdges() {
        Graph cycle = Graph.builder(true).node(0, 0, 0, "A").node(1, 80, 0, "B").node(2, 40, 70, "C")
                .edge(0, 0, 1, 1).edge(1, 1, 2, -3).edge(2, 2, 0, 1).build();
        Result r = new FloydWarshall().run(cycle, Params.none()).result();
        assertTrue(r.negativeCycle());
        Graph fine = Graph.builder(true).node(0, 0, 0, "A").node(1, 80, 0, "B").node(2, 40, 70, "C")
                .edge(0, 0, 1, 4).edge(1, 1, 2, -2).edge(2, 0, 2, 5).build();
        Result ok = new FloydWarshall().run(fine, Params.none()).result();
        assertFalse(ok.negativeCycle());
        assertEquals(2.0, ok.matrix().get(0).get(2), 0);
        Graph negUndirected = Graph.builder(false).node(0, 0, 0, "A").node(1, 80, 0, "B").edge(0, 0, 1, -1).build();
        assertTrue(new FloydWarshall().run(negUndirected, Params.none()).result().negativeCycle());
    }

    // ---- Edmonds-Karp ------------------------------------------------------------------

    /** Independent max flow (plain Ford-Fulkerson with DFS) on a capacity matrix. */
    private static double maxFlowMatrix(double[][] cap, int s, int t) {
        int n = cap.length;
        double[][] res = new double[n][];
        for (int i = 0; i < n; i++) {
            res[i] = cap[i].clone();
        }
        double total = 0;
        while (true) {
            double[] bottleneck = new double[1];
            int[] prev = new int[n];
            Arrays.fill(prev, -1);
            prev[s] = s;
            ArrayList<Integer> stack = new ArrayList<>(List.of(s));
            while (!stack.isEmpty() && prev[t] == -1) {
                int u = stack.remove(stack.size() - 1);
                for (int v = 0; v < n; v++) {
                    if (prev[v] == -1 && res[u][v] > 1e-9) {
                        prev[v] = u;
                        stack.add(v);
                    }
                }
            }
            if (prev[t] == -1) {
                return total;
            }
            bottleneck[0] = Double.POSITIVE_INFINITY;
            for (int v = t; v != s; v = prev[v]) {
                bottleneck[0] = Math.min(bottleneck[0], res[prev[v]][v]);
            }
            for (int v = t; v != s; v = prev[v]) {
                res[prev[v]][v] -= bottleneck[0];
                res[v][prev[v]] += bottleneck[0];
            }
            total += bottleneck[0];
        }
    }

    @Test
    public void edmondsKarpMatchesAnIndependentMaxFlowAndMinCutEqualsMaxFlow() {
        Random random = new Random(5);
        for (int round = 0; round < 80; round++) {
            int n = 2 + random.nextInt(8);
            boolean directed = random.nextBoolean();
            Graph g = randomGraph(random, n, directed, true);
            double[][] cap = new double[n][n];
            for (Edge e : g.edges()) {
                int u = e.from();
                int v = e.to();
                cap[u][v] += e.weight();
                if (!directed) {
                    cap[v][u] += e.weight();
                }
            }
            int s = 0;
            int t = n - 1;
            Trace trace = new EdmondsKarp().run(g, Params.from(s).withTarget(t));
            Result r = trace.result();
            assertEquals("round " + round, maxFlowMatrix(cap, s, t), r.value(), 1e-6);
            double cut = 0;
            for (int id : r.cutEdgeIds()) {
                cut += g.edge(id).weight();
            }
            assertEquals("min cut = max flow", r.value(), cut, 1e-6);
            // Conservation: at every node except s and t, flow in equals flow out; capacities respected.
            double[] balance = new double[n];
            for (Edge e : g.edges()) {
                double f = r.edgeFlow().get(e.id());
                assertTrue(Math.abs(f) <= e.weight() + 1e-9);
                if (directed) {
                    assertTrue(f >= -1e-9);
                }
                balance[e.from()] -= f;
                balance[e.to()] += f;
            }
            for (int v = 0; v < n; v++) {
                if (v != s && v != t) {
                    assertEquals(0.0, balance[v], 1e-6);
                }
            }
            assertEquals(r.value(), balance[t], 1e-6);
        }
    }

    @Test
    public void edmondsKarpOnTheSampleGraphSendsFifteenFromAToE() {
        Trace t = new EdmondsKarp().run(SAMPLE, Params.from(SampleGraph.A).withTarget(SampleGraph.E));
        assertEquals(15.0, t.result().value(), 0);
        GraphFrame last = (GraphFrame) t.last().frame();
        assertEquals("start", last.nodeLabels().get(SampleGraph.A));
        int de = -1;
        for (Edge e : SAMPLE.edges()) {
            if ((e.from() == SampleGraph.D && e.to() == SampleGraph.E) || (e.from() == SampleGraph.E && e.to() == SampleGraph.D)) {
                de = e.id();
            }
        }
        assertEquals(State.FAIL, last.edgeStates().get(de));
    }

    @Test
    public void edmondsKarpRefusesBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp().run(SAMPLE, Params.from(0)));                       // no end
        assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp().run(SAMPLE, Params.from(0).withTarget(0)));         // same node
        assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp().run(SAMPLE, Params.from(0).withTarget(99)));        // unknown
        Graph negative = Graph.builder(true).node(0, 0, 0, "A").node(1, 80, 0, "B").edge(0, 0, 1, -2).build();
        assertThrows(IllegalArgumentException.class, () -> new EdmondsKarp().run(negative, Params.from(0).withTarget(1)));
    }

    // ---- colouring ---------------------------------------------------------------------

    private static int bruteChromatic(Graph g) {
        int n = g.nodeCount();
        for (int k = 1; k <= n; k++) {
            if (colourable(g, new int[n], 0, k)) {
                return k;
            }
        }
        return n;
    }

    private static boolean colourable(Graph g, int[] colour, int i, int k) {
        if (i == colour.length) {
            return true;
        }
        for (int c = 1; c <= k; c++) {
            boolean ok = true;
            for (Edge e : g.edges()) {
                int a = e.from();
                int b = e.to();
                if ((a == i && b < i && colour[b] == c) || (b == i && a < i && colour[a] == c)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                colour[i] = c;
                if (colourable(g, colour, i + 1, k)) {
                    return true;
                }
                colour[i] = 0;
            }
        }
        return false;
    }

    @Test
    public void graphColouringIsProperAndUsesTheFewestColours() {
        Random random = new Random(6);
        for (int round = 0; round < 60; round++) {
            int n = 1 + random.nextInt(7);
            Graph g = randomGraph(random, n, false, false);
            Result r = new GraphColouring().run(g, Params.none()).result();
            for (Edge e : g.edges()) {
                assertTrue("round " + round, !r.colours().get(e.from()).equals(r.colours().get(e.to())));
            }
            assertEquals("round " + round, bruteChromatic(g), (int) r.value());
            assertTrue(r.colours().values().stream().allMatch(c -> c >= 1 && c <= r.value()));
        }
    }

    @Test
    public void colouringKnownGraphs() {
        assertEquals(3.0, new GraphColouring().run(SAMPLE, Params.none()).result().value(), 0);   // has a triangle (A, B, C)
        Graph edgeless = Graph.builder(false).node(0, 0, 0, "A").node(1, 80, 0, "B").build();
        assertEquals(1.0, new GraphColouring().run(edgeless, Params.none()).result().value(), 0);
        Graph k5 = complete(5);
        assertEquals(5.0, new GraphColouring().run(k5, Params.none()).result().value(), 0);       // symmetry breaking keeps this fast
        assertThrows(IllegalArgumentException.class,
                () -> new GraphColouring().run(Graph.builder(true).node(0, 0, 0, "A").node(1, 80, 0, "B").edge(0, 0, 1, 1).build(), Params.none()));
    }

    // ---- Karger ------------------------------------------------------------------------

    private static int bruteMinCut(Graph g) {
        int n = g.nodeCount();
        int best = Integer.MAX_VALUE;
        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            int cut = 0;
            for (Edge e : g.edges()) {
                if (((mask >> e.from()) & 1) != ((mask >> e.to()) & 1)) {
                    cut++;
                }
            }
            best = Math.min(best, cut);
        }
        return best;
    }

    @Test
    public void kargerAlwaysReturnsAValidCutAndFindsTheMinimumAcrossSeeds() {
        Random random = new Random(7);
        for (int round = 0; round < 25; round++) {
            Graph g = connectedRandom(random, 3 + random.nextInt(6));
            int min = bruteMinCut(g);
            int found = Integer.MAX_VALUE;
            for (long seed = 0; seed < 300; seed++) {
                Result r = new Karger().run(g, Params.none().withSeed(seed)).result();
                int size = (int) r.value();
                assertEquals(size, r.cutEdgeIds().size());
                assertTrue(size >= min);
                Set<Integer> sides = new HashSet<>(r.colours().values());
                assertEquals(Set.of(0, 1), sides);   // two non-empty sides
                for (int id : r.cutEdgeIds()) {
                    Edge e = g.edge(id);
                    assertTrue(!r.colours().get(e.from()).equals(r.colours().get(e.to())));
                }
                found = Math.min(found, size);
            }
            assertEquals("300 runs should hit the true minimum cut, round " + round, min, found);
        }
    }

    @Test
    public void kargerIsReproducibleHandlesDisconnectedGraphsAndRefusesBadInput() {
        Params p = Params.none().withSeed(11);
        assertEquals(new Karger().run(SAMPLE, p), new Karger().run(SAMPLE, p));
        Graph pieces = Graph.builder(false).node(0, 0, 0, "A").node(1, 80, 0, "B").node(2, 0, 80, "C").edge(0, 0, 1, 1).build();
        assertEquals(0.0, new Karger().run(pieces, p).result().value(), 0);
        assertThrows(IllegalArgumentException.class, () -> new Karger().run(Graph.builder(false).node(0, 0, 0, "A").build(), p));
        assertThrows(IllegalArgumentException.class,
                () -> new Karger().run(Graph.builder(true).node(0, 0, 0, "A").node(1, 80, 0, "B").edge(0, 0, 1, 1).build(), p));
        Trace t = new Karger().run(SAMPLE, p);
        assertEquals(SAMPLE.nodeCount() - 2 + 2, t.size());   // start, n - 2 contractions, final
    }

    // ---- shared ------------------------------------------------------------------------

    @Test
    public void everyNewAlgorithmHasPseudocodeAndLinesInRange() {
        for (Algorithm a : Algorithms.all()) {
            assertTrue(a.id(), a.pseudocode().size() >= 5);
            assertFalse(a.name().isBlank());
            assertTrue(a.category() != null);
        }
    }

    private static Graph complete(int n) {
        Graph.Builder b = Graph.builder(false);
        for (int i = 0; i < n; i++) {
            b.node(i, i * 70, (i % 2) * 70, String.valueOf((char) ('A' + i)));
        }
        int id = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                b.edge(id++, i, j, 1);
            }
        }
        return b.build();
    }

    private static Graph connectedRandom(Random random, int n) {
        while (true) {
            Graph g = randomGraph(random, n, false, false);
            boolean[] seen = new boolean[n];
            ArrayList<Integer> stack = new ArrayList<>(List.of(0));
            seen[0] = true;
            int count = 1;
            while (!stack.isEmpty()) {
                int u = stack.remove(stack.size() - 1);
                for (Edge e : g.edges()) {
                    int v = e.from() == u ? e.to() : e.to() == u ? e.from() : -1;
                    if (v >= 0 && !seen[v]) {
                        seen[v] = true;
                        count++;
                        stack.add(v);
                    }
                }
            }
            if (count == n) {
                return g;
            }
        }
    }

    /** Random simple graph with node ids 0..n-1; weights are small integers (negative ones only when {@code positive} is false and the graph is directed). */
    private static Graph randomGraph(Random random, int n, boolean directed, boolean positiveOnly) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(new Node(i, i * 70, (i % 3) * 70, String.valueOf((char) ('A' + i))));
        }
        List<Edge> edges = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        int id = 0;
        for (int tries = 0; tries < n * 2; tries++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            if (a == b) {
                continue;
            }
            long key = directed ? a * 100L + b : Math.min(a, b) * 100L + Math.max(a, b);
            if (!used.add(key)) {
                continue;
            }
            edges.add(new Edge(id++, a, b, 1 + random.nextInt(9)));
        }
        return new Graph(directed, nodes, edges);
    }
}

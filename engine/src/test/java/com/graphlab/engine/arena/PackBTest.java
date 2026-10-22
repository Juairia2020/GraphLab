package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** MS17 pack B: every answer is checked against an independent reference (not the engine algorithm that made it). */
public class PackBTest {
    private static final int SEEDS = 120;

    private static Graph graph(Question q) {
        return ((GraphInput) q.input()).graph();
    }

    private static int node(Graph g, String name) {
        for (int i = 0; i < g.nodeCount(); i++) {
            if (g.nodeName(i).equals(name)) {
                return i;
            }
        }
        throw new AssertionError("no node " + name);
    }

    @Test
    public void spotTheBugFindsTheOneWrongLine() {
        Pattern line = Pattern.compile("Line (\\d): settle (\\S+) with distance (\\d+)");
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.SPOT_BUG, level, seed);
                Graph g = graph(q);
                double[][] all = Reference.allPairs(g);
                Matcher m = line.matcher(q.prompt());
                List<Integer> wrong = new ArrayList<>();
                double previous = -1;
                int lines = 0;
                while (m.find()) {
                    lines++;
                    int n = node(g, m.group(2));
                    double shown = Double.parseDouble(m.group(3));
                    if (Math.abs(shown - all[0][n]) > 1e-9) {
                        wrong.add(Integer.parseInt(m.group(1)));
                    }
                }
                assertEquals(PackB.LOG_LINES, lines);
                assertEquals("seed " + seed, 1, wrong.size());
                assertEquals("Line " + wrong.get(0), q.options().get(q.correctIndex()));
                assertTrue(previous < 0);
            }
        }
    }

    @Test
    public void willItChangeMatchesAnIndependentRecomputation() {
        Pattern edge = Pattern.compile("The edge (\\S+)–(\\S+) now has weight (\\d+) instead of (\\d+)");
        int yes = 0;
        int no = 0;
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.WILL_CHANGE, level, seed);
                Graph g = graph(q);
                Matcher m = edge.matcher(q.prompt());
                assertTrue(m.find());
                int a = node(g, m.group(1));
                int b = node(g, m.group(2));
                double weight = Double.parseDouble(m.group(3));
                List<Edge> edges = new ArrayList<>();
                boolean found = false;
                for (Edge e : g.edges()) {
                    boolean same = (e.from() == a && e.to() == b) || (e.from() == b && e.to() == a);
                    if (same) {
                        assertEquals(Double.parseDouble(m.group(4)), e.weight(), 0);
                        found = true;
                    }
                    edges.add(same ? new Edge(e.id(), e.from(), e.to(), weight) : e);
                }
                assertTrue(found);
                Graph after = new Graph(false, g.nodes(), edges);
                boolean changes;
                if (q.source() >= 0) {
                    changes = Math.abs(Reference.allPairs(g)[q.source()][q.target()]
                            - Reference.allPairs(after)[q.source()][q.target()]) > 1e-9;
                } else {
                    changes = Math.abs(Reference.mstWeight(g) - Reference.mstWeight(after)) > 1e-9;
                }
                assertEquals("seed " + seed, changes ? "Yes, it changes" : "No, it stays the same",
                        q.options().get(q.correctIndex()));
                if (changes) {
                    yes++;
                } else {
                    no++;
                }
            }
        }
        assertTrue("yes " + yes + " no " + no, yes > 100 && no > 100);   // both answers are common
    }

    private static List<Integer> orderOf(Graph g, String text) {
        List<Integer> out = new ArrayList<>();
        for (String part : text.split(", ")) {
            out.add(node(g, part.trim()));
        }
        return out;
    }

    @Test
    public void reverseEngineerNamesTheAlgorithmThatMadeTheOrder() {
        Pattern shown = Pattern.compile("in this order: ([^.]+)\\. Ties");
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.REVERSE_ENGINEER, level, seed);
                Graph g = graph(q);
                Matcher m = shown.matcher(q.prompt());
                assertTrue(m.find());
                List<Integer> order = orderOf(g, m.group(1));
                List<Integer> bfs = Reference.bfs(g, 0);
                List<Integer> discovery = new ArrayList<>();
                Reference.dfs(g, 0, discovery, new ArrayList<>());
                // Dijkstra order: by true distance, which is unique for every node here
                double[] d = Reference.allPairs(g)[0];
                List<Integer> nearest = new ArrayList<>();
                for (int i = 0; i < g.nodeCount(); i++) {
                    nearest.add(i);
                }
                nearest.sort((x, y) -> Double.compare(d[x], d[y]));
                String expected = order.equals(bfs) ? "Breadth-first search"
                        : order.equals(discovery) ? "Depth-first search" : order.equals(nearest) ? "Dijkstra" : "?";
                assertEquals("seed " + seed, expected, q.options().get(q.correctIndex()));
                int matches = (order.equals(bfs) ? 1 : 0) + (order.equals(discovery) ? 1 : 0) + (order.equals(nearest) ? 1 : 0);
                assertEquals("exactly one algorithm makes the order", 1, matches);
            }
        }
    }

    @Test
    public void raceBetFollowsTheVisitingPositions() {
        int ties = 0;
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.RACE_BET, level, seed);
                Graph g = graph(q);
                List<Integer> bfs = Reference.bfs(g, 0);
                List<Integer> dfs = new ArrayList<>();
                Reference.dfs(g, 0, dfs, new ArrayList<>());
                int a = bfs.indexOf(q.target());
                int b = dfs.indexOf(q.target());
                String expected = a < b ? "Breadth-first search" : b < a ? "Depth-first search" : "A tie";
                assertEquals("seed " + seed, expected, q.options().get(q.correctIndex()));
                ties += a == b ? 1 : 0;
            }
        }
        assertTrue("ties are kept rare, got " + ties, ties < 120);
    }

    @Test
    public void cutTheNetworkPicksTheOnlyBiggestDrop() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.CUT_NETWORK, level, seed);
                Graph g = graph(q);
                int sink = q.target();
                double flow = Reference.maxFlow(g, 0, sink);
                double bestDrop = -1;
                double chosenDrop = -1;
                int bestCount = 0;
                for (Edge e : g.edges()) {
                    List<Edge> rest = new ArrayList<>();
                    for (Edge o : g.edges()) {
                        if (o.id() != e.id()) {
                            rest.add(o);
                        }
                    }
                    double drop = flow - Reference.maxFlow(new Graph(true, g.nodes(), rest), 0, sink);
                    String label = g.nodeName(e.from()) + " → " + g.nodeName(e.to()) + " ("
                            + Choices.number(e.weight()) + ")";
                    if (label.equals(q.options().get(q.correctIndex()))) {
                        chosenDrop = drop;
                    }
                    if (drop > bestDrop + 1e-9) {
                        bestDrop = drop;
                        bestCount = 1;
                    } else if (Math.abs(drop - bestDrop) < 1e-9) {
                        bestCount++;
                    }
                }
                assertTrue(bestDrop > 0);
                assertEquals("seed " + seed, bestDrop, chosenDrop, 1e-9);
                assertEquals("the best cut is the only best cut", 1, bestCount);
            }
        }
    }

    @Test
    public void complexityGuessMatchesTheCounts() {
        Pattern row = Pattern.compile("n = (\\d+): (\\d+)");
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.COMPLEXITY_GUESS, level, seed);
                List<int[]> rows = new ArrayList<>();
                Matcher m = row.matcher(q.prompt());
                while (m.find()) {
                    rows.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
                }
                assertEquals(4, rows.size());
                String right = q.options().get(q.correctIndex());
                String text = q.prompt();
                if (text.startsWith("Merge sort")) {
                    assertEquals("n log n", right);
                    for (int[] r : rows) {
                        double lg = Math.log(r[0]) / Math.log(2);
                        assertTrue(r[1] >= r[0] / 2.0 * lg - 1e-9 && r[1] <= r[0] * lg - r[0] + 1 + 1e-9);
                    }
                } else if (text.startsWith("Quick sort")) {
                    assertEquals("n²", right);
                    for (int[] r : rows) {
                        assertEquals(r[0] * (r[0] - 1) / 2, r[1]);   // pivot is always the largest: n(n-1)/2
                    }
                } else {
                    assertEquals("n", right);
                    for (int i = 1; i < rows.size(); i++) {
                        double ratio = rows.get(i)[1] / (double) rows.get(i - 1)[1];
                        assertTrue("ratio " + ratio, ratio > 1.7 && ratio < 2.3);
                    }
                }
            }
        }
    }

    @Test
    public void fitRejectsCountsThatFitTwoGrowthsAlike() {
        // Counts of exactly n squared fit n squared and nothing else.
        PackB.Counts square = new PackB.Counts("x", new int[] {2, 4, 8, 16}, new double[] {4, 16, 64, 256});
        assertEquals(2, PackB.bestGrowth(square));
        PackB.Counts linear = new PackB.Counts("x", new int[] {2, 4, 8, 16}, new double[] {2, 4, 8, 16});
        assertEquals(0, PackB.bestGrowth(linear));
        PackB.Counts nlogn = new PackB.Counts("x", new int[] {2, 4, 8, 16}, new double[] {2, 8, 24, 64});
        assertEquals(1, PackB.bestGrowth(nlogn));
        PackB.Counts doubling = new PackB.Counts("x", new int[] {2, 4, 8, 16}, new double[] {4, 16, 256, 65536});
        assertEquals(3, PackB.bestGrowth(doubling));
    }
}

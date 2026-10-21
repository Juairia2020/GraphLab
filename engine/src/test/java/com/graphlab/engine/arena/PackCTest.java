package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Priority 4 activities. Every answer is checked with code written separately from the generator. */
public class PackCTest {
    private static final int SEEDS = 150;

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

    /** Textbook Dijkstra with a priority scan; settled nodes are never reopened. */
    private static double dijkstra(Graph g, int s, int t) {
        int n = g.nodeCount();
        double[] d = new double[n];
        boolean[] done = new boolean[n];
        java.util.Arrays.fill(d, Double.POSITIVE_INFINITY);
        d[s] = 0;
        for (int k = 0; k < n; k++) {
            int u = -1;
            for (int v = 0; v < n; v++) {
                if (!done[v] && d[v] < Double.POSITIVE_INFINITY && (u == -1 || d[v] < d[u])) {
                    u = v;
                }
            }
            if (u == -1) {
                break;
            }
            done[u] = true;
            for (Edge e : g.edges()) {
                if (e.from() == u && !done[e.to()]) {
                    d[e.to()] = Math.min(d[e.to()], d[u] + e.weight());
                }
            }
        }
        return d[t];
    }

    private static Graph withWeight(Graph g, Edge target, double w) {
        List<Edge> edges = new ArrayList<>();
        for (Edge e : g.edges()) {
            edges.add(e.id() == target.id() ? new Edge(e.id(), e.from(), e.to(), w) : e);
        }
        return new Graph(g.isDirected(), g.nodes(), edges);
    }

    @Test
    public void breakItOffersExactlyOneChangeThatBreaksDijkstra() {
        Pattern label = Pattern.compile("(\\S+) → (\\S+) becomes (-\\d+)");
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.BREAK_IT, level, seed);
                Graph g = graph(q);
                int s = q.source();
                int t = q.target();
                assertEquals(4, q.options().size());
                int breaking = 0;
                for (int i = 0; i < q.options().size(); i++) {
                    Matcher m = label.matcher(q.options().get(i));
                    assertTrue(q.options().get(i), m.matches());
                    Edge edge = null;
                    for (Edge e : g.edges()) {
                        if (e.from() == node(g, m.group(1)) && e.to() == node(g, m.group(2))) {
                            edge = e;
                        }
                    }
                    assertTrue("the edge exists", edge != null);
                    Graph changed = withWeight(g, edge, Double.parseDouble(m.group(3)));
                    assertFalse("no negative cycle", Reference.negativeCycleFrom(changed, s));
                    double truth = Reference.allPairs(changed)[s][t];
                    boolean breaks = Math.abs(dijkstra(changed, s, t) - truth) > 1e-9;
                    if (breaks) {
                        breaking++;
                    }
                    assertEquals("option " + i + " seed " + seed, i == q.correctIndex(), breaks);
                }
                assertEquals(1, breaking);
            }
        }
    }

    @Test
    public void fogWalkerCostMatchesAnIndependentWalk() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = QuestionFactory.create(QuestionKind.FOG_OF_WAR, level, seed);
                Graph g = graph(q);
                int at = q.source();
                int t = q.target();
                boolean[] seen = new boolean[g.nodeCount()];
                seen[at] = true;
                double cost = 0;
                while (at != t) {
                    double bestW = Double.POSITIVE_INFINITY;
                    int bestTo = -1;
                    for (Edge e : g.edges()) {
                        int o = e.from() == at ? e.to() : e.to() == at ? e.from() : -1;
                        if (o >= 0 && !seen[o] && e.weight() < bestW) {
                            bestW = e.weight();
                            bestTo = o;
                        }
                    }
                    assertTrue("walker is never stuck", bestTo >= 0);
                    cost += bestW;
                    seen[bestTo] = true;
                    at = bestTo;
                }
                double optimal = Reference.allPairs(g)[q.source()][t];
                assertTrue("the greedy trip costs more than the best trip, seed " + seed, cost > optimal + 1e-9);
                assertEquals(Choices.number(cost), q.options().get(q.correctIndex()));
                assertTrue("the best-trip cost is offered as a wrong answer", q.options().contains(Choices.number(optimal)));
                assertTrue(q.explanation().contains(Choices.number(optimal)));
            }
        }
    }

    @Test
    public void newKindsAreCardFreeChoicesWithUnlockRules() {
        for (QuestionKind kind : new QuestionKind[] {QuestionKind.BREAK_IT, QuestionKind.FOG_OF_WAR}) {
            assertEquals(Interaction.CHOICE, kind.interaction());
            assertTrue(kind.fromCards());
            assertEquals(kind, QuestionKind.byId(kind.id()));
        }
        assertTrue(UnlockGraph.bundled().requirements(QuestionKind.BREAK_IT).contains(QuestionKind.BELLMAN_DISTANCE));
        assertTrue(UnlockGraph.bundled().requirements(QuestionKind.FOG_OF_WAR).contains(QuestionKind.DIJKSTRA_DISTANCE));
    }
}

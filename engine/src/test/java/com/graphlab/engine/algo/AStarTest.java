package com.graphlab.engine.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.graph.AStar;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class AStarTest {
    private static final double EPS = 1e-9;

    /** Random graph on a grid of positions; weights are the straight length times a factor, so the guide is safe when factor >= 1. */
    private static Graph random(long seed, boolean directed, double minFactor, double maxFactor) {
        Random random = new Random(seed);
        int n = 4 + random.nextInt(10);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(new Node(i, random.nextInt(300), random.nextInt(300), "N" + i));
        }
        List<Edge> edges = new ArrayList<>();
        int id = 0;
        for (int i = 0; i < n; i++) {
            for (int j = directed ? 0 : i + 1; j < n; j++) {
                if (i != j && random.nextInt(100) < 40) {
                    double len = Math.hypot(nodes.get(i).x() - nodes.get(j).x(), nodes.get(i).y() - nodes.get(j).y());
                    double w = Math.max(1, Math.ceil(len * (minFactor + random.nextDouble() * (maxFactor - minFactor))));
                    edges.add(new Edge(id++, i, j, w));
                }
            }
        }
        return new Graph(directed, nodes, edges);
    }

    private static double dijkstra(Graph g, int s, int t) {
        return com.graphlab.engine.algo.Algorithms.byId("dijkstra").run(g, new Params(s, 0L, t)).result().distances().get(t);
    }

    @Test
    public void equalsDijkstraWhenEveryEdgeIsAtLeastItsStraightLine() {
        for (int seed = 0; seed < 300; seed++) {
            for (boolean directed : new boolean[] {false, true}) {
                Graph g = random(seed, directed, 1.0, 2.0);
                assertTrue(AStar.admissible(g));
                int s = seed % g.nodeCount();
                int t = (seed * 7 + 3) % g.nodeCount();
                Trace trace = new AStar().run(g, new Params(s, 0L, t));
                double want = dijkstra(g, s, t);
                double got = trace.result().distances().get(t);
                if (Double.isInfinite(want)) {
                    assertTrue(Double.isInfinite(got));
                } else {
                    assertEquals("seed " + seed, want, got, EPS);
                    assertEquals(want, trace.result().value(), EPS);
                }
            }
        }
    }

    @Test
    public void staysCorrectWhenSomeEdgesAreShorterThanTheStraightLineAndSaysSo() {
        int warned = 0;
        for (int seed = 0; seed < 300; seed++) {
            Graph g = random(seed, false, 0.3, 1.5);
            int s = seed % g.nodeCount();
            int t = (seed * 5 + 1) % g.nodeCount();
            Trace trace = new AStar().run(g, new Params(s, 0L, t));
            double want = dijkstra(g, s, t);
            double got = trace.result().distances().get(t);
            if (Double.isInfinite(want)) {
                assertTrue(Double.isInfinite(got));
            } else {
                assertEquals("seed " + seed, want, got, EPS);
            }
            if (!AStar.admissible(g)) {
                warned++;
                assertTrue(trace.result().text().contains("Warning"));
                assertTrue(trace.step(0).message().contains("Warning"));
                assertTrue(AStar.safeScale(g) < 1.0);
            }
        }
        assertTrue("some graphs break the plain guide", warned > 100);
    }

    @Test
    public void aGoodGuideSettlesFewerNodesThanDijkstraOnAStraightRoad() {
        // a long straight road with a dead-end branch behind the start: A* never goes back
        List<Node> nodes = List.of(new Node(0, 0, 0, "S"), new Node(1, 100, 0, "A"), new Node(2, 200, 0, "B"),
                new Node(3, 300, 0, "T"), new Node(4, -100, 0, "X"), new Node(5, -200, 0, "Y"));
        List<Edge> edges = List.of(new Edge(0, 0, 1, 100), new Edge(1, 1, 2, 100), new Edge(2, 2, 3, 100),
                new Edge(3, 0, 4, 100), new Edge(4, 4, 5, 100));
        Graph g = new Graph(false, nodes, edges);
        Trace a = new AStar().run(g, new Params(0, 0L, 3));
        assertEquals(300.0, a.result().value(), EPS);
        assertEquals(List.of(0, 1, 2, 3), a.result().visitOrder());
        Trace d = com.graphlab.engine.algo.Algorithms.byId("dijkstra").run(g, new Params(0, 0L, 3));
        assertTrue(d.result().visitOrder().size() > a.result().visitOrder().size());
    }

    @Test
    public void reportsAnUnreachableEndAndIsDeterministic() {
        Graph g = new Graph(true, List.of(new Node(0, 0, 0, "A"), new Node(1, 10, 0, "B"), new Node(2, 20, 0, "C")),
                List.of(new Edge(0, 0, 1, 10)));
        Trace t = new AStar().run(g, new Params(0, 0L, 2));
        assertTrue(Double.isInfinite(t.result().distances().get(2)));
        assertTrue(t.result().text().contains("cannot be reached"));
        assertEquals(t, new AStar().run(g, new Params(0, 0L, 2)));
    }

    @Test
    public void refusesMissingEndNegativeWeightsAndBadNodes() {
        Graph g = random(1, false, 1, 2);
        assertRefused(g, new Params(0, 0L));
        assertRefused(g, new Params(0, 0L, 99));
        Graph neg = new Graph(true, List.of(new Node(0, 0, 0, "A"), new Node(1, 10, 0, "B")), List.of(new Edge(0, 0, 1, -1)));
        assertRefused(neg, new Params(0, 0L, 1));
    }

    private static void assertRefused(Graph g, Params p) {
        try {
            new AStar().run(g, p);
            org.junit.Assert.fail("should be refused");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
    }
}

package com.graphlab.engine.dynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class DynamicGraphTest {
    private static final double EPS = 1e-9;

    private static double[][] floyd(int n, List<DynamicGraph.Edge> edges) {
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
            }
        }
        for (DynamicGraph.Edge e : edges) {
            d[e.u()][e.v()] = Math.min(d[e.u()][e.v()], e.weight());
            d[e.v()][e.u()] = Math.min(d[e.v()][e.u()], e.weight());
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
                }
            }
        }
        return d;
    }

    @Test
    public void distancesStayEqualToAFullFloydWarshallAfterEveryEvent() {
        for (int seed = 0; seed < 300; seed++) {
            Random random = new Random(seed);
            int n = 2 + random.nextInt(7);
            DynamicGraph g = new DynamicGraph(n);
            List<Integer> alive = new ArrayList<>();
            for (int step = 0; step < 25; step++) {
                int kind = random.nextInt(10);
                if (kind < 6 || alive.isEmpty()) {
                    int u = random.nextInt(n);
                    int v = random.nextInt(n);
                    if (u != v) {
                        alive.add(g.addEdge(u, v, 1 + random.nextInt(20)));
                    }
                } else if (kind < 8) {
                    g.removeEdge(alive.remove(random.nextInt(alive.size())));
                } else {
                    g.setWeight(alive.get(random.nextInt(alive.size())), 1 + random.nextInt(20));
                }
                double[][] want = floyd(n, g.liveEdges());
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        assertEquals("seed " + seed + " step " + step + " " + i + "->" + j, want[i][j], g.distance(i, j), EPS);
                    }
                }
            }
        }
    }

    @Test
    public void connectivityFollowsInsertsAndRemovals() {
        DynamicGraph g = new DynamicGraph(4);
        assertEquals(4, g.components());
        int a = g.addEdge(0, 1, 5);
        g.addEdge(1, 2, 3);
        assertTrue(g.connected(0, 2));
        assertFalse(g.connected(0, 3));
        g.removeEdge(a);
        assertFalse(g.connected(0, 2));
        assertEquals(3, g.components());
    }

    @Test
    public void aNewCheaperEdgeSwapsOutTheHeaviestEdgeOnTheCycle() {
        DynamicGraph g = new DynamicGraph(3);
        g.addEdge(0, 1, 4);
        int heavy = g.addEdge(1, 2, 9);
        assertEquals(13, g.mstWeight(), EPS);
        g.addEdge(0, 2, 2);
        assertEquals(6, g.mstWeight(), EPS);
        assertFalse(g.mstEdgeIds().contains(heavy));
    }

    @Test
    public void removingATreeEdgeBringsInTheCheapestReplacement() {
        DynamicGraph g = new DynamicGraph(3);
        int a = g.addEdge(0, 1, 1);
        g.addEdge(1, 2, 2);
        g.addEdge(0, 2, 7);
        g.removeEdge(a);
        assertEquals(9, g.mstWeight(), EPS);
        assertEquals(1, g.components());
    }

    @Test
    public void incrementalWorkIsFarBelowRecomputeWork() {
        int n = 40;
        DynamicGraph g = new DynamicGraph(n);
        Random random = new Random(3);
        for (int i = 0; i < 300; i++) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            if (u != v) {
                g.addEdge(u, v, 1 + random.nextInt(100));
            }
        }
        DynamicGraph.Counters c = g.counters();
        assertTrue("mst " + c.mstUpdateWork() + " vs " + c.mstRecomputeWork(), c.mstUpdateWork() * 5 < c.mstRecomputeWork());
        assertTrue("dist " + c.distUpdateWork() + " vs " + c.distRecomputeWork(), c.distUpdateWork() * 5 < c.distRecomputeWork());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aSelfLoopIsRefused() {
        new DynamicGraph(3).addEdge(1, 1, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeWeightIsRefused() {
        new DynamicGraph(3).addEdge(0, 1, -2);
    }

    @Test
    public void theTimelineSliderGivesTheSameGraphForwardAndBackward() {
        List<DynamicEvent> events = List.of(DynamicEvent.add(0, 0, 1, 4), DynamicEvent.add(1, 1, 2, 9),
                DynamicEvent.add(2, 0, 2, 2), DynamicEvent.setWeight(3, 0, 1), DynamicEvent.remove(4, 2));
        Timeline tl = new Timeline(3, events);
        assertEquals(0, tl.at(0).mstWeight(), EPS);
        assertEquals(13, tl.at(2).mstWeight(), EPS);
        assertEquals(6, tl.at(3).mstWeight(), EPS);
        assertEquals(3, tl.at(4).mstWeight(), EPS);    // edge 0 now weighs 1, edge 2 weighs 2
        assertEquals(10, tl.at(5).mstWeight(), EPS);   // edge 2 removed: 1 + 9
        assertEquals(6, tl.at(3).mstWeight(), EPS);    // back again
    }

    @Test
    public void theTimelineSortsByTimeAndRefusesDamagedEvents() {
        Timeline tl = new Timeline(3, List.of(DynamicEvent.add(5, 0, 1, 1), DynamicEvent.add(1, 1, 2, 1)));
        assertEquals(1, tl.events().get(0).t());
        try {
            new Timeline(3, List.of(new DynamicEvent(0, DynamicEvent.Type.ADD_EDGE, "0 x 3")));
            org.junit.Assert.fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("damaged"));
        }
        try {
            new Timeline(3, List.of(DynamicEvent.remove(0, 5)));
            org.junit.Assert.fail();
        } catch (IllegalArgumentException expected) {
            // no such edge
        }
    }
}

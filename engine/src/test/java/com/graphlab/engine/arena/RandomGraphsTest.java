package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class RandomGraphsTest {

    @Test
    public void graphsAreConnectedSimpleAndInCanonicalOrder() {
        for (boolean directed : new boolean[] {false, true}) {
            for (long seed = 0; seed < 200; seed++) {
                Random r = new Random(seed);
                int n = 4 + r.nextInt(6);
                Graph g = RandomGraphs.connected(r, n, r.nextInt(8), directed, false, 1, 9, false);
                assertEquals(n, g.nodeCount());
                assertTrue(g.edgeCount() >= n - 1);
                Set<String> pairs = new HashSet<>();
                int previousFrom = -1;
                int previousTo = -1;
                for (Edge e : g.edges()) {
                    assertTrue("no self-loop", e.from() != e.to());
                    if (!directed) {
                        assertTrue("smaller node first", e.from() < e.to());
                    }
                    assertTrue("no parallel edge", pairs.add(e.from() + ">" + e.to()));
                    assertTrue("sorted by (from, to)", e.from() > previousFrom || e.from() == previousFrom && e.to() > previousTo);
                    previousFrom = e.from();
                    previousTo = e.to();
                }
                // reachable from node 0 (directed: along the arrows, thanks to the tree edges)
                assertEquals("reaches everything from A", n, Reference.bfs(g, 0).size());
            }
        }
    }

    @Test
    public void everyNodeListsItsNeighboursAlphabetically() {
        for (boolean directed : new boolean[] {false, true}) {
            for (long seed = 0; seed < 200; seed++) {
                Random r = new Random(seed);
                Graph g = RandomGraphs.connected(r, 5 + r.nextInt(5), 3, directed, false, 1, 9, false);
                for (int u = 0; u < g.nodeCount(); u++) {
                    int last = -1;
                    for (Arc arc : g.arcsFrom(u)) {
                        assertTrue("arcs of " + u + " in ascending order", arc.to() > last);
                        last = arc.to();
                    }
                }
            }
        }
    }

    @Test
    public void forwardOnlyGraphsHaveNoCycle() {
        for (long seed = 0; seed < 200; seed++) {
            Random r = new Random(seed);
            Graph g = RandomGraphs.connected(r, 6 + r.nextInt(4), 5, true, true, -3, 9, false);
            for (Edge e : g.edges()) {
                assertTrue(e.from() < e.to());
            }
        }
    }

    @Test
    public void distinctWeightsAreAllDifferent() {
        for (long seed = 0; seed < 200; seed++) {
            Random r = new Random(seed);
            Graph g = RandomGraphs.connected(r, 9, 6, false, false, 1, 20, true);
            Set<Double> weights = new HashSet<>();
            for (Edge e : g.edges()) {
                assertTrue(weights.add(e.weight()));
            }
        }
    }

    @Test
    public void sameSeedSameGraph() {
        Graph a = RandomGraphs.connected(new Random(42), 7, 3, false, false, 1, 9, false);
        Graph b = RandomGraphs.connected(new Random(42), 7, 3, false, false, 1, 9, false);
        assertEquals(a, b);
    }

    @Test
    public void nodesAreLabelledFromA() {
        Graph g = RandomGraphs.connected(new Random(1), 5, 1, false, false, 1, 9, false);
        List<String> labels = g.nodes().stream().map(n -> n.label()).toList();
        assertEquals(List.of("A", "B", "C", "D", "E"), labels);
    }
}

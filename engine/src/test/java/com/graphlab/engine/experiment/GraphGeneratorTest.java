package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class GraphGeneratorTest {
    @Test
    public void sameNumbersAndSeedGiveTheSameGraph() {
        Graph a = GraphGenerator.generate(12, 40, 1, 20, 7L).graph();
        Graph b = GraphGenerator.generate(12, 40, 1, 20, 7L).graph();
        assertEquals(a.edges(), b.edges());
        assertEquals(a.nodes(), b.nodes());
    }

    @Test
    public void differentSeedsUsuallyDiffer() {
        Graph a = GraphGenerator.generate(12, 40, 1, 20, 1L).graph();
        Graph b = GraphGenerator.generate(12, 40, 1, 20, 2L).graph();
        assertFalse(a.edges().equals(b.edges()));
    }

    @Test
    public void edgeCountFollowsDensityButNeverBelowATree() {
        assertEquals(9, GraphGenerator.edgeCount(10, 0));
        assertEquals(45, GraphGenerator.edgeCount(10, 100));
        assertEquals(23, GraphGenerator.edgeCount(10, 50));   // 45 pairs, half rounded
        assertEquals(10, GraphGenerator.generate(10, 0, 1, 5, 3L).graph().edgeCount() + 1);
    }

    @Test
    public void graphIsConnectedWithNoLoopsOrRepeatedPairs() {
        for (int density : new int[] {0, 10, 50, 100}) {
            Graph g = GraphGenerator.generate(15, density, 1, 9, density).graph();
            Set<Long> pairs = new HashSet<>();
            for (Edge e : g.edges()) {
                assertTrue(e.from() != e.to());
                assertTrue(pairs.add((long) Math.min(e.from(), e.to()) * 100 + Math.max(e.from(), e.to())));
            }
            int reached = Algorithms.byId("bfs").run(g, Params.from(0)).result().visitOrder().size();
            assertEquals(15, reached);
            assertFalse(g.isDirected());
        }
    }

    @Test
    public void weightsStayInRange() {
        Graph g = GraphGenerator.generate(20, 60, 5, 8, 11L).graph();
        for (Edge e : g.edges()) {
            assertTrue(e.weight() >= 5 && e.weight() <= 8);
        }
        Graph same = GraphGenerator.generate(6, 100, 4, 4, 1L).graph();
        for (Edge e : same.edges()) {
            assertEquals(4.0, e.weight(), 0);
        }
    }

    @Test
    public void badNumbersAreRefusedWithAMessage() {
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(1, 50, 1, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(Graph.MAX_NODES + 1, 50, 1, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(5, 101, 1, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(5, -1, 1, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(5, 50, 0, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(5, 50, 6, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> GraphGenerator.generate(5, 50, 1, 5000, 1L));
    }

    @Test
    public void largestGraphIsAccepted() {
        GraphDocument d = GraphGenerator.generate(Graph.MAX_NODES, 100, 1, 1000, 5L);
        assertEquals(Graph.MAX_NODES, d.graph().nodeCount());
    }

    @Test
    public void kargerRunsOnAGeneratedGraph() {
        Graph g = GraphGenerator.generate(8, 40, 1, 9, 3L).graph();
        assertTrue(Algorithms.byId("karger").run(g, Params.none().withSeed(1L)).size() > 1);
    }
}

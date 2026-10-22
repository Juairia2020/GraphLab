package com.graphlab.engine.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.compare.Comparison.Agreement;
import com.graphlab.engine.compare.Comparison.Report;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Algorithm;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ComparisonTest {

    private static Graph line(boolean directed, double... weights) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i <= weights.length; i++) {
            nodes.add(new Node(i, i * 80.0, 0, String.valueOf((char) ('A' + i))));
        }
        for (int i = 0; i < weights.length; i++) {
            edges.add(new Edge(i, i, i + 1, weights[i]));
        }
        return new Graph(directed, nodes, edges);
    }

    @Test
    public void candidatesAreGraphAlgorithmsWithoutAnEndNode() {
        List<String> ids = new ArrayList<>();
        for (Algorithm a : Comparison.candidates()) {
            ids.add(a.id());
            assertFalse(a.needsTarget());
        }
        assertTrue(ids.contains("dijkstra"));
        assertTrue(ids.contains("kruskal"));
        assertFalse(ids.contains("edmonds-karp"));
        assertFalse(ids.contains("merge-sort"));
        assertFalse(ids.contains("knapsack-01"));
    }

    @Test
    public void dijkstraAndBellmanFordAgreeOnTheSampleGraph() {
        Report r = Comparison.run(SampleGraph.sixNodeUndirected(), "dijkstra", "bellman-ford", SampleGraph.A);
        assertTrue(r.a().ran() && r.b().ran());
        assertEquals(Agreement.SAME_ANSWER, r.agreement());
        assertEquals(6, r.a().reached());
        assertTrue(r.a().steps() > 0 && r.b().steps() > 0);
    }

    @Test
    public void kruskalAndPrimFindTheSameTotalWeight() {
        Report r = Comparison.run(SampleGraph.sixNodeUndirected(), "kruskal", "prim", SampleGraph.A);
        assertEquals(Agreement.SAME_ANSWER, r.agreement());
        assertEquals(r.a().totalWeight(), r.b().totalWeight(), 1e-9);
        assertEquals(5, r.a().treeEdges());
        assertEquals(5, r.b().treeEdges());
    }

    @Test
    public void dijkstraRefusesANegativeEdgeAndBellmanFordStillRuns() {
        Graph g = line(true, 4, -2, 3);
        Report r = Comparison.run(g, "dijkstra", "bellman-ford", 0);
        assertFalse(r.a().ran());
        assertTrue(r.a().error(), r.a().error().length() > 5);
        assertTrue(r.b().ran());
        assertEquals(Agreement.NOT_COMPARABLE, r.agreement());
        assertNull(r.fewerSteps());
        assertEquals(0, r.a().steps());
    }

    @Test
    public void breadthFirstAndDepthFirstAreNotComparableByAnswer() {
        Report r = Comparison.run(SampleGraph.sixNodeUndirected(), "bfs", "dfs", 0);
        assertEquals(Agreement.NOT_COMPARABLE, r.agreement());
        assertEquals(6, r.a().reached());
        assertEquals(6, r.b().reached());
    }

    @Test
    public void aSpanningTreeAlgorithmRefusesADirectedGraphOnItsOwnSide() {
        Graph g = line(true, 1, 2);
        Report r = Comparison.run(g, "kruskal", "bfs", 0);
        assertFalse(r.a().ran());
        assertTrue(r.b().ran());
    }

    @Test
    public void fewerStepsNamesTheShorterRun() {
        Report r = Comparison.run(SampleGraph.sixNodeUndirected(), "bfs", "bellman-ford", 0);
        if (r.a().steps() == r.b().steps()) {
            assertNull(r.fewerSteps());
        } else {
            assertEquals(r.a().steps() < r.b().steps() ? "bfs" : "bellman-ford", r.fewerSteps().id());
        }
    }

    @Test
    public void countersComeFromTheLastStep() {
        Report r = Comparison.run(SampleGraph.sixNodeUndirected(), "dijkstra", "prim", 0);
        assertFalse(r.a().counters().isEmpty());
    }

    @Test
    public void sameAlgorithmTwiceIsRefused() {
        try {
            Comparison.run(SampleGraph.sixNodeUndirected(), "bfs", "bfs", 0);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("different"));
        }
    }

    @Test
    public void unknownAndUnsuitableIdsAreRefused() {
        for (String bad : new String[] {"nope", "merge-sort", "edmonds-karp"}) {
            try {
                Comparison.run(SampleGraph.sixNodeUndirected(), "bfs", bad, 0);
                org.junit.Assert.fail(bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void everyPairOfCandidatesRunsOnTheSampleGraphWithoutCrashing() {
        List<Algorithm> all = Comparison.candidates();
        for (Algorithm a : all) {
            for (Algorithm b : all) {
                if (a != b) {
                    Report r = Comparison.run(SampleGraph.sixNodeUndirected(), a.id(), b.id(), 0);
                    assertEquals(a.id(), r.a().id());
                    assertEquals(b.id(), r.b().id());
                }
            }
        }
    }

    @Test
    public void unreachedNodesMakeDistancesDifferOnlyIfBothAgreeOnWhichAreUnreached() {
        List<Node> nodes = List.of(new Node(0, 0, 0, "A"), new Node(1, 80, 0, "B"), new Node(2, 160, 0, "C"));
        Graph g = new Graph(false, nodes, List.of(new Edge(0, 0, 1, 2)));
        Report r = Comparison.run(g, "dijkstra", "bellman-ford", 0);
        assertEquals(Agreement.SAME_ANSWER, r.agreement());
        assertEquals(2, r.a().reached());
    }

    @Test
    public void aNegativeCycleMakesBellmanFordNotComparable() {
        Graph g = line(true, 1, -5);
        // close the cycle: C -> A with -1 gives a negative total
        List<Edge> edges = new ArrayList<>(g.edges());
        edges.add(new Edge(2, 2, 0, -1));
        Graph cyc = new Graph(true, g.nodes(), edges);
        Report r = Comparison.run(cyc, "bellman-ford", "bfs", 0);
        assertTrue(r.a().ran());
        assertEquals(Agreement.NOT_COMPARABLE, r.agreement());
    }

    @Test
    public void comparableAreOnlyAlgorithmsWithAVerdict() {
        List<String> ids = new ArrayList<>();
        for (Algorithm a : Comparison.comparable()) {
            ids.add(a.id());
        }
        assertTrue(ids.containsAll(List.of("dijkstra", "bellman-ford", "kruskal", "prim")));
        assertFalse(ids.contains("bfs"));
        assertFalse(ids.contains("dfs"));
        for (Algorithm a : Comparison.comparable()) {
            for (Algorithm b : Comparison.comparable()) {
                if (a != b && a.category() == b.category()) {
                    assertTrue(Comparison.run(SampleGraph.sixNodeUndirected(), a.id(), b.id(), 0).agreement() != Agreement.NOT_COMPARABLE);
                }
            }
        }
    }
}

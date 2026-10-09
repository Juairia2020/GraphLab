package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.TestGraphs;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class BellmanFordTest {
    private final BellmanFord bf = new BellmanFord();

    @Test
    public void agreesWithDijkstraOnTheSampleGraph() {
        Graph g = SampleGraph.sixNodeUndirected();
        Result expected = new Dijkstra().run(g, Params.from(SampleGraph.A)).result();
        Result actual = bf.run(g, Params.from(SampleGraph.A)).result();
        assertEquals(expected.distances(), actual.distances());
        assertFalse(actual.negativeCycle());
    }

    @Test
    public void negativeEdgeWithoutCycleGivesTheCorrectDistances() {
        // A->B 4, A->C 5, B->C -2: C is cheaper through B (2)
        Graph g = Graph.builder(true).nodes(3).edge(0, 1, 4).edge(0, 2, 5).edge(1, 2, -2).build();
        Trace t = bf.run(g, Params.from(0));
        assertFalse(t.result().negativeCycle());
        assertEquals(2.0, t.result().distances().get(2), 1e-9);
        assertEquals(List.of(0, 1, 2), t.result().pathTo(g, 2));
    }

    @Test
    public void detectsADirectedNegativeCycle() {
        // A->B 1, B->C -3, C->A 1: the loop costs -1 each time round
        Graph g = Graph.builder(true).nodes(3).edge(0, 1, 1).edge(1, 2, -3).edge(2, 0, 1).build();
        Trace t = bf.run(g, Params.from(0));
        assertTrue(t.result().negativeCycle());
        assertTrue(t.result().distances().isEmpty());
        assertTrue(t.result().treeEdgeIds().isEmpty());
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(1, TestGraphs.edgesIn(last, State.FAIL).size());
        assertTrue(TestGraphs.edgesIn(last, State.PATH).isEmpty());
    }

    @Test
    public void anUndirectedNegativeEdgeIsANegativeCycle() {
        Graph g = Graph.builder(false).nodes(2).edge(0, 1, -2).build();
        assertTrue(bf.run(g, Params.from(0)).result().negativeCycle());
    }

    @Test
    public void ignoresANegativeCycleThatCannotBeReached() {
        // A->B is fine; the C<->D loop is negative but unreachable from A
        Graph g = Graph.builder(true).nodes(4).edge(0, 1, 1).edge(2, 3, -5).edge(3, 2, 1).build();
        Result r = bf.run(g, Params.from(0)).result();
        assertFalse(r.negativeCycle());
        assertEquals(Double.POSITIVE_INFINITY, r.distances().get(2), 0);
    }

    @Test
    public void stopsEarlyWhenAPassChangesNothing() {
        Graph g = TestGraphs.chain(6, 1, true);   // edge order matches travel order: one pass is enough
        Trace t = bf.run(g, Params.from(0));
        assertEquals(2L, (long) t.last().counters().get("pass"));   // pass 1 did the work, pass 2 changed nothing
        assertTrue(t.steps().stream().anyMatch(s -> s.message().contains("stop early")));
    }

    @Test
    public void reachedNodesAreVisitedAtTheEnd() {
        Trace t = bf.run(TestGraphs.disconnected(), Params.from(0));
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(Set.of(0, 1, 2), TestGraphs.nodesIn(last, State.VISITED));
        assertEquals(List.of(0, 1, 2).size(), t.result().visitOrder().size());
    }

    @Test
    public void singleNodeGraphHasNoNegativeCycle() {
        Trace t = bf.run(TestGraphs.singleNode(), Params.from(0));
        assertFalse(t.result().negativeCycle());
        assertEquals(0.0, t.result().distances().get(0), 1e-9);
    }

    @Test
    public void needsAValidStartNode() {
        Graph g = SampleGraph.sixNodeUndirected();
        assertThrows(IllegalArgumentException.class, () -> bf.run(g, Params.none()));
        assertThrows(IllegalArgumentException.class, () -> bf.run(g, Params.from(42)));
    }
}

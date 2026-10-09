package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.TestGraphs;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DijkstraTest {
    private final Dijkstra dijkstra = new Dijkstra();

    @Test
    public void shortestDistancesOnSampleGraph() {
        Result r = dijkstra.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(0.0, r.distances().get(SampleGraph.A), 1e-9);
        assertEquals(7.0, r.distances().get(SampleGraph.B), 1e-9);
        assertEquals(9.0, r.distances().get(SampleGraph.C), 1e-9);
        assertEquals(11.0, r.distances().get(SampleGraph.F), 1e-9);
        assertEquals(20.0, r.distances().get(SampleGraph.D), 1e-9);
        assertEquals(20.0, r.distances().get(SampleGraph.E), 1e-9);
    }

    @Test
    public void shortestPathToEGoesThroughCAndF() {
        Graph g = SampleGraph.sixNodeUndirected();
        Result r = dijkstra.run(g, Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 2, 5, 4), r.pathTo(g, SampleGraph.E));   // A C F E
    }

    @Test
    public void settleOrderBreaksTiesByLowerNodeId() {
        Result r = dijkstra.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 1, 2, 5, 3, 4), r.visitOrder());   // D and E tie at 20, D first
    }

    @Test
    public void treeIsTheShortestPathTree() {
        Result r = dijkstra.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(Set.of(0, 1, 5, 6, 8), Set.copyOf(r.treeEdgeIds()));   // AB AC CD CF EF
        assertEquals(38.0, r.totalWeight(), 1e-9);
    }

    @Test
    public void everyNodeStartsLabelledInfinityExceptTheSource() {
        Trace t = dijkstra.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        GraphFrame first = (GraphFrame) t.step(0).frame();
        assertEquals("0", first.nodeLabel(SampleGraph.A));
        assertEquals(Labels.INFINITY, first.nodeLabel(SampleGraph.E));
    }

    @Test
    public void betterRouteReplacesTheOldParentEdge() {
        // A-B 10 directly, but A-C 1 then C-B 2 = 3 is better.
        Graph g = Graph.builder(false).nodes(3).edge(0, 1, 10).edge(0, 2, 1).edge(2, 1, 2).build();
        Trace t = dijkstra.run(g, Params.from(0));
        assertEquals(3.0, t.result().distances().get(1), 1e-9);
        assertEquals(Set.of(1, 2), Set.copyOf(t.result().treeEdgeIds()));
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(Set.of(1, 2), TestGraphs.edgesIn(last, State.PATH));
        assertEquals(State.IDLE, last.edgeState(0));
    }

    @Test
    public void parallelEdgesUseTheLighterOne() {
        Result r = dijkstra.run(TestGraphs.awkward(), Params.from(0)).result();
        assertEquals(2.0, r.distances().get(1), 1e-9);
        assertEquals(2.0, r.distances().get(2), 1e-9);   // zero-weight B-C
        assertEquals(6.0, r.distances().get(3), 1e-9);
    }

    @Test
    public void unreachableNodesKeepInfiniteDistance() {
        Trace t = dijkstra.run(TestGraphs.disconnected(), Params.from(0));
        assertEquals(Double.POSITIVE_INFINITY, t.result().distances().get(4), 0);
        assertEquals(Labels.INFINITY, TestGraphs.lastFrame(t).nodeLabel(4));
        assertEquals(State.IDLE, TestGraphs.lastFrame(t).nodeState(4));
    }

    @Test
    public void directedGraphIsAsymmetric() {
        Graph g = Graph.builder(true).nodes(2).edge(0, 1, 5).build();
        assertEquals(5.0, dijkstra.run(g, Params.from(0)).result().distances().get(1), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY,
                dijkstra.run(g, Params.from(1)).result().distances().get(0), 0);
    }

    @Test
    public void negativeWeightIsRejectedWithAHelpfulMessage() {
        Graph g = Graph.builder(true).nodes(2).edge(0, 1, -2).build();
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> dijkstra.run(g, Params.from(0)));
        assertTrue(e.getMessage().contains("Bellman-Ford"));
    }

    @Test
    public void needsAValidStartNode() {
        Graph g = SampleGraph.sixNodeUndirected();
        assertThrows(IllegalArgumentException.class, () -> dijkstra.run(g, Params.none()));
        assertThrows(IllegalArgumentException.class, () -> dijkstra.run(g, Params.from(42)));
    }

    @Test
    public void relaxedCounterCountsImprovements() {
        Trace t = dijkstra.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        // improvements: B7 C9 F14 | D22 | D20 | F11 | E20 (via F) ... counted by the algorithm itself
        long relaxed = t.last().counters().get("relaxed");
        long improvements = t.steps().stream().filter(s -> s.line() == 7).count();
        assertEquals(improvements, relaxed);
        assertEquals(6L, (long) t.last().counters().get("settled"));
    }

    @Test
    public void equalDistancesSettleTheLowerNodeIdFirstEvenWhenQueuedLater() {
        // From A the edge to node 2 comes first in edge order, but node 1 has the lower id
        Graph g = Graph.builder(false).nodes(3).edge(0, 2, 5).edge(0, 1, 5).build();
        assertEquals(List.of(0, 1, 2), dijkstra.run(g, Params.from(0)).result().visitOrder());
    }
}

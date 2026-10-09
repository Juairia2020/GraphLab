package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
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

public class PrimTest {
    private final Prim prim = new Prim();

    @Test
    public void minimumSpanningTreeFromA() {
        Result r = prim.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(33.0, r.totalWeight(), 1e-9);
        assertEquals(List.of(0, 1, 6, 8, 7), r.treeEdgeIds());   // AB AC CF EF DE
    }

    @Test
    public void nodesAreAddedInTheOrderTheTreeGrows() {
        Result r = prim.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 1, 2, 5, 4, 3), r.visitOrder());   // A B C F E D
    }

    @Test
    public void startingElsewhereGivesTheSameTotalWeight() {
        Graph g = SampleGraph.sixNodeUndirected();
        for (int start = 0; start < 6; start++) {
            assertEquals("start " + start, 33.0, prim.run(g, Params.from(start)).result().totalWeight(), 1e-9);
        }
    }

    @Test
    public void primAndKruskalChooseTheSameEdges() {
        Graph g = SampleGraph.sixNodeUndirected();
        Set<Integer> fromPrim = Set.copyOf(prim.run(g, Params.from(0)).result().treeEdgeIds());
        Set<Integer> fromKruskal = Set.copyOf(new Kruskal().run(g, Params.none()).result().treeEdgeIds());
        assertEquals(fromKruskal, fromPrim);
    }

    @Test
    public void skipsAWaitingEdgeWhoseFarEndAlreadyJoined() {
        // A-B 1, A-C 2, B-C 3, C-D 9: B-C is queued, then C joins through A-C, so B-C is skipped
        Graph g = Graph.builder(false).nodes(4).edge(0, 1, 1).edge(0, 2, 2).edge(1, 2, 3).edge(2, 3, 9).build();
        Trace t = prim.run(g, Params.from(0));
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(State.FAIL, last.edgeState(2));
        assertEquals(Set.of(0, 1, 3), TestGraphs.edgesIn(last, State.PATH));
        assertEquals(12.0, t.result().totalWeight(), 1e-9);
    }

    @Test
    public void onlyTheStartComponentIsSpanned() {
        Trace t = prim.run(TestGraphs.disconnected(), Params.from(0));
        assertEquals(3, t.result().visitOrder().size());
        assertEquals(2, t.result().treeEdgeIds().size());
        assertTrue(t.last().message().contains("3 of 7"));
    }

    @Test
    public void nodesWaitingInTheQueueShowTheirCheapestKey() {
        Trace t = prim.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        GraphFrame first = (GraphFrame) t.step(0).frame();
        assertEquals("7", first.nodeLabel(SampleGraph.B));
        assertEquals("9", first.nodeLabel(SampleGraph.C));
        assertEquals("14", first.nodeLabel(SampleGraph.F));
        assertEquals(State.FRONTIER, first.nodeState(SampleGraph.B));
        assertEquals(State.VISITED, first.nodeState(SampleGraph.A));
    }

    @Test
    public void rejectsDirectedGraphsAndBadStarts() {
        Graph directed = Graph.builder(true).nodes(2).edge(0, 1, 1).build();
        assertThrows(IllegalArgumentException.class, () -> prim.run(directed, Params.from(0)));
        Graph g = SampleGraph.sixNodeUndirected();
        assertThrows(IllegalArgumentException.class, () -> prim.run(g, Params.none()));
        assertThrows(IllegalArgumentException.class, () -> prim.run(g, Params.from(42)));
    }

    @Test
    public void equalWeightsGoToTheLowerEdgeIdEvenWhenQueuedLater() {
        // After B joins, edge 0 (B-D, weight 2) is queued after edge 2 (A-C, weight 2) but has the lower id
        Graph tie = new Graph(false, Graph.builder(false).nodes(4).build().nodes(), List.of(
                new com.graphlab.engine.model.Edge(0, 1, 3, 2),
                new com.graphlab.engine.model.Edge(1, 0, 1, 1),
                new com.graphlab.engine.model.Edge(2, 0, 2, 2)));
        assertEquals(List.of(0, 1, 3, 2), prim.run(tie, Params.from(0)).result().visitOrder());   // A B D C
    }
}

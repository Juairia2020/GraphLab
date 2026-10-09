package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.graphlab.engine.algo.TestGraphs;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import org.junit.Test;

public class DfsTest {
    private final Dfs dfs = new Dfs();

    @Test
    public void discoveryAndFinishOrderOnSampleGraph() {
        Result r = dfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 1, 2, 3, 4, 5), r.visitOrder());     // A B C D E F
        assertEquals(List.of(5, 4, 3, 2, 1, 0), r.finishOrder());    // F E D C B A
    }

    @Test
    public void treeEdgesFormTheDeepPath() {
        Result r = dfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 3, 5, 7, 8), r.treeEdgeIds());       // A-B B-C C-D D-E E-F
        assertEquals(43.0, r.totalWeight(), 1e-9);   // 7 + 10 + 11 + 6 + 9
    }

    @Test
    public void labelsShowDiscoverySlashFinishTimes() {
        Trace t = dfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals("1/12", last.nodeLabel(SampleGraph.A));
        assertEquals("6/7", last.nodeLabel(SampleGraph.F));
    }

    @Test
    public void nodesOnTheStackAreFrontierWhileTheCurrentOneIsChosen() {
        Trace t = dfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        boolean sawDeepStack = false;
        for (var step : t.steps()) {
            GraphFrame f = (GraphFrame) step.frame();
            long chosen = TestGraphs.nodesIn(f, State.CHOSEN).size();
            if (chosen > 1) {
                throw new AssertionError("More than one CHOSEN node at: " + step.message());
            }
            if (TestGraphs.nodesIn(f, State.FRONTIER).size() >= 4) {
                sawDeepStack = true;
            }
        }
        org.junit.Assert.assertTrue(sawDeepStack);
    }

    @Test
    public void onlyTheStartComponentIsExplored() {
        Result r = dfs.run(TestGraphs.disconnected(), Params.from(3)).result();
        assertEquals(List.of(3, 4, 5), r.visitOrder());
    }

    @Test
    public void directedGraphFollowsDirection() {
        Graph g = Graph.builder(true).nodes(3).edge(0, 1, 1).edge(2, 0, 1).build();
        assertEquals(List.of(0, 1), dfs.run(g, Params.from(0)).result().visitOrder());
    }

    @Test
    public void sixtyNodeChainDoesNotOverflowTheStack() {
        Graph g = TestGraphs.chain(60, 1, false);
        Result r = dfs.run(g, Params.from(0)).result();
        assertEquals(60, r.visitOrder().size());
        assertEquals(59, r.treeEdgeIds().size());
    }

    @Test
    public void needsAValidStartNode() {
        Graph g = SampleGraph.sixNodeUndirected();
        assertThrows(IllegalArgumentException.class, () -> dfs.run(g, Params.none()));
        assertThrows(IllegalArgumentException.class, () -> dfs.run(g, Params.from(42)));
    }
}

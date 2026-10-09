package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.TestGraphs;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class BfsTest {
    private final Bfs bfs = new Bfs();

    @Test
    public void hopDistancesOnSampleGraph() {
        Result r = bfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(0.0, r.distances().get(SampleGraph.A), 1e-9);
        assertEquals(1.0, r.distances().get(SampleGraph.B), 1e-9);
        assertEquals(1.0, r.distances().get(SampleGraph.C), 1e-9);
        assertEquals(1.0, r.distances().get(SampleGraph.F), 1e-9);
        assertEquals(2.0, r.distances().get(SampleGraph.D), 1e-9);
        assertEquals(2.0, r.distances().get(SampleGraph.E), 1e-9);
    }

    @Test
    public void visitOrderFollowsEdgeOrder() {
        Result r = bfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 1, 2, 5, 3, 4), r.visitOrder());   // A B C F D E
    }

    @Test
    public void treeEdgesAreTheDiscoveringEdges() {
        Result r = bfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A)).result();
        // F is dequeued before D, so F discovers E (edge E-F) before D could (edge D-E)
        assertEquals(List.of(0, 1, 2, 4, 8), r.treeEdgeIds());   // A-B A-C A-F B-D E-F
        assertEquals(5, r.treeEdgeIds().size());
    }

    @Test
    public void firstStepShowsOnlyTheStartNodeInTheQueue() {
        Trace t = bfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.C));
        var first = (com.graphlab.engine.trace.GraphFrame) t.step(0).frame();
        assertEquals(Set.of(SampleGraph.C), TestGraphs.nodesIn(first, State.FRONTIER));
        assertEquals("0", first.nodeLabel(SampleGraph.C));
    }

    @Test
    public void unreachableNodesStayIdleWithInfiniteDistance() {
        Graph g = TestGraphs.disconnected();
        Trace t = bfs.run(g, Params.from(0));
        assertEquals(Double.POSITIVE_INFINITY, t.result().distances().get(3), 0);
        assertEquals(Double.POSITIVE_INFINITY, t.result().distances().get(6), 0);
        assertEquals(State.IDLE, TestGraphs.lastFrame(t).nodeState(3));
        assertEquals(3, t.result().visitOrder().size());
    }

    @Test
    public void directedGraphFollowsDirection() {
        Graph g = Graph.builder(true).nodes(3).edge(0, 1, 1).edge(2, 0, 1).build();
        Result r = bfs.run(g, Params.from(0)).result();
        assertEquals(List.of(0, 1), r.visitOrder());
        assertEquals(Double.POSITIVE_INFINITY, r.distances().get(2), 0);
    }

    @Test
    public void singleNodeGraphStillGivesAFullTrace() {
        Trace t = bfs.run(TestGraphs.singleNode(), Params.from(0));
        assertEquals(List.of(0), t.result().visitOrder());
        assertTrue(t.size() >= 3);
        assertEquals(State.VISITED, TestGraphs.lastFrame(t).nodeState(0));
    }

    @Test
    public void selfLoopsAndParallelEdgesAreSkippedCleanly() {
        Result r = bfs.run(TestGraphs.awkward(), Params.from(0)).result();
        assertEquals(List.of(0, 1, 2, 3), r.visitOrder());
        assertEquals(3, r.treeEdgeIds().size());
    }

    @Test
    public void pathToUsesTheBfsTree() {
        Graph g = SampleGraph.sixNodeUndirected();
        Result r = bfs.run(g, Params.from(SampleGraph.A)).result();
        assertEquals(List.of(0, 1, 3), r.pathTo(g, SampleGraph.D));   // A B D
    }

    @Test
    public void needsAValidStartNode() {
        Graph g = SampleGraph.sixNodeUndirected();
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> bfs.run(g, Params.none()));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> bfs.run(g, Params.from(42)));
    }

    @Test
    public void labelsShowHopsOnTheLastFrame() {
        Trace t = bfs.run(SampleGraph.sixNodeUndirected(), Params.from(SampleGraph.A));
        var last = TestGraphs.lastFrame(t);
        assertEquals("2", last.nodeLabel(SampleGraph.E));
        assertEquals(2.0, Labels.parse(last.nodeLabel(SampleGraph.D)), 1e-9);
    }
}

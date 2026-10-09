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

public class KruskalTest {
    private final Kruskal kruskal = new Kruskal();

    @Test
    public void minimumSpanningTreeOfTheSampleGraph() {
        Result r = kruskal.run(SampleGraph.sixNodeUndirected(), Params.none()).result();
        assertEquals(33.0, r.totalWeight(), 1e-9);
        assertEquals(List.of(6, 7, 0, 1, 8), r.treeEdgeIds());   // CF 2, DE 6, AB 7, AC 9, EF 9
    }

    @Test
    public void stopsAsSoonAsTheTreeIsComplete() {
        Trace t = kruskal.run(SampleGraph.sixNodeUndirected(), Params.none());
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(State.IDLE, last.edgeState(3));    // B-C (10) was never looked at
        assertEquals(0L, (long) t.last().counters().get("skipped"));
    }

    @Test
    public void skipsAnEdgeThatWouldMakeACycle() {
        // A-B 1, B-C 1, A-C 1 (closes the triangle), C-D 5
        Graph g = Graph.builder(false).nodes(4).edge(0, 1, 1).edge(1, 2, 1).edge(0, 2, 1).edge(2, 3, 5).build();
        Trace t = kruskal.run(g, Params.none());
        GraphFrame last = TestGraphs.lastFrame(t);
        assertEquals(State.FAIL, last.edgeState(2));
        assertEquals(Set.of(0, 1, 3), TestGraphs.edgesIn(last, State.PATH));
        assertEquals(1L, (long) t.last().counters().get("skipped"));
        assertEquals(7.0, t.result().totalWeight(), 1e-9);
    }

    @Test
    public void equalWeightsAreTriedInEdgeIdOrder() {
        // Both A-B edges weigh the same; the lower id (0) must win
        Graph g = Graph.builder(false).nodes(2).edge(0, 1, 3).edge(0, 1, 3).build();
        assertEquals(List.of(0), kruskal.run(g, Params.none()).result().treeEdgeIds());
    }

    @Test
    public void selfLoopIsAlwaysSkipped() {
        Graph g = Graph.builder(false).nodes(2).edge(0, 0, 0).edge(0, 1, 4).build();
        Trace t = kruskal.run(g, Params.none());
        assertEquals(State.FAIL, TestGraphs.lastFrame(t).edgeState(0));
        assertEquals(List.of(1), t.result().treeEdgeIds());
    }

    @Test
    public void disconnectedGraphGivesASpanningForest() {
        Trace t = kruskal.run(TestGraphs.disconnected(), Params.none());
        assertEquals(4, t.result().treeEdgeIds().size());   // 2 per triangle
        assertEquals(2 + 3 + 1 + 2, t.result().totalWeight(), 1e-9);
        assertTrue(t.last().message().contains("forest"));
    }

    @Test
    public void singleNodeGivesAnEmptyTree() {
        Trace t = kruskal.run(TestGraphs.singleNode(), Params.none());
        assertTrue(t.result().treeEdgeIds().isEmpty());
        assertTrue(t.last().message().contains("tree"));
    }

    @Test
    public void rejectsDirectedGraphsAndEmptyGraphs() {
        Graph directed = Graph.builder(true).nodes(2).edge(0, 1, 1).build();
        assertThrows(IllegalArgumentException.class, () -> kruskal.run(directed, Params.none()));
        Graph empty = Graph.builder(false).build();
        assertThrows(IllegalArgumentException.class, () -> kruskal.run(empty, Params.none()));
    }

    @Test
    public void equalWeightsAreTriedInIdOrderEvenWhenListedOutOfOrder() {
        Graph base = Graph.builder(false).nodes(2).build();
        Graph g = new Graph(false, base.nodes(), List.of(
                new com.graphlab.engine.model.Edge(5, 0, 1, 3),
                new com.graphlab.engine.model.Edge(2, 0, 1, 3)));
        assertEquals(List.of(2), kruskal.run(g, Params.none()).result().treeEdgeIds());
    }
}

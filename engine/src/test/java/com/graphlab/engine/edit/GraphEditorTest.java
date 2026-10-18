package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class GraphEditorTest {

    private static GraphEditor triangle(boolean directed) {
        GraphEditor ed = new GraphEditor(directed, true);
        int a = ed.addNode(0, 0);
        int b = ed.addNode(100, 0);
        int c = ed.addNode(50, 80);
        ed.addEdge(a, b, 4);
        ed.addEdge(b, c, 5);
        ed.addEdge(a, c, 6);
        return ed;
    }

    // ---- nodes ----

    @Test
    public void nodesGetIncreasingIdsAndLettersInOrder() {
        GraphEditor ed = new GraphEditor(false, true);
        assertEquals(0, ed.addNode(0, 0));
        assertEquals(1, ed.addNode(100, 0));
        assertEquals("A", ed.nodeAt(0).label());
        assertEquals("B", ed.nodeAt(1).label());
    }

    @Test
    public void deletedLabelIsReusedButTheIdIsNot() {
        GraphEditor ed = new GraphEditor(false, true);
        ed.addNode(0, 0);              // id 0, A
        int b = ed.addNode(100, 0);    // id 1, B
        ed.addNode(200, 0);            // id 2, C
        ed.removeNode(b);
        int again = ed.addNode(300, 0);
        assertEquals(3, again);                       // ids never come back
        assertEquals("B", ed.findNode(again).label()); // the free letter does
    }

    @Test
    public void labelsStayUniqueUpToTheNodeLimit() {
        GraphEditor ed = new GraphEditor(false, true);
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < GraphEditor.MAX_NODES; i++) {
            int id = ed.addNode(i * 100.0, 0);
            assertTrue(id >= 0);
            labels.add(ed.findNode(id).label());
        }
        assertEquals(GraphEditor.MAX_NODES, labels.size());
        assertEquals("A", GraphEditor.labelFor(0));
        assertEquals("Z", GraphEditor.labelFor(25));
        assertEquals("A1", GraphEditor.labelFor(26));
        assertEquals("H2", GraphEditor.labelFor(59));
    }

    @Test
    public void refusesNodeNumberSixtyOne() {
        GraphEditor ed = new GraphEditor(false, true);
        for (int i = 0; i < GraphEditor.MAX_NODES; i++) {
            ed.addNode(i * 100.0, 0);
        }
        assertFalse(ed.canAddNode());
        assertEquals(-1, ed.addNode(0, 500));
        assertEquals(GraphEditor.MAX_NODES, ed.nodeCount());
    }

    @Test
    public void freeSpotRespectsTheMinimumGap() {
        GraphEditor ed = new GraphEditor(false, true);
        ed.addNode(0, 0);
        assertFalse(ed.isFreeSpot(10, 10));
        assertFalse(ed.isFreeSpot(EditorMetrics.MIN_NODE_GAP - 1, 0));
        assertTrue(ed.isFreeSpot(EditorMetrics.MIN_NODE_GAP + 1, 0));
    }

    @Test
    public void movingANodeChangesOnlyItsPosition() {
        GraphEditor ed = triangle(false);
        int structure = ed.structureVersion();
        int version = ed.version();
        assertTrue(ed.moveNode(1, 120, 30));
        assertEquals(120, ed.findNode(1).x(), 1e-9);
        assertEquals("B", ed.findNode(1).label());
        assertEquals(structure, ed.structureVersion());   // a move does not rebuild caches
        assertTrue(ed.version() > version);
        assertFalse(ed.moveNode(99, 0, 0));
    }

    @Test
    public void removingANodeRemovesItsEdges() {
        GraphEditor ed = triangle(false);
        assertTrue(ed.removeNode(0));      // A touches two edges
        assertEquals(2, ed.nodeCount());
        assertEquals(1, ed.edgeCount());
        assertEquals(1, ed.edgeAt(0).from());
        assertFalse(ed.removeNode(0));
    }

    // ---- edges ----

    @Test
    public void edgesGetIncreasingIds() {
        GraphEditor ed = triangle(false);
        assertEquals(0, ed.edgeAt(0).id());
        assertEquals(2, ed.edgeAt(2).id());
    }

    @Test
    public void refusesSelfLoopsAndParallelEdges() {
        GraphEditor ed = triangle(false);
        assertEquals(GraphEditor.ConnectCheck.SAME_NODE, ed.checkConnect(0, 0));
        assertEquals(GraphEditor.ConnectCheck.EXISTS, ed.checkConnect(0, 1));
        assertEquals(GraphEditor.ConnectCheck.EXISTS, ed.checkConnect(1, 0));   // undirected: either order
        assertEquals(GraphEditor.ConnectCheck.MISSING_NODE, ed.checkConnect(0, 42));
        assertThrows(IllegalArgumentException.class, () -> ed.addEdge(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ed.addEdge(1, 0, 1));
        assertEquals(3, ed.edgeCount());
    }

    @Test
    public void directedGraphAllowsTheOppositeEdge() {
        GraphEditor ed = triangle(true);
        assertEquals(GraphEditor.ConnectCheck.EXISTS, ed.checkConnect(0, 1));
        assertEquals(GraphEditor.ConnectCheck.OK, ed.checkConnect(1, 0));
        ed.addEdge(1, 0, 9);
        assertEquals(4, ed.edgeCount());
    }

    @Test
    public void rejectsNonFiniteWeights() {
        GraphEditor ed = new GraphEditor(false, true);
        ed.addNode(0, 0);
        ed.addNode(100, 0);
        assertThrows(IllegalArgumentException.class, () -> ed.addEdge(0, 1, Double.NaN));
        assertEquals(0, ed.edgeCount());
        assertEquals(0, ed.addEdge(0, 1, 2));   // the failed attempt did not use up id 0
    }

    @Test
    public void edgeWeightCanBeChangedAndEdgeDeleted() {
        GraphEditor ed = triangle(false);
        assertTrue(ed.setEdgeWeight(1, -3));
        assertEquals(-3.0, ed.findEdge(1).weight(), 1e-9);
        assertTrue(ed.removeEdge(1));
        assertNull(ed.findEdge(1));
        assertFalse(ed.removeEdge(1));
        assertFalse(ed.setEdgeWeight(1, 5));
    }

    @Test
    public void edgeBetweenFindsTheEdge() {
        GraphEditor ed = triangle(false);
        assertEquals(1, ed.edgeBetween(1, 2));
        assertEquals(1, ed.edgeBetween(2, 1));
        GraphEditor directed = triangle(true);
        assertEquals(-1, directed.edgeBetween(2, 1));
    }

    // ---- directed / weighted ----

    @Test
    public void switchingToUndirectedMergesReciprocalPairsKeepingTheFirst() {
        GraphEditor ed = triangle(true);
        ed.addEdge(1, 0, 99);            // reciprocal of the first edge
        assertEquals(4, ed.edgeCount());
        assertEquals(1, ed.setDirected(false));
        assertEquals(3, ed.edgeCount());
        assertEquals(4.0, ed.findEdge(0).weight(), 1e-9);   // the earlier edge won
        assertFalse(ed.isDirected());
    }

    @Test
    public void countsReciprocalPairsOnlyWhenDirected() {
        GraphEditor ed = triangle(true);
        assertEquals(0, ed.reciprocalPairCount());
        ed.addEdge(1, 0, 3);
        ed.addEdge(2, 1, 3);
        assertEquals(2, ed.reciprocalPairCount());
        assertEquals(2, ed.setDirected(false));      // the count is exactly what the merge removes
        assertEquals(0, ed.reciprocalPairCount());
    }

    @Test
    public void switchingToDirectedKeepsEveryEdge() {
        GraphEditor ed = triangle(false);
        assertEquals(0, ed.setDirected(true));
        assertEquals(3, ed.edgeCount());
        assertEquals(0, ed.setDirected(true));   // no change
    }

    @Test
    public void unweightedGraphExportsEveryWeightAsOneAndKeepsTheTypedOnes() {
        GraphEditor ed = triangle(false);
        ed.setWeighted(false);
        Graph unweighted = ed.toGraph();
        for (var edge : unweighted.edges()) {
            assertEquals(1.0, edge.weight(), 1e-9);
        }
        ed.setWeighted(true);
        assertEquals(4.0, ed.toGraph().edge(0).weight(), 1e-9);
    }

    @Test
    public void storedGraphKeepsTypedWeightsEvenWhenUnweighted() {
        GraphEditor ed = triangle(false);
        ed.setWeighted(false);
        assertEquals(1.0, ed.toGraph().edge(0).weight(), 1e-9);          // what algorithms see
        assertEquals(4.0, ed.toStoredGraph().edge(0).weight(), 1e-9);    // what is saved
        assertEquals(6.0, ed.toStoredGraph().edge(2).weight(), 1e-9);
    }

    // ---- derived data for drawing ----

    @Test
    public void edgeIndexesPointAtTheNodeList() {
        GraphEditor ed = triangle(false);
        ed.removeNode(0);   // node list is now [B, C]; the remaining edge is B-C
        assertEquals(0, ed.edgeFromIndex(0));
        assertEquals(1, ed.edgeToIndex(0));
    }

    @Test
    public void onlyDirectedReciprocalEdgesAreMarkedForCurving() {
        GraphEditor ed = triangle(true);
        assertFalse(ed.edgeIsReciprocal(0));
        ed.addEdge(1, 0, 3);
        assertTrue(ed.edgeIsReciprocal(0));
        assertTrue(ed.edgeIsReciprocal(3));
        assertFalse(ed.edgeIsReciprocal(1));
        ed.setDirected(false);
        for (int i = 0; i < ed.edgeCount(); i++) {
            assertFalse(ed.edgeIsReciprocal(i));
        }
    }

    // ---- whole graph ----

    @Test
    public void roundTripWithGraphIsLossless() {
        Graph sample = SampleGraph.sixNodeUndirected();
        GraphEditor ed = GraphEditor.from(sample, true);
        assertEquals(sample, ed.toGraph());
    }

    @Test
    public void loadReplacesTheContentAndContinuesIds() {
        GraphEditor ed = triangle(false);
        ed.load(SampleGraph.sixNodeUndirected(), true);
        assertEquals(6, ed.nodeCount());
        assertEquals(9, ed.edgeCount());
        assertEquals(6, ed.addNode(500, 500));   // after the highest loaded id
        ed.clear();
        assertTrue(ed.isEmpty());
        assertEquals(0, ed.addNode(0, 0));
    }

    @Test
    public void scalePositionsAndBounds() {
        GraphEditor ed = GraphEditor.from(SampleGraph.sixNodeUndirected(), true);
        ed.scalePositions(2);
        double[] b = ed.bounds();
        assertNotNull(b);
        assertEquals(20, b[0], 1e-9);    // A.x 10 -> 20
        assertEquals(30, b[1], 1e-9);    // B.y 15 -> 30
        assertEquals(180, b[2], 1e-9);   // E.x 90 -> 180
        assertEquals(160, b[3], 1e-9);   // F.y 80 -> 160
        assertNull(new GraphEditor(false, true).bounds());
    }

    // ---- the editor's output is accepted by the real algorithms ----

    @Test
    public void aGraphBuiltByTouchRunsThroughDijkstra() {
        GraphEditor ed = new GraphEditor(false, true);
        int a = ed.addNode(0, 0);
        int b = ed.addNode(100, 0);
        int c = ed.addNode(200, 0);
        ed.addEdge(a, b, 2);
        ed.addEdge(b, c, 3);
        ed.addEdge(a, c, 10);
        Graph g = ed.toGraph();
        Result r = Algorithms.byId("dijkstra").run(g, Params.from(a)).result();
        assertEquals(5.0, r.distances().get(c), 1e-9);
    }

    @Test
    public void everyAlgorithmAcceptsTheEditedSampleGraph() {
        for (var algorithm : Algorithms.all()) {
            if (algorithm.inputKind() != com.graphlab.engine.input.InputKind.GRAPH) {
                continue;   // sorting and knapsack do not take a graph
            }
            Graph g = GraphEditor.from(algorithm.exampleGraph(), true).toGraph();
            Params params = Params.from(SampleGraph.A);
            algorithm.run(g, algorithm.needsTarget() ? params.withTarget(SampleGraph.E) : params);   // must not throw
        }
    }
}

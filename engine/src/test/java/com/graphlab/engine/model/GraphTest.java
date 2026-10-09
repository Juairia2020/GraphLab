package com.graphlab.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GraphTest {

    @Test
    public void builderCreatesNodesAndEdges() {
        Graph g = Graph.builder(false).nodes(3).edge(0, 1, 4).edge(1, 2, 5).build();
        assertEquals(3, g.nodeCount());
        assertEquals(2, g.edgeCount());
        assertEquals("A", g.node(0).label());
        assertEquals(5.0, g.edge(1).weight(), 1e-9);
        assertFalse(g.isDirected());
    }

    @Test
    public void autoEdgeIdsContinueAfterExplicitIds() {
        Graph g = Graph.builder(false).nodes(3).edge(10, 0, 1, 1).edge(1, 2, 1).build();
        assertEquals(11, g.edges().get(1).id());
    }

    @Test
    public void rejectsDuplicateNodeId() {
        List<Node> nodes = List.of(new Node(1, 0, 0, "a"), new Node(1, 1, 1, "b"));
        assertThrows(IllegalArgumentException.class, () -> new Graph(false, nodes, List.of()));
    }

    @Test
    public void rejectsDuplicateEdgeId() {
        List<Node> nodes = List.of(new Node(0, 0, 0, "a"), new Node(1, 1, 1, "b"));
        List<Edge> edges = List.of(new Edge(0, 0, 1, 1), new Edge(0, 1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Graph(false, nodes, edges));
    }

    @Test
    public void rejectsEdgeToMissingNode() {
        List<Node> nodes = List.of(new Node(0, 0, 0, "a"));
        List<Edge> edges = List.of(new Edge(0, 0, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> new Graph(false, nodes, edges));
    }

    @Test
    public void rejectsNegativeIdsAndNonFiniteWeights() {
        assertThrows(IllegalArgumentException.class, () -> new Node(-1, 0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Edge(0, 0, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new Edge(0, 0, 1, Double.POSITIVE_INFINITY));
    }

    @Test
    public void acceptsExactlyMaxNodesButNotOneMore() {
        assertEquals(60, Graph.builder(false).nodes(Graph.MAX_NODES).build().nodeCount());
        assertThrows(IllegalArgumentException.class,
                () -> Graph.builder(false).nodes(Graph.MAX_NODES + 1).build());
    }

    @Test
    public void undirectedEdgeGivesTwoArcs() {
        Graph g = Graph.builder(false).nodes(2).edge(0, 1, 3).build();
        assertEquals(2, g.arcs().size());
        assertEquals(1, g.arcsFrom(0).size());
        assertEquals(1, g.arcsFrom(0).get(0).to());
        assertEquals(0, g.arcsFrom(1).get(0).to());
    }

    @Test
    public void directedEdgeGivesOneArc() {
        Graph g = Graph.builder(true).nodes(2).edge(0, 1, 3).build();
        assertEquals(1, g.arcs().size());
        assertEquals(1, g.arcsFrom(0).size());
        assertTrue(g.arcsFrom(1).isEmpty());
    }

    @Test
    public void selfLoopGivesOneArcEvenWhenUndirected() {
        Graph g = Graph.builder(false).nodes(1).edge(0, 0, 2).build();
        assertEquals(1, g.arcs().size());
    }

    @Test
    public void arcsFromFollowEdgeOrder() {
        Graph g = Graph.builder(false).nodes(4).edge(0, 3, 1).edge(0, 1, 1).edge(2, 0, 1).build();
        List<Integer> targets = new ArrayList<>();
        g.arcsFrom(0).forEach(a -> targets.add(a.to()));
        assertEquals(List.of(3, 1, 2), targets);
    }

    @Test
    public void isolatedNodeHasNoArcs() {
        Graph g = Graph.builder(false).nodes(3).edge(0, 1, 1).build();
        assertTrue(g.arcsFrom(2).isEmpty());
    }

    @Test
    public void arcsFromUnknownNodeThrows() {
        Graph g = Graph.builder(false).nodes(2).build();
        assertThrows(IllegalArgumentException.class, () -> g.arcsFrom(9));
        assertThrows(IllegalArgumentException.class, () -> g.node(9));
        assertThrows(IllegalArgumentException.class, () -> g.edge(9));
    }

    @Test
    public void exposedListsCannotBeChanged() {
        Graph g = Graph.builder(false).nodes(2).edge(0, 1, 1).build();
        assertThrows(UnsupportedOperationException.class, () -> g.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> g.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> g.arcs().clear());
        assertThrows(UnsupportedOperationException.class, () -> g.arcsFrom(0).clear());
    }

    @Test
    public void namesFallBackToIdAndShowDirection() {
        Graph g = Graph.builder(true).node(4, 0, 0, "").node(5, 1, 1, "Dhaka").edge(4, 5, 1).build();
        assertEquals("#4", g.nodeName(4));
        assertEquals("#4->Dhaka", g.arcName(g.arcs().get(0)));
        Graph u = Graph.builder(false).nodes(2).edge(0, 1, 1).build();
        assertEquals("A-B", u.arcName(u.arcs().get(0)));
    }

    @Test
    public void equalGraphsAreEqual() {
        Graph a = SampleGraph.sixNodeUndirected();
        Graph b = SampleGraph.sixNodeUndirected();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        Graph directed = new Graph(true, a.nodes(), a.edges());
        assertNotEquals(a, directed);
    }

    @Test
    public void sampleGraphHasSixNodesAndNineEdges() {
        Graph g = SampleGraph.sixNodeUndirected();
        assertEquals(6, g.nodeCount());
        assertEquals(9, g.edgeCount());
        assertFalse(g.isDirected());
        assertEquals(2.0, g.edge(6).weight(), 1e-9);
    }
}

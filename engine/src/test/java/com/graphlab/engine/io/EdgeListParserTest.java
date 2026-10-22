package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import org.junit.Test;

public class EdgeListParserTest {

    private static GraphDocument ok(String text, boolean directed) {
        try {
            return EdgeListParser.parse(text, directed);
        } catch (GraphFormatException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    private static String refused(String text) {
        try {
            EdgeListParser.parse(text, false);
        } catch (GraphFormatException e) {
            return e.getMessage();
        }
        fail("expected a refusal for: " + text);
        return "";
    }

    @Test
    public void theExampleParses() {
        GraphDocument doc = ok(EdgeListParser.EXAMPLE, false);
        Graph g = doc.graph();
        assertEquals(4, g.nodeCount());
        assertEquals(5, g.edgeCount());
        assertTrue(doc.weighted());
        assertFalse(g.isDirected());
        assertEquals("A", g.nodes().get(0).label());
        assertEquals(4.0, g.edges().get(0).weight(), 0);
        assertEquals(GraphNames.IMPORTED, doc.name());
    }

    @Test
    public void edgesWithoutWeightsMakeAnUnweightedGraph() {
        GraphDocument doc = ok("A B\nB C", false);
        assertFalse(doc.weighted());
        assertEquals(1.0, doc.graph().edges().get(1).weight(), 0);
    }

    @Test
    public void oneWeightMakesItWeightedAndTheRestGetOne() {
        GraphDocument doc = ok("A B 7\nB C", false);
        assertTrue(doc.weighted());
        assertEquals(1.0, doc.graph().edges().get(1).weight(), 0);
    }

    @Test
    public void nodesGetIdsInOrderOfFirstAppearance() {
        Graph g = ok("Dhaka Khulna 3\nSylhet Dhaka 4", false).graph();
        assertEquals("Dhaka", g.node(0).label());
        assertEquals("Khulna", g.node(1).label());
        assertEquals("Sylhet", g.node(2).label());
        Edge second = g.edges().get(1);
        assertEquals(2, second.from());
        assertEquals(0, second.to());
    }

    @Test
    public void anArrowMeansDirected() {
        assertTrue(ok("A -> B 2\nB->C 1", false).graph().isDirected());
    }

    @Test
    public void aDirectiveOverridesTheDefault() {
        assertTrue(ok("directed\nA B", false).graph().isDirected());
        assertFalse(ok("undirected\nA B", true).graph().isDirected());
        assertTrue(ok("A B", true).graph().isDirected());
    }

    @Test
    public void conflictingDirectivesAreRefused() {
        assertTrue(refused("directed\nundirected\nA B").contains("both"));
    }

    @Test
    public void commentsBlankLinesCommasAndTabsAreFine() {
        Graph g = ok("# title\n\nA,B,4   // road\n\tB\tC\t2 # another\n", false).graph();
        assertEquals(3, g.nodeCount());
        assertEquals(2, g.edgeCount());
        assertEquals(4.0, g.edges().get(0).weight(), 0);
    }

    @Test
    public void windowsLineEndingsWork() {
        assertEquals(2, ok("A B\r\nB C\r\n", false).graph().edgeCount());
    }

    @Test
    public void aSingleWordIsAnIsolatedNode() {
        Graph g = ok("A B\nZ", false).graph();
        assertEquals(3, g.nodeCount());
        assertEquals(1, g.edgeCount());
    }

    @Test
    public void negativeAndDecimalWeightsAreAccepted() {
        Graph g = ok("A B -2.5\nB C 1e2", true).graph();
        assertEquals(-2.5, g.edges().get(0).weight(), 0);
        assertEquals(100.0, g.edges().get(1).weight(), 0);
    }

    @Test
    public void emptyTextIsRefused() {
        assertTrue(refused("").contains("empty"));
        assertTrue(refused("   \n  ").contains("empty"));
        assertTrue(refused("# only a comment").contains("no nodes"));
    }

    @Test
    public void badWeightNamesTheLine() {
        String message = refused("A B 1\nB C heavy");
        assertTrue(message, message.startsWith("Line 2"));
        assertTrue(message.contains("heavy"));
        assertTrue(refused("A B NaN").contains("not a weight"));
        assertTrue(refused("A B Infinity").contains("not a weight"));
    }

    @Test
    public void tooManyWordsOnALine() {
        assertTrue(refused("A B 1 2").contains("Line 1"));
    }

    @Test
    public void selfLoopIsRefused() {
        assertTrue(refused("A A 3").contains("itself"));
    }

    @Test
    public void duplicateEdgesAreRefused() {
        assertTrue(refused("A B\nB A").contains("already joined"));
        assertTrue(refused("A B 1\nA B 2").contains("line 1"));
    }

    @Test
    public void inADirectedGraphBothDirectionsAreDifferentEdges() {
        assertEquals(2, ok("directed\nA B\nB A", false).graph().edgeCount());
        assertTrue(refused("directed\nA B\nA B").contains("already joined"));
    }

    @Test
    public void tooManyNodesAreRefused() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Graph.MAX_NODES + 1; i++) {
            sb.append("n").append(i).append("\n");
        }
        assertTrue(refused(sb.toString()).contains(String.valueOf(Graph.MAX_NODES)));
    }

    @Test
    public void exactlyTheMaximumNodesWorks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Graph.MAX_NODES; i++) {
            sb.append("n").append(i).append("\n");
        }
        assertEquals(Graph.MAX_NODES, ok(sb.toString(), false).graph().nodeCount());
    }

    @Test
    public void longNamesAreRefused() {
        assertTrue(refused("abcdefghijklmnop B").contains("longer than"));
    }

    @Test
    public void hugeTextIsRefused() {
        assertTrue(refused("A B\n" + "#".repeat(EdgeListParser.MAX_CHARS)).contains("too long"));
    }

    @Test
    public void nodesAreSpreadOutSoTheyDoNotOverlap() {
        Graph g = ok("A B\nB C\nC D\nD E\nE F\nF A", false).graph();
        for (int i = 0; i < g.nodeCount(); i++) {
            for (int j = i + 1; j < g.nodeCount(); j++) {
                double d = Math.hypot(g.nodes().get(i).x() - g.nodes().get(j).x(), g.nodes().get(i).y() - g.nodes().get(j).y());
                assertTrue("nodes " + i + " and " + j + " are " + d + " apart", d >= GraphLayouts.MIN_SPACING - 1);
            }
        }
    }

    @Test
    public void theResultRoundTripsThroughJson() throws GraphFormatException {
        GraphDocument doc = ok(EdgeListParser.EXAMPLE, false);
        GraphDocument back = GraphJson.read(GraphJson.write(doc));
        assertEquals(doc.graph().edges(), back.graph().edges());
        assertEquals(doc.weighted(), back.weighted());
    }

    @Test
    public void nullIsRefusedNotACrash() {
        try {
            EdgeListParser.parse(null, false);
            fail();
        } catch (GraphFormatException e) {
            assertTrue(e.getMessage().contains("empty"));
        }
    }
}

package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import org.junit.Test;

public class GraphMlTest {

    private static String msg(String xml) {
        return assertThrows(GraphFormatException.class, () -> GraphMl.read(xml)).getMessage();
    }

    @Test
    public void roundTripGivesTheIdenticalDocument() throws Exception {
        GraphDocument doc = new GraphDocument("Campus roads", true, SampleGraph.sixNodeUndirected());
        assertEquals(doc, GraphMl.read(GraphMl.write(doc)));
    }

    @Test
    public void roundTripKeepsDirectionNegativeWeightsAndUnicode() throws Exception {
        Graph g = Graph.builder(true)
                .node(3, -12.5, 3.25, "S").node(8, 100, 0.1, "T<&>\"'").node(9, 7, 70, "\u0985\u09BE")
                .edge(5, 3, 8, -3.5).edge(2, 8, 3, 0.1).edge(11, 9, 3, 1e-9)
                .build();
        GraphDocument doc = new GraphDocument("Tom & \"Jerry\" <roads>", true, g);
        GraphDocument back = GraphMl.read(GraphMl.write(doc));
        assertEquals(doc, back);                       // gaps in ids (3, 8, 9 and 5, 2, 11) are kept
        assertTrue(back.graph().isDirected());
    }

    @Test
    public void anUnweightedDocumentWritesNoWeightsAndReadsBackUnweighted() throws Exception {
        GraphDocument doc = new GraphDocument("Plain", false, SampleGraph.sixNodeUndirected());
        String xml = GraphMl.write(doc);
        assertFalse(xml.contains("weight"));
        GraphDocument back = GraphMl.read(xml);
        assertFalse(back.weighted());
        for (var edge : back.graph().edges()) {
            assertEquals(1.0, edge.weight(), 0);
        }
    }

    @Test
    public void writtenFileIsGraphMlWithTheStandardNamespace() {
        String xml = GraphMl.write(new GraphDocument("N", true, SampleGraph.sixNodeUndirected()));
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("xmlns=\"http://graphml.graphdrawing.org/xmlns\""));
        assertTrue(xml.contains("edgedefault=\"undirected\""));
        assertTrue(xml.contains("<edge id=\"e0\" source=\"n0\" target=\"n1\">"));
    }

    @Test
    public void escapingHandlesTheFiveCharactersAndDropsInvalidOnes() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f", GraphMl.escape("a&b<c>d\"e'f"));
        assertEquals("ab", GraphMl.escape("a\u0000\u000Bb"));        // control characters XML cannot hold
        assertEquals("x\ty\n", GraphMl.escape("x\ty\n"));            // tab and newline are fine
        assertEquals("\uD83D\uDE00", GraphMl.escape("\uD83D\uDE00"));
        assertEquals("ab", GraphMl.escape("a\uD800b"));              // an unpaired surrogate
    }

    @Test
    public void readsAForeignFileWithTextIdsAndNoPositions() throws Exception {
        String xml = "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">"
                + "<key id=\"k1\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>"
                + "<graph id=\"Cities\" edgedefault=\"undirected\">"
                + "<node id=\"Dhaka\"/><node id=\"Khulna\"/><node id=\"Sylhet\"/>"
                + "<edge source=\"Dhaka\" target=\"Khulna\"><data key=\"k1\">270</data></edge>"
                + "<edge source=\"Dhaka\" target=\"Sylhet\"><data key=\"k1\">240.5</data></edge>"
                + "</graph></graphml>";
        GraphDocument doc = GraphMl.read(xml);
        assertEquals("Cities", doc.name());
        assertEquals(3, doc.graph().nodeCount());
        assertEquals("Dhaka", doc.graph().nodes().get(0).label());
        assertEquals(0, doc.graph().nodes().get(0).id());
        assertEquals(2, doc.graph().nodes().get(2).id());                       // numbered in file order
        assertEquals(240.5, doc.graph().edges().get(1).weight(), 0);
        assertTrue(doc.weighted());
        assertEquals(0, doc.graph().edges().get(0).id());
        // no positions in the file: laid out on a circle
        assertTrue(doc.graph().nodes().get(0).x() != doc.graph().nodes().get(1).x()
                || doc.graph().nodes().get(0).y() != doc.graph().nodes().get(1).y());
    }

    @Test
    public void fileWithNoNamespaceAndNumericIdsKeepsTheNumbers() throws Exception {
        String xml = "<graphml><graph edgedefault=\"directed\"><node id=\"4\"/><node id=\"7\"/>"
                + "<edge id=\"e9\" source=\"4\" target=\"7\"/></graph></graphml>";
        GraphDocument doc = GraphMl.read(xml);
        assertTrue(doc.graph().isDirected());
        assertEquals(4, doc.graph().nodes().get(0).id());
        assertEquals(7, doc.graph().nodes().get(1).id());
        assertEquals(9, doc.graph().edges().get(0).id());
        assertFalse(doc.weighted());                       // no weights anywhere
        assertEquals("4", doc.graph().nodes().get(0).label());
    }

    @Test
    public void mixedIdStylesFallBackToFileOrder() throws Exception {
        String xml = "<graphml><graph><node id=\"n1\"/><node id=\"x\"/><node id=\"n3\"/></graph></graphml>";
        GraphDocument doc = GraphMl.read(xml);
        assertEquals(0, doc.graph().nodes().get(0).id());
        assertEquals(2, doc.graph().nodes().get(2).id());
    }

    @Test
    public void duplicateNumberedIdsFallBackToFileOrder() throws Exception {
        String xml = "<graphml><graph><node id=\"n1\"/><node id=\"1\"/></graph></graphml>";   // both mean 1
        GraphDocument doc = GraphMl.read(xml);
        assertEquals(0, doc.graph().nodes().get(0).id());
        assertEquals(1, doc.graph().nodes().get(1).id());
    }

    @Test
    public void foreignPositionsInTinyUnitsAreScaledUp() throws Exception {
        String xml = "<graphml><key id=\"x\" for=\"node\" attr.name=\"x\"/><key id=\"y\" for=\"node\" attr.name=\"y\"/>"
                + "<graph><node id=\"a\"><data key=\"x\">0</data><data key=\"y\">0</data></node>"
                + "<node id=\"b\"><data key=\"x\">0.5</data><data key=\"y\">0</data></node></graph></graphml>";
        GraphDocument doc = GraphMl.read(xml);
        double d = Math.hypot(doc.graph().nodes().get(0).x() - doc.graph().nodes().get(1).x(),
                doc.graph().nodes().get(0).y() - doc.graph().nodes().get(1).y());
        assertEquals(GraphLayouts.MIN_SPACING, d, 1e-6);
    }

    @Test
    public void ourOwnFilesKeepCrowdedPositionsExactly() throws Exception {
        // Nodes the user dragged almost on top of each other: a foreign file would be re-laid out, ours must not.
        Graph g = Graph.builder(false).node(0, 0, 0, "A").node(1, 3, 4, "B").node(2, 3.5, 4.5, "C").edge(0, 0, 1, 2).build();
        GraphDocument doc = new GraphDocument("Crowded", true, g);
        assertEquals(doc, GraphMl.read(GraphMl.write(doc)));
        assertTrue(GraphMl.write(doc).contains("attr.name=\"graphlab\""));
    }

    @Test
    public void aForeignFileWithTheSameCrowdedPositionsIsLaidOutAgain() throws Exception {
        String xml = "<graphml><key id=\"x\" for=\"node\" attr.name=\"x\"/><key id=\"y\" for=\"node\" attr.name=\"y\"/><graph>"
                + "<node id=\"a\"><data key=\"x\">0</data><data key=\"y\">0</data></node>"
                + "<node id=\"b\"><data key=\"x\">3</data><data key=\"y\">4</data></node></graph></graphml>";
        GraphDocument doc = GraphMl.read(xml);
        double d = Math.hypot(doc.graph().nodes().get(1).x() - doc.graph().nodes().get(0).x(),
                doc.graph().nodes().get(1).y() - doc.graph().nodes().get(0).y());
        assertEquals(GraphLayouts.MIN_SPACING, d, 1e-6);
    }

    @Test
    public void readsLabelsFromTheLabelOrNameKey() throws Exception {
        String xml = "<graphml><key id=\"l\" for=\"node\" attr.name=\"name\"/><graph><node id=\"1\"><data key=\"l\"> Home </data></node></graph></graphml>";
        assertEquals("Home", GraphMl.read(xml).graph().nodes().get(0).label());
    }

    @Test
    public void graphNameComesFromDescThenGraphId() throws Exception {
        assertEquals("From desc", GraphMl.read("<graphml><graph id=\"G\"><desc> From   desc </desc></graph></graphml>").name());
        assertEquals("MyId", GraphMl.read("<graphml><graph id=\"MyId\"></graph></graphml>").name());
        assertEquals("Imported graph", GraphMl.read("<graphml><graph id=\"G\"></graph></graphml>").name());
    }

    // ---- refusals ----

    @Test
    public void refusesDoctypeAndEntities() {
        String bomb = "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;\">]>"
                + "<graphml><graph><node id=\"&lol2;\"/></graph></graphml>";
        assertTrue(msg(bomb).contains("DOCTYPE"));
        String external = "<!DOCTYPE g SYSTEM \"file:///etc/passwd\"><graphml><graph/></graphml>";
        assertTrue(msg(external).contains("DOCTYPE"));
        assertTrue(msg("<!doctype x><graphml/>").contains("DOCTYPE"));
    }

    @Test
    public void refusesBrokenAndWrongXml() {
        assertTrue(msg("<graphml><graph>").contains("not valid XML"));
        assertTrue(msg("<a><b></a></b>").contains("not valid XML"));
        assertTrue(msg("<html><body/></html>").contains("not GraphML"));
        assertTrue(msg("<graphml/>").contains("no <graph>"));
    }

    @Test
    public void refusesWhatGraphLabCannotHold() {
        assertTrue(msg("<graphml><graph/><graph/></graphml>").contains("more than one graph"));
        assertTrue(msg("<graphml><graph><node id=\"a\"><graph/></node></graph></graphml>").contains("Nested graphs"));
        assertTrue(msg("<graphml><graph><hyperedge/></graph></graphml>").contains("Hyperedges"));
        assertTrue(msg("<graphml><graph edgedefault=\"sideways\"/></graphml>").contains("edgedefault"));
        assertTrue(msg("<graphml><graph edgedefault=\"undirected\"><node id=\"a\"/><node id=\"b\"/>"
                + "<edge source=\"a\" target=\"b\" directed=\"true\"/></graph></graphml>").contains("mixed"));
    }

    @Test
    public void refusesBadNodesAndEdges() {
        assertTrue(msg("<graphml><graph><node/></graph></graphml>").contains("has no id"));
        assertTrue(msg("<graphml><graph><node id=\"a\"/><node id=\"a\"/></graph></graphml>").contains("same")
                || msg("<graphml><graph><node id=\"a\"/><node id=\"a\"/></graph></graphml>").contains("\"a\""));
        assertTrue(msg("<graphml><graph><node id=\"a\"/><edge source=\"a\" target=\"zzz\"/></graph></graphml>").contains("zzz"));
        assertTrue(msg("<graphml><graph><node id=\"a\"/><edge source=\"a\"/></graph></graphml>").contains("source and a target"));
        assertTrue(msg("<graphml><graph><node id=\"a\"/><edge source=\"a\" target=\"a\"/></graph></graphml>").contains("self-loops"));
        assertTrue(msg("<graphml><graph><node id=\"a\"/><node id=\"b\"/><edge source=\"a\" target=\"b\"/><edge source=\"b\" target=\"a\"/></graph></graphml>").contains("parallel"));
    }

    @Test
    public void refusesBadNumbers() {
        String keys = "<key id=\"w\" for=\"edge\" attr.name=\"weight\"/><key id=\"x\" for=\"node\" attr.name=\"x\"/>";
        assertTrue(msg("<graphml>" + keys + "<graph><node id=\"a\"/><node id=\"b\"/><edge source=\"a\" target=\"b\"><data key=\"w\">heavy</data></edge></graph></graphml>")
                .contains("not a number"));
        assertTrue(msg("<graphml>" + keys + "<graph><node id=\"a\"><data key=\"x\">1e999</data></node></graph></graphml>")
                .contains("finite"));
    }

    @Test
    public void refusesMoreThanSixtyNodes() {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 61; i++) {
            nodes.append("<node id=\"n").append(i).append("\"/>");
        }
        assertTrue(msg("<graphml><graph>" + nodes + "</graph></graphml>").contains("at most 60"));
    }

    @Test
    public void aBillionLaughsStyleFileIsRefusedBeforeParsing() {
        // The whole file is tiny; what matters is that it never reaches the parser.
        String text = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]><graphml><graph id=\"&b;\"/></graphml>";
        long start = System.nanoTime();
        assertTrue(msg(text).contains("DOCTYPE"));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500);
    }
}

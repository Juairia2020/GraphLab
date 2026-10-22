package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class GraphFilesTest {
    private static final GraphDocument SAMPLE = new GraphDocument("Sample", true, SampleGraph.sixNodeUndirected());

    @Test
    public void detectsByTheFirstCharacter() throws Exception {
        assertEquals(GraphFiles.Format.JSON, GraphFiles.detect("  \n {\"a\":1}"));
        assertEquals(GraphFiles.Format.GRAPHML, GraphFiles.detect("\uFEFF<?xml version=\"1.0\"?>"));
        assertEquals(GraphFiles.Format.GRAPHML, GraphFiles.detect("<graphml/>"));
        assertThrows(GraphFormatException.class, () -> GraphFiles.detect("a,b,c\n1,2,3"));
        assertThrows(GraphFormatException.class, () -> GraphFiles.detect(""));
        assertThrows(GraphFormatException.class, () -> GraphFiles.detect("   "));
    }

    @Test
    public void readsBothFormatsAndAByteOrderMark() throws Exception {
        for (GraphFiles.Format format : GraphFiles.Format.values()) {
            String text = GraphFiles.write(SAMPLE, format);
            assertEquals(SAMPLE, GraphFiles.read(text));
            assertEquals(SAMPLE, GraphFiles.read("\uFEFF" + text));
        }
    }

    @Test
    public void convertingJsonToGraphMlAndBackChangesNothing() throws Exception {
        GraphDocument viaMl = GraphFiles.read(GraphFiles.write(
                GraphFiles.read(GraphFiles.write(SAMPLE, GraphFiles.Format.JSON)), GraphFiles.Format.GRAPHML));
        assertEquals(SAMPLE, GraphFiles.read(GraphFiles.write(viaMl, GraphFiles.Format.JSON)));
    }

    @Test
    public void refusesHugeText() {
        String huge = "{" + " ".repeat(GraphFiles.MAX_CHARS + 1);
        assertTrue(assertThrows(GraphFormatException.class, () -> GraphFiles.read(huge)).getMessage().contains("too large"));
    }

    @Test
    public void refusesPlainText() {
        assertTrue(assertThrows(GraphFormatException.class, () -> GraphFiles.read("hello")).getMessage().contains("neither"));
    }

    @Test
    public void fileNamesAreSafeEverywhere() {
        assertEquals("Campus roads.json", GraphFiles.fileName("Campus roads", GraphFiles.Format.JSON));
        assertEquals("a_b_c_d.graphml", GraphFiles.fileName("a/b\\c:d", GraphFiles.Format.GRAPHML));
        assertEquals("graph.json", GraphFiles.fileName("", GraphFiles.Format.JSON));
        assertEquals("graph.json", GraphFiles.fileName("////", GraphFiles.Format.JSON));
        assertEquals("graph.json", GraphFiles.fileName("\u0985\u09BE", GraphFiles.Format.JSON));   // non-ASCII only
        assertTrue(GraphFiles.fileName("x".repeat(100), GraphFiles.Format.JSON).length() <= 40 + ".json".length());
        assertEquals("graph.json", GraphFiles.fileName("..", GraphFiles.Format.JSON));                  // dots never survive
    }

    @Test
    public void importedFileNamesBecomeGraphNames() {
        assertEquals("campus", GraphFiles.nameFromFile("campus.graphml"));
        assertEquals("campus", GraphFiles.nameFromFile("/storage/emulated/0/Download/campus.JSON"));
        assertEquals("my.notes", GraphFiles.nameFromFile("my.notes"));
        assertEquals("Imported graph", GraphFiles.nameFromFile(null));
        assertEquals("Imported graph", GraphFiles.nameFromFile(".json"));
    }

    @Test
    public void thirtyRandomGraphsRoundTripExactlyInBothFormats() throws Exception {
        Random random = new Random(20261002L);
        for (int round = 0; round < 30; round++) {
            boolean directed = random.nextBoolean();
            int n = random.nextInt(Graph.MAX_NODES + 1);
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                nodes.add(new Node(i * 2 + random.nextInt(2), Math.round(random.nextDouble() * 4000 - 2000) / 4.0,
                        random.nextDouble() * 900, GraphEditor.labelFor(i)));
            }
            List<Edge> edges = new ArrayList<>();
            java.util.Set<Long> used = new java.util.HashSet<>();
            int id = 0;
            for (int tries = 0; tries < n * 2 && n > 1; tries++) {
                int a = random.nextInt(n);
                int b = random.nextInt(n);
                if (a == b) {
                    continue;
                }
                long key = directed ? a * 100L + b : Math.min(a, b) * 100L + Math.max(a, b);
                if (!used.add(key)) {
                    continue;
                }
                double w = random.nextInt(4) == 0 ? random.nextDouble() * 20 - 10 : random.nextInt(100) - 20;
                edges.add(new Edge(id++ * 3, nodes.get(a).id(), nodes.get(b).id(), w));
            }
            GraphDocument doc = new GraphDocument("Random " + round, random.nextBoolean(), new Graph(directed, nodes, edges));
            for (GraphFiles.Format format : GraphFiles.Format.values()) {
                GraphDocument back = GraphFiles.read(GraphFiles.write(doc, format));
                if (format == GraphFiles.Format.GRAPHML && !doc.weighted()) {
                    // an unweighted GraphML file stores no weights, so they read back as 1
                    assertEquals(doc.graph().nodes(), back.graph().nodes());
                    assertEquals(doc.graph().edgeCount(), back.graph().edgeCount());
                    continue;
                }
                assertEquals("round " + round + " " + format, doc, back);
            }
        }
    }
}

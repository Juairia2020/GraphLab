package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GraphJsonTest {

    private static GraphDocument sample() {
        return new GraphDocument("Campus roads", true, SampleGraph.sixNodeUndirected());
    }

    private static String msg(String json) {
        return assertThrows(GraphFormatException.class, () -> GraphJson.read(json)).getMessage();
    }

    private static String file(String body) {
        return "{\"format\":\"graphlab-graph\",\"version\":1," + body + "}";
    }

    @Test
    public void roundTripGivesTheIdenticalDocument() throws Exception {
        GraphDocument back = GraphJson.read(GraphJson.write(sample()));
        assertEquals(sample(), back);
    }

    @Test
    public void roundTripKeepsDirectionWeightsAndTheWeightedFlag() throws Exception {
        Graph g = Graph.builder(true)
                .node(0, -12.5, 3.25, "S").node(4, 100, 0.1, "T").node(9, 7, 7, "\u0985")
                .edge(5, 0, 4, -3.5).edge(2, 4, 0, 0.1).edge(8, 9, 0, 1e-9)
                .build();
        GraphDocument doc = new GraphDocument("Negative & fractional \"quoted\"", false, g);
        GraphDocument back = GraphJson.read(GraphJson.write(doc));
        assertEquals(doc, back);
        assertTrue(back.graph().isDirected());
        assertFalse(back.weighted());
    }

    @Test
    public void emptyGraphRoundTrips() throws Exception {
        GraphDocument doc = new GraphDocument("Empty", true, new Graph(false, List.of(), List.of()));
        assertEquals(doc, GraphJson.read(GraphJson.write(doc)));
    }

    @Test
    public void sixtyNodeGraphRoundTrips() throws Exception {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            nodes.add(new Node(i, i * 61.5, (i % 7) * 40.25, "N" + i));
            if (i > 0) {
                edges.add(new Edge(i - 1, i - 1, i, i * 0.5));
            }
        }
        GraphDocument doc = new GraphDocument("Chain", true, new Graph(false, nodes, edges));
        assertEquals(doc, GraphJson.read(GraphJson.write(doc)));
    }

    @Test
    public void writtenFileHasTheDocumentedShape() {
        String json = GraphJson.write(sample());
        assertTrue(json.contains("\"format\": \"graphlab-graph\""));
        assertTrue(json.contains("\"version\": 1"));
        assertTrue(json.contains("{\"id\": 0, \"from\": 0, \"to\": 1, \"weight\": 7}"));
        assertTrue(json.endsWith("}\n"));
    }

    @Test
    public void aHandWrittenMinimalFileGetsSensibleDefaults() throws Exception {
        GraphDocument doc = GraphJson.read(file("\"nodes\":[{\"id\":0},{\"id\":1},{\"id\":2}],\"edges\":[{\"from\":0,\"to\":1},{\"from\":1,\"to\":2,\"weight\":4}]"));
        assertEquals("Imported graph", doc.name());
        assertFalse(doc.graph().isDirected());
        assertTrue(doc.weighted());
        assertEquals("A", doc.graph().nodes().get(0).label());
        assertEquals("C", doc.graph().nodes().get(2).label());
        assertEquals(1.0, doc.graph().edges().get(0).weight(), 0);
        assertEquals(4.0, doc.graph().edges().get(1).weight(), 0);
        assertEquals(0, doc.graph().edges().get(0).id());
        assertEquals(1, doc.graph().edges().get(1).id());
        // no positions given: placed on a circle, so none overlap
        assertTrue(doc.graph().nodes().get(0).x() != doc.graph().nodes().get(1).x());
    }

    @Test
    public void missingEdgeIdsAreNumberedAfterTheGivenOnes() throws Exception {
        GraphDocument doc = GraphJson.read(file("\"nodes\":[{\"id\":0,\"x\":0,\"y\":0},{\"id\":1,\"x\":90,\"y\":0},{\"id\":2,\"x\":0,\"y\":90}],"
                + "\"edges\":[{\"from\":0,\"to\":1},{\"id\":7,\"from\":1,\"to\":2},{\"from\":2,\"to\":0}]"));
        assertEquals(8, doc.graph().edges().get(0).id());
        assertEquals(7, doc.graph().edges().get(1).id());
        assertEquals(9, doc.graph().edges().get(2).id());
    }

    @Test
    public void nameIsCleanedAndDefaultsWhenBlank() throws Exception {
        assertEquals("My roads", GraphJson.read(file("\"name\":\"  My   roads \",\"nodes\":[],\"edges\":[]")).name());
        assertEquals("Imported graph", GraphJson.read(file("\"name\":\"   \",\"nodes\":[],\"edges\":[]")).name());
        assertEquals(40, GraphJson.read(file("\"name\":\"" + "q".repeat(80) + "\",\"nodes\":[],\"edges\":[]")).name().length());
    }

    @Test
    public void reciprocalEdgesAreFineInADirectedGraph() throws Exception {
        GraphDocument doc = GraphJson.read(file("\"directed\":true,\"nodes\":[{\"id\":0},{\"id\":1}],\"edges\":[{\"from\":0,\"to\":1},{\"from\":1,\"to\":0}]"));
        assertEquals(2, doc.graph().edgeCount());
    }

    // ---- refusals, each with a message a person can act on ----

    @Test
    public void refusesWhatIsNotAGraphLabFile() {
        assertTrue(msg("[1,2]").contains("must be a JSON object"));
        assertTrue(msg("{\"nodes\":[],\"edges\":[]}").contains("not a GraphLab graph"));
        assertTrue(msg("{\"format\":\"other\",\"version\":1}").contains("not a GraphLab graph"));
        assertTrue(msg("not json at all").contains("(at character"));
        assertTrue(msg("hello").contains("(at character"));
        assertTrue(msg("%").contains("Unexpected character"));
        assertTrue(msg("").contains("ends too early"));
    }

    @Test
    public void refusesBadVersions() {
        assertTrue(msg("{\"format\":\"graphlab-graph\",\"version\":2,\"nodes\":[],\"edges\":[]}").contains("newer version"));
        assertTrue(msg("{\"format\":\"graphlab-graph\",\"version\":0,\"nodes\":[],\"edges\":[]}").contains("1 or more"));
        assertTrue(msg("{\"format\":\"graphlab-graph\",\"nodes\":[],\"edges\":[]}").contains("\"version\""));
        assertTrue(msg("{\"format\":\"graphlab-graph\",\"version\":1.5,\"nodes\":[],\"edges\":[]}").contains("whole number"));
    }

    @Test
    public void refusesMissingOrWronglyTypedSections() {
        assertTrue(msg(file("\"edges\":[]")).contains("\"nodes\""));
        assertTrue(msg(file("\"nodes\":[]")).contains("\"edges\""));
        assertTrue(msg(file("\"nodes\":{},\"edges\":[]")).contains("\"nodes\""));
        assertTrue(msg(file("\"directed\":\"yes\",\"nodes\":[],\"edges\":[]")).contains("true or false"));
        assertTrue(msg(file("\"nodes\":[5],\"edges\":[]")).contains("Node 1"));
    }

    @Test
    public void refusesBadNodes() {
        assertTrue(msg(file("\"nodes\":[{\"label\":\"A\"}],\"edges\":[]")).contains("Node 1 \"id\""));
        assertTrue(msg(file("\"nodes\":[{\"id\":-1}],\"edges\":[]")).contains("0 or more"));
        assertTrue(msg(file("\"nodes\":[{\"id\":1.5}],\"edges\":[]")).contains("whole number"));
        assertTrue(msg(file("\"nodes\":[{\"id\":0},{\"id\":0}],\"edges\":[]")).contains("share the id 0"));
        assertTrue(msg(file("\"nodes\":[{\"id\":0,\"x\":\"far\",\"y\":0}],\"edges\":[]")).contains("must be a number"));
        assertTrue(msg(file("\"nodes\":[{\"id\":0,\"x\":1e999,\"y\":0}],\"edges\":[]")).contains("too large"));
        assertTrue(msg(file("\"nodes\":[{\"id\":0,\"label\":7}],\"edges\":[]")).contains("must be text"));
    }

    @Test
    public void refusesMoreThanSixtyNodes() {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 61; i++) {
            nodes.append(i == 0 ? "" : ",").append("{\"id\":").append(i).append('}');
        }
        assertTrue(msg(file("\"nodes\":[" + nodes + "],\"edges\":[]")).contains("at most 60"));
    }

    @Test
    public void refusesBadEdges() {
        String two = "\"nodes\":[{\"id\":0},{\"id\":1}],";
        assertTrue(msg(file(two + "\"edges\":[{\"from\":0,\"to\":5}]")).contains("does not exist"));
        assertTrue(msg(file(two + "\"edges\":[{\"from\":0}]")).contains("\"to\""));
        assertTrue(msg(file(two + "\"edges\":[{\"from\":0,\"to\":1,\"weight\":\"heavy\"}]")).contains("must be a number"));
        assertTrue(msg(file(two + "\"edges\":[{\"from\":0,\"to\":1,\"weight\":1e999}]")).contains("too large"));
        assertTrue(msg(file(two + "\"edges\":[{\"id\":3,\"from\":0,\"to\":1},{\"id\":3,\"from\":1,\"to\":0}]")).contains("share the id 3"));
    }

    @Test
    public void refusesSelfLoopsAndParallelEdgesByName() {
        String two = "\"nodes\":[{\"id\":0,\"label\":\"A\"},{\"id\":1,\"label\":\"B\"}],";
        String loop = msg(file(two + "\"edges\":[{\"from\":1,\"to\":1}]"));
        assertTrue(loop, loop.contains("B") && loop.contains("self-loops"));
        String parallel = msg(file(two + "\"edges\":[{\"id\":0,\"from\":0,\"to\":1},{\"id\":1,\"from\":0,\"to\":1}]"));
        assertTrue(parallel, parallel.contains("parallel") && parallel.contains("A") && parallel.contains("B"));
        // in an undirected graph the opposite order is the same pair
        assertTrue(msg(file(two + "\"edges\":[{\"from\":0,\"to\":1},{\"from\":1,\"to\":0}]")).contains("parallel"));
    }

    @Test
    public void refusesDuplicateKeysInsideTheFile() {
        assertTrue(msg("{\"format\":\"graphlab-graph\",\"version\":1,\"version\":1,\"nodes\":[],\"edges\":[]}").contains("twice"));
    }
}

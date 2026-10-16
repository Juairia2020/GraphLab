package com.graphlab.engine.io;

import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GraphLab's own file format: readable JSON with a format name and a version, so a future app
 * can still open today's files. Version 1:
 *
 * <pre>
 * {
 *   "format": "graphlab-graph",
 *   "version": 1,
 *   "name": "Campus roads",
 *   "directed": false,
 *   "weighted": true,
 *   "background": {"file": "bg_1700000000000.jpg", "opacity": 0.5, "locked": false},
 *   "nodes": [ {"id": 0, "label": "A", "x": 10, "y": 50}, ... ],
 *   "edges": [ {"id": 0, "from": 0, "to": 1, "weight": 7}, ... ]
 * }
 * </pre>
 *
 * <p>{@code background} (MS10) is optional: the name of a photo in the app's own folder. Files written before it existed
 * still read, and an older app reading a newer file ignores it, so the version stays 1.
 *
 * <p>Only {@code format}, {@code version}, {@code nodes}, {@code edges} and each node's
 * {@code id}, each edge's {@code from} and {@code to} are required. Everything else has a default
 * (name "Imported graph", undirected, weighted, label A B C ..., weight 1, nodes on a circle if any
 * position is missing), so a file written by hand is easy. Writing then reading gives back the same
 * document exactly.
 */
public final class GraphJson {
    public static final String FORMAT = "graphlab-graph";
    public static final int VERSION = 1;

    private GraphJson() {
    }

    // ---- writing -----------------------------------------------------------------------

    public static String write(GraphDocument doc) {
        Graph graph = doc.graph();
        StringBuilder out = new StringBuilder(256 + 64 * (graph.nodeCount() + graph.edgeCount()));
        out.append("{\n");
        out.append("  \"format\": ").append(MiniJson.quote(FORMAT)).append(",\n");
        out.append("  \"version\": ").append(VERSION).append(",\n");
        out.append("  \"name\": ").append(MiniJson.quote(doc.name())).append(",\n");
        out.append("  \"directed\": ").append(graph.isDirected()).append(",\n");
        out.append("  \"weighted\": ").append(doc.weighted()).append(",\n");
        if (doc.background() != null) {
            Background bg = doc.background();
            out.append("  \"background\": {\"file\": ").append(MiniJson.quote(bg.file()))
                    .append(", \"opacity\": ").append(MiniJson.number(bg.opacity()))
                    .append(", \"locked\": ").append(bg.locked()).append("},\n");
        }
        out.append("  \"nodes\": [");
        for (int i = 0; i < graph.nodeCount(); i++) {
            Node node = graph.nodes().get(i);
            out.append(i == 0 ? "\n    " : ",\n    ");
            out.append("{\"id\": ").append(node.id())
                    .append(", \"label\": ").append(MiniJson.quote(node.label()))
                    .append(", \"x\": ").append(MiniJson.number(node.x()))
                    .append(", \"y\": ").append(MiniJson.number(node.y())).append('}');
        }
        out.append(graph.nodeCount() == 0 ? "],\n" : "\n  ],\n");
        out.append("  \"edges\": [");
        for (int i = 0; i < graph.edgeCount(); i++) {
            Edge edge = graph.edges().get(i);
            out.append(i == 0 ? "\n    " : ",\n    ");
            out.append("{\"id\": ").append(edge.id())
                    .append(", \"from\": ").append(edge.from())
                    .append(", \"to\": ").append(edge.to())
                    .append(", \"weight\": ").append(MiniJson.number(edge.weight())).append('}');
        }
        out.append(graph.edgeCount() == 0 ? "]\n" : "\n  ]\n");
        out.append("}\n");
        return out.toString();
    }

    // ---- reading -----------------------------------------------------------------------

    public static GraphDocument read(String text) throws GraphFormatException {
        return fromObject(MiniJson.parse(text));
    }

    /**
     * Reads a graph from an already parsed JSON value (what {@link MiniJson#parse} returns), so a
     * graph can sit inside a larger file such as the sample library.
     */
    public static GraphDocument fromObject(Object root) throws GraphFormatException {
        if (!(root instanceof Map)) {
            throw new GraphFormatException("A GraphLab file must be a JSON object, not a list or a single value.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> top = (Map<String, Object>) root;

        Object format = top.get("format");
        if (!FORMAT.equals(format)) {
            throw new GraphFormatException("This JSON is not a GraphLab graph (\"format\" should be \""
                    + FORMAT + "\").");
        }
        int version = wholeNumber(top.get("version"), "\"version\"");
        if (version < 1) {
            throw new GraphFormatException("\"version\" must be 1 or more.");
        }
        if (version > VERSION) {
            throw new GraphFormatException("This graph was saved by a newer version of GraphLab (file version "
                    + version + ", this app reads up to " + VERSION + "). Update the app to open it.");
        }

        String name = nameOf(top.get("name"));
        boolean directed = flag(top.get("directed"), false, "\"directed\"");
        boolean weighted = flag(top.get("weighted"), true, "\"weighted\"");

        List<Object> rawNodes = list(top.get("nodes"), "\"nodes\"");
        List<Object> rawEdges = list(top.get("edges"), "\"edges\"");
        if (rawNodes.size() > Graph.MAX_NODES) {
            throw new GraphFormatException("The graph has " + rawNodes.size()
                    + " nodes, but GraphLab supports at most " + Graph.MAX_NODES + ".");
        }

        List<Node> nodes = new ArrayList<>(rawNodes.size());
        boolean positionsMissing = false;
        for (int i = 0; i < rawNodes.size(); i++) {
            String where = "Node " + (i + 1);
            Map<String, Object> raw = object(rawNodes.get(i), where);
            int id = wholeNumber(raw.get("id"), where + " \"id\"");
            if (id < 0) {
                throw new GraphFormatException(where + ": \"id\" must be 0 or more.");
            }
            String label = raw.get("label") == null ? GraphEditor.labelFor(i) : text(raw.get("label"), where + " \"label\"");
            Double x = optionalNumber(raw.get("x"), where + " \"x\"");
            Double y = optionalNumber(raw.get("y"), where + " \"y\"");
            positionsMissing |= x == null || y == null;
            nodes.add(new Node(id, x == null ? 0 : x, y == null ? 0 : y, label));
        }
        if (positionsMissing) {
            nodes = GraphLayouts.circle(nodes);
        }

        List<Edge> edges = new ArrayList<>(rawEdges.size());
        Set<Integer> given = new HashSet<>();
        int highest = -1;
        for (int i = 0; i < rawEdges.size(); i++) {
            Object id = object(rawEdges.get(i), "Edge " + (i + 1)).get("id");
            if (id != null) {
                int value = wholeNumber(id, "Edge " + (i + 1) + " \"id\"");
                if (value < 0) {
                    throw new GraphFormatException("Edge " + (i + 1) + ": \"id\" must be 0 or more.");
                }
                given.add(value);
                highest = Math.max(highest, value);
            }
        }
        int nextFree = highest + 1;
        for (int i = 0; i < rawEdges.size(); i++) {
            String where = "Edge " + (i + 1);
            Map<String, Object> raw = object(rawEdges.get(i), where);
            int id = raw.get("id") == null ? nextFree++ : wholeNumber(raw.get("id"), where + " \"id\"");
            int from = wholeNumber(raw.get("from"), where + " \"from\"");
            int to = wholeNumber(raw.get("to"), where + " \"to\"");
            Double weight = optionalNumber(raw.get("weight"), where + " \"weight\"");
            edges.add(new Edge(id, from, to, weight == null ? GraphEditor.DEFAULT_WEIGHT : weight));
        }
        return new GraphDocument(name, weighted, GraphChecks.build(directed, nodes, edges), background(top.get("background")));
    }

    // ---- field helpers -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Background background(Object value) throws GraphFormatException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new GraphFormatException("\"background\" must be an object with a \"file\".");
        }
        Map<String, Object> raw = (Map<String, Object>) value;
        String file = text(raw.get("file"), "\"background\" \"file\"");
        if (!Background.isSafeName(file)) {
            throw new GraphFormatException("\"background\" \"file\" must be a plain file name.");
        }
        Double opacity = optionalNumber(raw.get("opacity"), "\"background\" \"opacity\"");
        double o = opacity == null ? Background.DEFAULT_OPACITY : opacity;
        if (o < 0 || o > 1) {
            throw new GraphFormatException("\"background\" \"opacity\" must be between 0 and 1.");
        }
        return new Background(file, o, flag(raw.get("locked"), false, "\"background\" \"locked\""));
    }

    private static String nameOf(Object value) throws GraphFormatException {
        if (value == null) {
            return GraphNames.IMPORTED;
        }
        String cleaned = GraphNames.clean(text(value, "\"name\""));
        return cleaned.isEmpty() ? GraphNames.IMPORTED : cleaned;
    }

    private static String text(Object value, String what) throws GraphFormatException {
        if (!(value instanceof String)) {
            throw new GraphFormatException(what + " must be text.");
        }
        return (String) value;
    }

    private static boolean flag(Object value, boolean fallback, String what) throws GraphFormatException {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean)) {
            throw new GraphFormatException(what + " must be true or false.");
        }
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String what) throws GraphFormatException {
        if (!(value instanceof List)) {
            throw new GraphFormatException(what + " is missing or is not a list.");
        }
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String where) throws GraphFormatException {
        if (!(value instanceof Map)) {
            throw new GraphFormatException(where + " must be an object like {...}.");
        }
        return (Map<String, Object>) value;
    }

    private static int wholeNumber(Object value, String what) throws GraphFormatException {
        if (!(value instanceof Double)) {
            throw new GraphFormatException(what + " must be a whole number.");
        }
        double d = (Double) value;
        if (d != Math.rint(d) || Math.abs(d) > Integer.MAX_VALUE) {
            throw new GraphFormatException(what + " must be a whole number.");
        }
        return (int) d;
    }

    private static Double optionalNumber(Object value, String what) throws GraphFormatException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Double)) {
            throw new GraphFormatException(what + " must be a number.");
        }
        double d = (Double) value;
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new GraphFormatException(what + " is too large to be a number.");
        }
        return d;
    }
}

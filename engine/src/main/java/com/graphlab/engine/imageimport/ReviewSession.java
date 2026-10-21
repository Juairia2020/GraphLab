package com.graphlab.engine.imageimport;

import com.graphlab.engine.io.EdgeListParser;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphLayouts;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The review-and-fix step. It holds a {@link DetectedGraph}, lists every doubtful item ({@link #flags()}), lets the
 * person fix or accept each one, and builds the final {@link GraphDocument}. A fix or an accept sets that item's
 * confidence to 1, so it leaves the amber list. Nothing is built while a blocking problem remains (for example two
 * nodes with the same name).
 */
public final class ReviewSession {
    public enum Kind { NODE, EDGE, WEIGHT, DIRECTION, WARNING }

    /** One doubtful item. {@code id} is the node id, the edge id, or the warning index. */
    public record Flag(Kind kind, int id, String message) {
    }

    private final List<DetectedGraph.Node> nodes;
    private final List<DetectedGraph.Edge> edges;
    private final List<String> warnings;
    private final Set<Integer> dismissed = new HashSet<>();
    private final boolean hasPositions;
    private final DetectedGraph.Source source;
    private boolean directed;
    private double directedConfidence;
    private boolean weighted;

    public ReviewSession(DetectedGraph detected) {
        nodes = new ArrayList<>(detected.nodes());
        edges = new ArrayList<>(detected.edges());
        warnings = new ArrayList<>(detected.warnings());
        hasPositions = detected.hasPositions();
        source = detected.source();
        directed = detected.directed();
        directedConfidence = detected.directedConfidence();
        weighted = detected.weighted();
    }

    // ---- reading ----

    public List<DetectedGraph.Node> nodes() {
        return List.copyOf(nodes);
    }

    public List<DetectedGraph.Edge> edges() {
        return List.copyOf(edges);
    }

    public boolean directed() {
        return directed;
    }

    public boolean weighted() {
        return weighted;
    }

    public boolean hasPositions() {
        return hasPositions;
    }

    public DetectedGraph.Source source() {
        return source;
    }

    public String labelOf(int nodeId) {
        for (DetectedGraph.Node n : nodes) {
            if (n.id() == nodeId) {
                return n.label();
            }
        }
        return "?";
    }

    /** Everything the person should look at, in the order: names, lines, weights, direction, notes. */
    public List<Flag> flags() {
        List<Flag> out = new ArrayList<>();
        double t = DetectedGraph.REVIEW_BELOW;
        for (DetectedGraph.Node n : nodes) {
            if (n.confidence() < t) {
                out.add(new Flag(Kind.NODE, n.id(), "Node " + n.label() + ": " + orElse(n.reason(), "please check this node")));
            }
        }
        for (DetectedGraph.Edge e : edges) {
            if (e.confidence() < t) {
                out.add(new Flag(Kind.EDGE, e.id(), "Edge " + labelOf(e.from()) + "–" + labelOf(e.to()) + ": "
                        + orElse(e.reason(), "please check this edge")));
            }
        }
        for (DetectedGraph.Edge e : edges) {
            if (weighted && (e.weight() == null || e.weightConfidence() < t)) {
                out.add(new Flag(Kind.WEIGHT, e.id(), "Weight of " + labelOf(e.from()) + "–" + labelOf(e.to()) + ": "
                        + orElse(e.reason(), "please check this weight")));
            }
        }
        if (directedConfidence < t) {
            out.add(new Flag(Kind.DIRECTION, 0, "Direction: the picture does not make clear if edges are directed."));
        }
        for (int i = 0; i < warnings.size(); i++) {
            if (!dismissed.contains(i)) {
                out.add(new Flag(Kind.WARNING, i, warnings.get(i)));
            }
        }
        return out;
    }

    /** Reasons the graph cannot be built yet, whatever the flags say. Empty when it can be built. */
    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (nodes.isEmpty()) {
            p.add("The graph has no nodes.");
        }
        if (nodes.size() > Graph.MAX_NODES) {
            p.add("The graph has " + nodes.size() + " nodes. GraphLab supports at most " + Graph.MAX_NODES + ".");
        }
        Set<String> seen = new HashSet<>();
        for (DetectedGraph.Node n : nodes) {
            String l = n.label().trim();
            if (l.isEmpty()) {
                p.add("A node has no name.");
            } else if (l.length() > EdgeListParser.MAX_NAME) {
                p.add("The name \"" + l + "\" is longer than " + EdgeListParser.MAX_NAME + " characters.");
            } else if (!seen.add(l)) {
                p.add("Two nodes are named \"" + l + "\".");
            }
        }
        Set<Long> pairs = new HashSet<>();
        for (DetectedGraph.Edge e : edges) {
            long a = e.from();
            long b = e.to();
            if (!directed && a > b) {
                long s = a;
                a = b;
                b = s;
            }
            if (!pairs.add(a * 100000 + b)) {
                p.add("Nodes " + labelOf(e.from()) + " and " + labelOf(e.to()) + " are joined twice.");
            }
            if (weighted && e.weight() == null) {
                p.add("The edge " + labelOf(e.from()) + "–" + labelOf(e.to()) + " has no weight.");
            }
        }
        return p;
    }

    /** True when nothing is flagged and nothing blocks the build. */
    public boolean isReady() {
        return flags().isEmpty() && problems().isEmpty();
    }

    // ---- fixing ----

    public void renameNode(int id, String label) {
        int i = nodeIndex(id);
        DetectedGraph.Node n = nodes.get(i);
        nodes.set(i, new DetectedGraph.Node(id, label.trim(), n.x(), n.y(), 1.0, false, ""));
    }

    public void moveNode(int id, double x, double y) {
        int i = nodeIndex(id);
        DetectedGraph.Node n = nodes.get(i);
        nodes.set(i, new DetectedGraph.Node(id, n.label(), x, y, n.confidence(), n.generatedLabel(), n.reason()));
    }

    public int addNode(String label, double x, double y) {
        int id = 0;
        for (DetectedGraph.Node n : nodes) {
            id = Math.max(id, n.id() + 1);
        }
        nodes.add(new DetectedGraph.Node(id, label.trim(), x, y, 1.0, false, ""));
        return id;
    }

    /** Removes the node and every edge that touches it. */
    public void deleteNode(int id) {
        nodes.remove(nodeIndex(id));
        edges.removeIf(e -> e.from() == id || e.to() == id);
    }

    public void confirmNode(int id) {
        int i = nodeIndex(id);
        DetectedGraph.Node n = nodes.get(i);
        nodes.set(i, new DetectedGraph.Node(id, n.label(), n.x(), n.y(), 1.0, n.generatedLabel(), ""));
    }

    public void setWeight(int edgeId, double weight) {
        int i = edgeIndex(edgeId);
        DetectedGraph.Edge e = edges.get(i);
        if (!weighted && weight != 1.0) {
            weighted = true;
        }
        edges.set(i, new DetectedGraph.Edge(edgeId, e.from(), e.to(), weight, e.confidence(), 1.0, ""));
    }

    public void confirmEdge(int edgeId) {
        int i = edgeIndex(edgeId);
        DetectedGraph.Edge e = edges.get(i);
        edges.set(i, new DetectedGraph.Edge(edgeId, e.from(), e.to(), e.weight(), 1.0, e.weightConfidence(), ""));
    }

    /** Accepts the weight as read. A missing weight cannot be accepted; type one with {@link #setWeight}. */
    public void confirmWeight(int edgeId) {
        int i = edgeIndex(edgeId);
        DetectedGraph.Edge e = edges.get(i);
        if (e.weight() == null) {
            throw new IllegalStateException("This edge has no weight to accept. Type one.");
        }
        edges.set(i, new DetectedGraph.Edge(edgeId, e.from(), e.to(), e.weight(), e.confidence(), 1.0, ""));
    }

    public void deleteEdge(int edgeId) {
        edges.remove(edgeIndex(edgeId));
    }

    /** @return the new edge id */
    public int addEdge(int from, int to, Double weight) {
        nodeIndex(from);
        nodeIndex(to);
        if (from == to) {
            throw new IllegalArgumentException("A node cannot be joined to itself.");
        }
        for (DetectedGraph.Edge e : edges) {
            boolean same = e.from() == from && e.to() == to;
            boolean flipped = e.from() == to && e.to() == from;
            if (same || (!directed && flipped)) {
                throw new IllegalArgumentException("These nodes are already joined.");
            }
        }
        int id = 0;
        for (DetectedGraph.Edge e : edges) {
            id = Math.max(id, e.id() + 1);
        }
        double w = weight == null ? 1.0 : weight;
        if (weight != null && weight != 1.0) {
            weighted = true;
        }
        edges.add(new DetectedGraph.Edge(id, from, to, weighted ? w : 1.0, 1.0, 1.0, ""));
        return id;
    }

    public void reverseEdge(int edgeId) {
        int i = edgeIndex(edgeId);
        DetectedGraph.Edge e = edges.get(i);
        for (DetectedGraph.Edge o : edges) {
            if (directed && o.from() == e.to() && o.to() == e.from()) {
                throw new IllegalArgumentException("The opposite edge already exists.");
            }
        }
        edges.set(i, new DetectedGraph.Edge(edgeId, e.to(), e.from(), e.weight(), 1.0, e.weightConfidence(), e.reason()));
    }

    /** Sets direction. Going to undirected joins an edge and its opposite into one (the first one is kept). */
    public void setDirected(boolean value) {
        directedConfidence = 1.0;
        if (directed && !value) {
            Set<Long> seen = new HashSet<>();
            edges.removeIf(e -> !seen.add((long) Math.min(e.from(), e.to()) * 100000 + Math.max(e.from(), e.to())));
        }
        directed = value;
    }

    public void confirmDirection() {
        directedConfidence = 1.0;
    }

    /** Turning weights off sets every weight to 1. Turning them on keeps the numbers and asks for any that are missing. */
    public void setWeighted(boolean value) {
        if (!value) {
            for (int i = 0; i < edges.size(); i++) {
                DetectedGraph.Edge e = edges.get(i);
                edges.set(i, new DetectedGraph.Edge(e.id(), e.from(), e.to(), 1.0, e.confidence(), 1.0, ""));
            }
        } else {
            for (int i = 0; i < edges.size(); i++) {
                DetectedGraph.Edge e = edges.get(i);
                if (!weighted && e.weight() != null) {
                    edges.set(i, new DetectedGraph.Edge(e.id(), e.from(), e.to(), e.weight(), e.confidence(), e.weightConfidence(), e.reason()));
                }
            }
        }
        weighted = value;
    }

    public void dismissWarning(int index) {
        if (index < 0 || index >= warnings.size()) {
            throw new IllegalArgumentException("No such note: " + index);
        }
        dismissed.add(index);
    }

    /** Accepts everything as read. Items without a weight or with a bad name still block the build. */
    public void confirmAll() {
        for (int i = 0; i < nodes.size(); i++) {
            confirmNode(nodes.get(i).id());
        }
        for (int i = 0; i < edges.size(); i++) {
            DetectedGraph.Edge e = edges.get(i);
            edges.set(i, new DetectedGraph.Edge(e.id(), e.from(), e.to(), e.weight(), 1.0, e.weight() == null ? e.weightConfidence() : 1.0, ""));
        }
        directedConfidence = 1.0;
        for (int i = 0; i < warnings.size(); i++) {
            dismissed.add(i);
        }
    }

    // ---- result ----

    /** The reviewed graph. Throws when {@link #problems()} is not empty. Flags do not block: the person may accept them. */
    public GraphDocument build(String name) throws GraphFormatException {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new GraphFormatException(problems.get(0));
        }
        List<Node> out = new ArrayList<>();
        int[] newId = new int[maxId() + 1];
        for (int i = 0; i < nodes.size(); i++) {
            DetectedGraph.Node n = nodes.get(i);
            newId[n.id()] = i;
            out.add(new Node(i, n.x(), n.y(), n.label().trim()));
        }
        List<Node> placed = hasPositions ? GraphLayouts.fitForeign(scaleToWorld(out)) : GraphLayouts.circle(out);
        List<Edge> es = new ArrayList<>();
        for (DetectedGraph.Edge e : edges) {
            es.add(new Edge(es.size(), newId[e.from()], newId[e.to()], weighted ? e.weight() : 1.0));
        }
        try {
            return new GraphDocument(name, weighted, new Graph(directed, placed, es));
        } catch (IllegalArgumentException ex) {
            throw new GraphFormatException(ex.getMessage(), ex);
        }
    }

    /** Pixels to editor units: the picture is centred and shrunk or grown to fit about 900 units, never overlapping nodes. */
    static List<Node> scaleToWorld(List<Node> in) {
        if (in.size() < 2) {
            return in.isEmpty() ? in : List.of(new Node(in.get(0).id(), 0, 0, in.get(0).label()));
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < in.size(); i++) {
            Node a = in.get(i);
            minX = Math.min(minX, a.x());
            maxX = Math.max(maxX, a.x());
            minY = Math.min(minY, a.y());
            maxY = Math.max(maxY, a.y());
            for (int j = i + 1; j < in.size(); j++) {
                closest = Math.min(closest, Math.hypot(a.x() - in.get(j).x(), a.y() - in.get(j).y()));
            }
        }
        double extent = Math.max(Math.max(maxX - minX, maxY - minY), 1);
        if (closest == 0) {
            return in;
        }
        double scale = 900.0 / extent;
        if (closest * scale < 1.3 * GraphLayouts.MIN_SPACING) {
            scale = 1.3 * GraphLayouts.MIN_SPACING / closest;
        }
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        List<Node> out = new ArrayList<>();
        for (Node n : in) {
            out.add(new Node(n.id(), Math.round((n.x() - cx) * scale * 10) / 10.0, Math.round((n.y() - cy) * scale * 10) / 10.0, n.label()));
        }
        return out;
    }

    private int maxId() {
        int m = 0;
        for (DetectedGraph.Node n : nodes) {
            m = Math.max(m, n.id());
        }
        return m;
    }

    private int nodeIndex(int id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id() == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("No node with id " + id);
    }

    private int edgeIndex(int id) {
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i).id() == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("No edge with id " + id);
    }

    private static String orElse(String s, String fallback) {
        return s == null || s.isEmpty() ? fallback : s;
    }
}

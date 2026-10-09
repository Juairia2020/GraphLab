package com.graphlab.engine.edit;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * The editable graph behind the editor screen. {@link Graph} is immutable, so the editor keeps
 * its own mutable lists and hands out a fresh {@link Graph} with {@link #toGraph()} when an
 * algorithm needs one.
 *
 * <p>Rules that keep every result valid for the engine: at most {@link Graph#MAX_NODES} nodes,
 * no self-loops, no parallel edges (the same pair twice; in an undirected graph either order
 * counts as the same pair) and only finite weights. Node ids and edge ids only ever grow, so
 * an id is never reused after a delete; labels (A, B, C...) are reused, smallest free first.
 *
 * <p>The accessors with an index ({@link #nodeAt}, {@link #edgeAt}, {@link #edgeFromIndex} ...)
 * allocate nothing, so the view can call them from onDraw.
 */
public final class GraphEditor {
    public static final int MAX_NODES = Graph.MAX_NODES;
    public static final double DEFAULT_WEIGHT = 1.0;

    /** Why two nodes can or cannot be connected. */
    public enum ConnectCheck { OK, SAME_NODE, MISSING_NODE, EXISTS }

    private boolean directed;
    private boolean weighted;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final ArrayList<Edge> edges = new ArrayList<>();
    private int nextNodeId;
    private int nextEdgeId;
    private int version;
    private int structureVersion;

    private boolean derivedDirty = true;
    private int[] edgeFromIdx = new int[0];
    private int[] edgeToIdx = new int[0];
    private boolean[] edgeReciprocal = new boolean[0];

    public GraphEditor(boolean directed, boolean weighted) {
        this.directed = directed;
        this.weighted = weighted;
    }

    /** A letter label: 0 is A, 25 is Z, 26 is A1, 27 is B1 ... so 60 nodes always get distinct labels. */
    public static String labelFor(int index) {
        char letter = (char) ('A' + index % 26);
        int round = index / 26;
        return round == 0 ? String.valueOf(letter) : letter + String.valueOf(round);
    }

    // ---- whole graph -------------------------------------------------------------------

    /** Replaces the content with a copy of {@code graph}. */
    public void load(Graph graph, boolean weighted) {
        nodes.clear();
        edges.clear();
        nextNodeId = 0;
        nextEdgeId = 0;
        directed = graph.isDirected();
        this.weighted = weighted;
        for (Node node : graph.nodes()) {
            nodes.add(node);
            nextNodeId = Math.max(nextNodeId, node.id() + 1);
        }
        for (Edge edge : graph.edges()) {
            edges.add(edge);
            nextEdgeId = Math.max(nextEdgeId, edge.id() + 1);
        }
        structureChanged();
    }

    public static GraphEditor from(Graph graph, boolean weighted) {
        GraphEditor editor = new GraphEditor(graph.isDirected(), weighted);
        editor.load(graph, weighted);
        return editor;
    }

    /**
     * The graph an algorithm should run on. When the editor is not weighted every edge has
     * weight 1 (the typed weights are kept, so switching back restores them).
     */
    public Graph toGraph() {
        List<Edge> out = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            out.add(weighted ? edge : new Edge(edge.id(), edge.from(), edge.to(), DEFAULT_WEIGHT));
        }
        return new Graph(directed, new ArrayList<>(nodes), out);
    }

    public void clear() {
        nodes.clear();
        edges.clear();
        nextNodeId = 0;
        nextEdgeId = 0;
        structureChanged();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    // ---- settings ----------------------------------------------------------------------

    public boolean isDirected() {
        return directed;
    }

    public boolean isWeighted() {
        return weighted;
    }

    public void setWeighted(boolean value) {
        if (value != weighted) {
            weighted = value;
            structureChanged();
        }
    }

    /**
     * Switches between directed and undirected. Going to undirected merges each reciprocal
     * pair (A to B and B to A) into the edge that was added first.
     *
     * @return how many edges were removed by merging
     */
    public int setDirected(boolean value) {
        if (value == directed) {
            return 0;
        }
        int removed = 0;
        if (!value) {
            for (int i = edges.size() - 1; i >= 0; i--) {
                Edge later = edges.get(i);
                for (int j = 0; j < i; j++) {
                    Edge earlier = edges.get(j);
                    if (earlier.from() == later.to() && earlier.to() == later.from()) {
                        edges.remove(i);
                        removed++;
                        break;
                    }
                }
            }
        }
        directed = value;
        structureChanged();
        return removed;
    }

    // ---- nodes -------------------------------------------------------------------------

    public boolean canAddNode() {
        return nodes.size() < MAX_NODES;
    }

    /** True when (x, y) is far enough from every node to put a new one there. */
    public boolean isFreeSpot(double x, double y) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (Math.hypot(node.x() - x, node.y() - y) < EditorMetrics.MIN_NODE_GAP) {
                return false;
            }
        }
        return true;
    }

    /** @return the new node's id, or -1 when the graph already has {@link #MAX_NODES} nodes */
    public int addNode(double x, double y) {
        if (!canAddNode()) {
            return -1;
        }
        int id = nextNodeId++;
        nodes.add(new Node(id, x, y, nextFreeLabel()));
        structureChanged();
        return id;
    }

    public boolean moveNode(int id, double x, double y) {
        int index = indexOfNode(id);
        if (index < 0) {
            return false;
        }
        Node old = nodes.get(index);
        nodes.set(index, new Node(old.id(), x, y, old.label()));
        version++;
        return true;
    }

    /** Removes a node and every edge that touches it. */
    public boolean removeNode(int id) {
        int index = indexOfNode(id);
        if (index < 0) {
            return false;
        }
        nodes.remove(index);
        for (int i = edges.size() - 1; i >= 0; i--) {
            Edge edge = edges.get(i);
            if (edge.from() == id || edge.to() == id) {
                edges.remove(i);
            }
        }
        structureChanged();
        return true;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public Node nodeAt(int index) {
        return nodes.get(index);
    }

    /** @return the node, or null when there is none with this id */
    public Node findNode(int id) {
        int index = indexOfNode(id);
        return index < 0 ? null : nodes.get(index);
    }

    public int indexOfNode(int id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    // ---- edges -------------------------------------------------------------------------

    public ConnectCheck checkConnect(int from, int to) {
        if (from == to) {
            return ConnectCheck.SAME_NODE;
        }
        if (indexOfNode(from) < 0 || indexOfNode(to) < 0) {
            return ConnectCheck.MISSING_NODE;
        }
        if (edgeBetween(from, to) >= 0) {
            return ConnectCheck.EXISTS;
        }
        return ConnectCheck.OK;
    }

    /** The id of the edge joining the two nodes (either order if undirected), or -1. */
    public int edgeBetween(int from, int to) {
        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            if (edge.from() == from && edge.to() == to) {
                return edge.id();
            }
            if (!directed && edge.from() == to && edge.to() == from) {
                return edge.id();
            }
        }
        return -1;
    }

    /**
     * @return the new edge's id
     * @throws IllegalArgumentException if {@link #checkConnect} is not OK or the weight is not finite
     */
    public int addEdge(int from, int to, double weight) {
        ConnectCheck check = checkConnect(from, to);
        if (check != ConnectCheck.OK) {
            throw new IllegalArgumentException("Cannot connect " + from + " and " + to + ": " + check);
        }
        int id = nextEdgeId;
        Edge edge = new Edge(id, from, to, weight);   // throws if the weight is not finite
        nextEdgeId++;
        edges.add(edge);
        structureChanged();
        return id;
    }

    public boolean setEdgeWeight(int id, double weight) {
        int index = indexOfEdge(id);
        if (index < 0) {
            return false;
        }
        Edge old = edges.get(index);
        edges.set(index, new Edge(old.id(), old.from(), old.to(), weight));
        structureChanged();
        return true;
    }

    public boolean removeEdge(int id) {
        int index = indexOfEdge(id);
        if (index < 0) {
            return false;
        }
        edges.remove(index);
        structureChanged();
        return true;
    }

    public int edgeCount() {
        return edges.size();
    }

    public Edge edgeAt(int index) {
        return edges.get(index);
    }

    /** @return the edge, or null when there is none with this id */
    public Edge findEdge(int id) {
        int index = indexOfEdge(id);
        return index < 0 ? null : edges.get(index);
    }

    public int indexOfEdge(int id) {
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    /** Index into the node list of the edge's start. Stable until the structure changes. */
    public int edgeFromIndex(int edgeIndex) {
        ensureDerived();
        return edgeFromIdx[edgeIndex];
    }

    public int edgeToIndex(int edgeIndex) {
        ensureDerived();
        return edgeToIdx[edgeIndex];
    }

    /** How many pairs of opposite edges (A to B and B to A) a directed graph has. 0 when undirected. */
    public int reciprocalPairCount() {
        if (!directed) {
            return 0;
        }
        int pairs = 0;
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                if (edges.get(i).from() == edges.get(j).to() && edges.get(i).to() == edges.get(j).from()) {
                    pairs++;
                }
            }
        }
        return pairs;
    }

    /** True when the graph is directed and the opposite edge also exists: draw both curved. */
    public boolean edgeIsReciprocal(int edgeIndex) {
        ensureDerived();
        return edgeReciprocal[edgeIndex];
    }

    // ---- layout helpers ----------------------------------------------------------------

    /** Multiplies every position by {@code factor}. Used to spread out the 0 to 100 sample graph. */
    public void scalePositions(double factor) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            nodes.set(i, new Node(node.id(), node.x() * factor, node.y() * factor, node.label()));
        }
        version++;
    }

    /** {minX, minY, maxX, maxY} over the node centres, or null for an empty graph. */
    public double[] bounds() {
        if (nodes.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x());
            maxY = Math.max(maxY, node.y());
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    // ---- change tracking ---------------------------------------------------------------

    /** Goes up on every change, including node moves. */
    public int version() {
        return version;
    }

    /** Goes up when nodes, edges, weights or settings change, but not when a node only moves. */
    public int structureVersion() {
        return structureVersion;
    }

    // ---- internals ---------------------------------------------------------------------

    private void structureChanged() {
        version++;
        structureVersion++;
        derivedDirty = true;
    }

    private String nextFreeLabel() {
        for (int i = 0; ; i++) {
            String label = labelFor(i);
            boolean used = false;
            for (int j = 0; j < nodes.size(); j++) {
                if (nodes.get(j).label().equals(label)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return label;
            }
        }
    }

    private void ensureDerived() {
        if (!derivedDirty) {
            return;
        }
        int n = edges.size();
        edgeFromIdx = new int[n];
        edgeToIdx = new int[n];
        edgeReciprocal = new boolean[n];
        for (int i = 0; i < n; i++) {
            Edge edge = edges.get(i);
            edgeFromIdx[i] = indexOfNode(edge.from());
            edgeToIdx[i] = indexOfNode(edge.to());
            if (directed) {
                for (int j = 0; j < n; j++) {
                    Edge other = edges.get(j);
                    if (other.from() == edge.to() && other.to() == edge.from()) {
                        edgeReciprocal[i] = true;
                        break;
                    }
                }
            }
        }
        derivedDirty = false;
    }
}

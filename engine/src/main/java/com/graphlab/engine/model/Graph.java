package com.graphlab.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable weighted graph, directed or undirected. Validated on construction, so every
 * algorithm can assume ids are unique and every edge points at real nodes.
 *
 * <p>Adjacency lists are built on first use and then cached. Neighbour order always follows
 * edge order, which is what makes every algorithm's trace deterministic.
 */
public final class Graph {
    /** Keeps traces small: every step stores a whole frame. */
    public static final int MAX_NODES = 60;

    private final boolean directed;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final Map<Integer, Node> nodeById;
    private final Map<Integer, Edge> edgeById;

    private List<Arc> arcs;                        // built on first use
    private Map<Integer, List<Arc>> adjacency;     // built on first use

    public Graph(boolean directed, List<Node> nodes, List<Edge> edges) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        if (nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException(
                    "A graph can have at most " + MAX_NODES + " nodes, got " + nodes.size());
        }
        this.directed = directed;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);

        Map<Integer, Node> byId = new LinkedHashMap<>();
        for (Node node : this.nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id " + node.id());
            }
        }
        Map<Integer, Edge> edgeMap = new LinkedHashMap<>();
        for (Edge edge : this.edges) {
            if (edgeMap.put(edge.id(), edge) != null) {
                throw new IllegalArgumentException("Duplicate edge id " + edge.id());
            }
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) {
                throw new IllegalArgumentException(
                        "Edge " + edge.id() + " uses a node that does not exist");
            }
        }
        this.nodeById = Collections.unmodifiableMap(byId);
        this.edgeById = Collections.unmodifiableMap(edgeMap);
    }

    public boolean isDirected() {
        return directed;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public boolean hasNode(int id) {
        return nodeById.containsKey(id);
    }

    public Node node(int id) {
        Node node = nodeById.get(id);
        if (node == null) {
            throw new IllegalArgumentException("No node with id " + id);
        }
        return node;
    }

    public Edge edge(int id) {
        Edge edge = edgeById.get(id);
        if (edge == null) {
            throw new IllegalArgumentException("No edge with id " + id);
        }
        return edge;
    }

    /** Every arc in the graph, in edge order (an undirected edge contributes from-to, then to-from). */
    public synchronized List<Arc> arcs() {
        if (arcs == null) {
            List<Arc> built = new ArrayList<>();
            for (Edge edge : edges) {
                built.add(new Arc(edge, edge.from(), edge.to()));
                if (!directed && edge.from() != edge.to()) {
                    built.add(new Arc(edge, edge.to(), edge.from()));
                }
            }
            arcs = Collections.unmodifiableList(built);
        }
        return arcs;
    }

    /** The arcs leaving {@code nodeId}, in edge order. Isolated nodes give an empty list. */
    public synchronized List<Arc> arcsFrom(int nodeId) {
        if (adjacency == null) {
            Map<Integer, List<Arc>> built = new LinkedHashMap<>();
            for (Node node : nodes) {
                built.put(node.id(), new ArrayList<>());
            }
            for (Arc arc : arcs()) {
                built.get(arc.from()).add(arc);
            }
            Map<Integer, List<Arc>> frozen = new LinkedHashMap<>();
            built.forEach((id, list) -> frozen.put(id, Collections.unmodifiableList(list)));
            adjacency = Collections.unmodifiableMap(frozen);
        }
        List<Arc> result = adjacency.get(nodeId);
        if (result == null) {
            throw new IllegalArgumentException("No node with id " + nodeId);
        }
        return result;
    }

    /** The node's label, or "#id" when the label is empty. Used in step messages. */
    public String nodeName(int id) {
        String label = node(id).label();
        return label.isEmpty() ? "#" + id : label;
    }

    /** "A-B" for an undirected travel, "A->B" for a directed one. */
    public String arcName(Arc arc) {
        return nodeName(arc.from()) + (directed ? "->" : "-") + nodeName(arc.to());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Graph that)) {
            return false;
        }
        return directed == that.directed && nodes.equals(that.nodes) && edges.equals(that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directed, nodes, edges);
    }

    @Override
    public String toString() {
        return (directed ? "Directed" : "Undirected") + " graph with " + nodes.size()
                + " nodes and " + edges.size() + " edges";
    }

    public static Builder builder(boolean directed) {
        return new Builder(directed);
    }

    /** Convenient way to build graphs in code and tests. */
    public static final class Builder {
        private final boolean directed;
        private final List<Node> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private int nextNodeId;
        private int nextEdgeId;

        private Builder(boolean directed) {
            this.directed = directed;
        }

        public Builder node(int id, double x, double y, String label) {
            nodes.add(new Node(id, x, y, label));
            nextNodeId = Math.max(nextNodeId, id + 1);
            return this;
        }

        /** Adds {@code count} nodes labelled A, B, C... placed on a circle. Handy in tests. */
        public Builder nodes(int count) {
            int first = nextNodeId;
            for (int i = 0; i < count; i++) {
                int id = first + i;
                double angle = 2 * Math.PI * i / Math.max(count, 1);
                String label = id < 26 ? String.valueOf((char) ('A' + id)) : "N" + id;
                node(id, 50 + 40 * Math.cos(angle), 50 + 40 * Math.sin(angle), label);
            }
            return this;
        }

        public Builder edge(int id, int from, int to, double weight) {
            edges.add(new Edge(id, from, to, weight));
            nextEdgeId = Math.max(nextEdgeId, id + 1);
            return this;
        }

        /** Adds an edge with the next free id. */
        public Builder edge(int from, int to, double weight) {
            return edge(nextEdgeId, from, to, weight);
        }

        public Graph build() {
            return new Graph(directed, nodes, edges);
        }
    }
}

package com.graphlab.engine.io;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks every importer runs before it builds a {@link Graph}. {@code Graph} itself accepts
 * self-loops and parallel edges, but the editor does not (it cannot draw or edit them), so a file
 * holding them is refused here with a message that names the nodes involved.
 */
final class GraphChecks {
    private GraphChecks() {
    }

    static Graph build(boolean directed, List<Node> nodes, List<Edge> edges) throws GraphFormatException {
        if (nodes.size() > Graph.MAX_NODES) {
            throw new GraphFormatException("The graph has " + nodes.size()
                    + " nodes, but GraphLab supports at most " + Graph.MAX_NODES + ".");
        }
        Map<Integer, Node> byId = new HashMap<>();
        for (Node node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new GraphFormatException("Two nodes share the id " + node.id() + ".");
            }
        }
        Set<Integer> edgeIds = new HashSet<>();
        Map<Long, Integer> pairs = new HashMap<>();
        for (Edge edge : edges) {
            if (!edgeIds.add(edge.id())) {
                throw new GraphFormatException("Two edges share the id " + edge.id() + ".");
            }
            Node from = byId.get(edge.from());
            Node to = byId.get(edge.to());
            if (from == null || to == null) {
                throw new GraphFormatException("Edge " + edge.id() + " uses a node that does not exist.");
            }
            if (edge.from() == edge.to()) {
                throw new GraphFormatException("Edge " + edge.id() + " joins node " + name(from)
                        + " to itself. GraphLab does not support self-loops.");
            }
            long a = edge.from();
            long b = edge.to();
            if (!directed && a > b) {
                long swap = a;
                a = b;
                b = swap;
            }
            Integer earlier = pairs.put(a * 2_147_483_648L + b, edge.id());
            if (earlier != null) {
                throw new GraphFormatException("Edges " + earlier + " and " + edge.id() + " both join "
                        + name(from) + " and " + name(to)
                        + ". GraphLab does not support parallel edges.");
            }
        }
        try {
            return new Graph(directed, nodes, edges);
        } catch (IllegalArgumentException e) {
            throw new GraphFormatException(e.getMessage(), e);
        }
    }

    private static String name(Node node) {
        return node.label().isEmpty() ? "#" + node.id() : node.label();
    }
}

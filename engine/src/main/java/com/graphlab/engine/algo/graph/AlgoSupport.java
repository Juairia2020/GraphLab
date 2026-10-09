package com.graphlab.engine.algo.graph;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;

/** Input checks shared by the graph algorithms. Every failure is an IllegalArgumentException with a readable message. */
final class AlgoSupport {
    private AlgoSupport() {
    }

    static void requireSource(Graph graph, Params params, String algorithmName) {
        if (!params.hasSource()) {
            throw new IllegalArgumentException(algorithmName + " needs a start node");
        }
        if (!graph.hasNode(params.source())) {
            throw new IllegalArgumentException(
                    "Start node " + params.source() + " is not in the graph");
        }
    }

    static void requireNonEmpty(Graph graph, String algorithmName) {
        if (graph.nodeCount() == 0) {
            throw new IllegalArgumentException(algorithmName + " needs a graph with at least one node");
        }
    }

    static void requireUndirected(Graph graph, String algorithmName) {
        if (graph.isDirected()) {
            throw new IllegalArgumentException(
                    algorithmName + " works on undirected graphs only; this graph is directed");
        }
    }

    static void requireNonNegativeWeights(Graph graph, String algorithmName, String alternative) {
        for (Edge edge : graph.edges()) {
            if (edge.weight() < 0) {
                throw new IllegalArgumentException(
                        algorithmName + " needs non-negative edge weights, but edge "
                                + graph.nodeName(edge.from()) + (graph.isDirected() ? "->" : "-")
                                + graph.nodeName(edge.to()) + " has weight " + edge.weight()
                                + ". Try " + alternative + " instead.");
            }
        }
    }
}

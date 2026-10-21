package com.graphlab.engine.imageimport;

import java.util.List;

/**
 * What the importer thinks the picture shows, before the person has looked at it. Every node, edge and weight carries a
 * confidence from 0 to 1; {@link ReviewSession} flags those below {@link #REVIEW_BELOW} so the person checks them.
 *
 * @param hasPositions true when the node positions come from the picture (geometry); false for a matrix or an edge list
 * @param weighted     false when no weight was found, so every edge counts 1
 */
public record DetectedGraph(List<Node> nodes, List<Edge> edges, boolean directed, double directedConfidence, boolean weighted,
                            boolean hasPositions, Source source, List<String> warnings) {
    /** Items with a confidence under this are shown in amber for review. */
    public static final double REVIEW_BELOW = 0.75;

    public enum Source { MATRIX, EDGE_LIST, PICTURE, DEMO }

    /** @param generatedLabel true when the picture had no readable name and the importer chose one (A, B, C ...) */
    public record Node(int id, String label, double x, double y, double confidence, boolean generatedLabel, String reason) {
    }

    /** @param weight null when none could be read; {@code reason} says why the edge or its weight is doubtful, or is empty */
    public record Edge(int id, int from, int to, Double weight, double confidence, double weightConfidence, String reason) {
    }

    public DetectedGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        warnings = List.copyOf(warnings);
    }
}

package com.graphlab.engine.preset;

import java.util.List;

/**
 * The answer of one tool on one preset, ready for a result sheet. {@code value} is the max flow, the tree cost or the route
 * length. {@code highlightEdges} are the edges to draw (the min cut for flow, the tree, the route); {@code route} is the
 * node ids of the route (empty for the other tools); {@code flowEdges} lists edges that carry flow; {@code reachable} is
 * false only when a route does not exist.
 */
public record PresetResult(PresetTool tool, double value, boolean reachable, List<Integer> highlightEdges,
                           List<Integer> route, List<Integer> flowEdges, String traceAlgorithmId) {
    public PresetResult {
        highlightEdges = List.copyOf(highlightEdges);
        route = List.copyOf(route);
        flowEdges = List.copyOf(flowEdges);
    }
}

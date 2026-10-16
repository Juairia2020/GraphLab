package com.graphlab.engine.preset;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.List;

/** Runs a preset tool with the real engine algorithm. The same trace can be opened in the Lab. */
public final class PresetRunner {
    private PresetRunner() {
    }

    /** The engine trace of the tool, for the Lab replay. */
    public static Trace trace(Preset preset, PresetTool tool) {
        Graph graph = preset.graph();
        Params params = switch (tool) {
            case MAX_FLOW, SHORTEST_ROUTE -> new Params(preset.source(), 0L, preset.target());
            case MST -> Params.from(preset.source());
        };
        return Algorithms.byId(tool.algorithmId()).run(graph, params);
    }

    public static PresetResult run(Preset preset, PresetTool tool) {
        if (!preset.tools().contains(tool)) {
            throw new IllegalArgumentException("Preset " + preset.id() + " does not offer " + tool.id());
        }
        Trace trace = trace(preset, tool);
        Result r = trace.result();
        Graph graph = preset.graph();
        switch (tool) {
            case MAX_FLOW: {
                List<Integer> carrying = new ArrayList<>();
                for (Edge e : graph.edges()) {
                    Double f = r.edgeFlow().get(e.id());
                    if (f != null && Math.abs(f) > 1e-9) {
                        carrying.add(e.id());
                    }
                }
                return new PresetResult(tool, r.value(), true, r.cutEdgeIds(), List.of(), carrying, trace.algorithmId());
            }
            case MST:
                return new PresetResult(tool, r.totalWeight(), true, r.treeEdgeIds(), List.of(), List.of(), trace.algorithmId());
            default: {
                List<Integer> route = r.pathTo(graph, preset.target());
                boolean reached = !route.isEmpty();
                List<Integer> edges = new ArrayList<>();
                for (int i = 1; i < route.size(); i++) {
                    edges.add(r.parentEdge().get(route.get(i)));
                }
                double length = reached ? r.distances().get(preset.target()) : 0.0;
                return new PresetResult(tool, length, reached, edges, route, List.of(), trace.algorithmId());
            }
        }
    }
}

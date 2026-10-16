package com.graphlab.engine.preset;

/** What a Toolkit preset can be asked. Each tool is one engine algorithm run on the preset's graph. */
public enum PresetTool {
    MAX_FLOW("max-flow", "edmonds-karp"),
    MST("mst", "kruskal"),
    SHORTEST_ROUTE("shortest-route", "dijkstra");

    private final String id;
    private final String algorithmId;

    PresetTool(String id, String algorithmId) {
        this.id = id;
        this.algorithmId = algorithmId;
    }

    public String id() {
        return id;
    }

    public String algorithmId() {
        return algorithmId;
    }

    public static PresetTool fromId(String id) {
        for (PresetTool tool : values()) {
            if (tool.id.equals(id)) {
                return tool;
            }
        }
        return null;
    }
}

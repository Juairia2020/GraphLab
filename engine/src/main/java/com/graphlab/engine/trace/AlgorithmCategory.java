package com.graphlab.engine.trace;

/** The families the algorithm picker groups by. The order here is the order shown. */
public enum AlgorithmCategory {
    GRAPH_SEARCH("Graph search"),
    SHORTEST_PATHS("Shortest paths"),
    SPANNING_TREES("Spanning trees"),
    NETWORK_FLOW("Flow and cuts"),
    GREEDY("Greedy"),
    BACKTRACKING("Backtracking"),
    DIVIDE_AND_CONQUER("Divide and conquer"),
    RANDOMIZED("Randomized"),
    DYNAMIC_PROGRAMMING("Dynamic programming");

    private final String title;

    AlgorithmCategory(String title) {
        this.title = title;
    }

    /** The name shown on chips and headings. */
    public String title() {
        return title;
    }
}

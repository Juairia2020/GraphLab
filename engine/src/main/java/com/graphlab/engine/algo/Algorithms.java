package com.graphlab.engine.algo;

import com.graphlab.engine.algo.graph.BellmanFord;
import com.graphlab.engine.algo.graph.Bfs;
import com.graphlab.engine.algo.graph.Dfs;
import com.graphlab.engine.algo.graph.Dijkstra;
import com.graphlab.engine.algo.graph.Kruskal;
import com.graphlab.engine.algo.graph.Prim;
import com.graphlab.engine.trace.Algorithm;
import java.util.List;

/** The list of available algorithms. MS5 adds the rest and groups them into categories. */
public final class Algorithms {
    private static final List<Algorithm> ALL = List.of(
            new Bfs(), new Dfs(), new Dijkstra(), new BellmanFord(), new Kruskal(), new Prim());

    private Algorithms() {
    }

    public static List<Algorithm> all() {
        return ALL;
    }

    /** @throws IllegalArgumentException if no algorithm has this id */
    public static Algorithm byId(String id) {
        for (Algorithm algorithm : ALL) {
            if (algorithm.id().equals(id)) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm id: " + id);
    }
}

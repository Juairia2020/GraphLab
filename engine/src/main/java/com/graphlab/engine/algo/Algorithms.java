package com.graphlab.engine.algo;

import com.graphlab.engine.algo.array.MergeSort;
import com.graphlab.engine.algo.array.QuickSort;
import com.graphlab.engine.algo.array.RandomizedQuickSort;
import com.graphlab.engine.algo.dp.CoinChange;
import com.graphlab.engine.algo.dp.EditDistance;
import com.graphlab.engine.algo.dp.Lcs;
import com.graphlab.engine.algo.dp.Lis;
import com.graphlab.engine.algo.dp.MatrixChain;
import com.graphlab.engine.algo.extra.Huffman;
import com.graphlab.engine.algo.extra.ReservoirSampling;
import com.graphlab.engine.algo.graph.BipartiteMatching;
import com.graphlab.engine.algo.graph.ClosestPair;
import com.graphlab.engine.algo.graph.Dinic;
import com.graphlab.engine.algo.graph.TarjanScc;
import com.graphlab.engine.algo.graph.TopologicalSort;
import com.graphlab.engine.algo.graph.BellmanFord;
import com.graphlab.engine.algo.graph.AStar;
import com.graphlab.engine.algo.graph.Bfs;
import com.graphlab.engine.algo.graph.Dfs;
import com.graphlab.engine.algo.graph.Dijkstra;
import com.graphlab.engine.algo.graph.EdmondsKarp;
import com.graphlab.engine.algo.graph.FloydWarshall;
import com.graphlab.engine.algo.graph.GraphColouring;
import com.graphlab.engine.algo.graph.Karger;
import com.graphlab.engine.algo.graph.Kruskal;
import com.graphlab.engine.algo.graph.Prim;
import com.graphlab.engine.algo.knapsack.FractionalKnapsack;
import com.graphlab.engine.algo.knapsack.Knapsack01;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import java.util.ArrayList;
import java.util.List;

/**
 * The 15 core algorithms and the four dynamic-programming additions of MS12, in the order the picker shows them: grouped by
 * {@link AlgorithmCategory}, and in teaching order inside a group.
 */
public final class Algorithms {
    private static final List<Algorithm> ALL = List.of(
            new Bfs(), new Dfs(), new TopologicalSort(), new TarjanScc(), // graph search
            new Dijkstra(), new AStar(), new BellmanFord(), new FloydWarshall(), // shortest paths
            new Kruskal(), new Prim(),                              // spanning trees
            new EdmondsKarp(), new Dinic(), new BipartiteMatching(), // flow and cuts
            new FractionalKnapsack(), new Huffman(),               // greedy
            new GraphColouring(),                                   // backtracking
            new MergeSort(), new QuickSort(), new ClosestPair(),     // divide and conquer
            new RandomizedQuickSort(), new Karger(), new ReservoirSampling(), // randomized
            new Knapsack01(), new CoinChange(),                     // dynamic programming
            new Lcs(), new EditDistance(), new Lis(), new MatrixChain());

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

    /** The algorithms of one family, in picker order. */
    public static List<Algorithm> inCategory(AlgorithmCategory category) {
        List<Algorithm> out = new ArrayList<>();
        for (Algorithm algorithm : ALL) {
            if (algorithm.category() == category) {
                out.add(algorithm);
            }
        }
        return out;
    }

    /** The families that have at least one algorithm, in picker order. */
    public static List<AlgorithmCategory> categories() {
        List<AlgorithmCategory> out = new ArrayList<>();
        for (AlgorithmCategory category : AlgorithmCategory.values()) {
            if (!inCategory(category).isEmpty()) {
                out.add(category);
            }
        }
        return out;
    }

    /** The algorithms that work on one kind of input (for example every sort that takes a list of numbers). */
    public static List<Algorithm> ofInputKind(InputKind kind) {
        List<Algorithm> out = new ArrayList<>();
        for (Algorithm algorithm : ALL) {
            if (algorithm.inputKind() == kind) {
                out.add(algorithm);
            }
        }
        return out;
    }
}

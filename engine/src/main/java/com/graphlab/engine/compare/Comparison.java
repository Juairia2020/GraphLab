package com.graphlab.engine.compare;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs two graph algorithms on the same graph and puts their numbers side by side: how many steps each took, how many
 * nodes it reached, the total weight of its tree, and its own counters at the end. When both are of one family
 * (Dijkstra with Bellman-Ford, Kruskal with Prim) it also says whether they gave the same answer. If one of them
 * refuses the graph (Dijkstra with a negative edge), that side carries the reason and the other side still runs: this is
 * the "see an algorithm fail" lesson. Plain Java, no Android.
 */
public final class Comparison {

    /** Whether the two answers match. */
    public enum Agreement { SAME_ANSWER, DIFFERENT_ANSWER, NOT_COMPARABLE }

    /**
     * What one algorithm did.
     *
     * @param error    why it could not run, or null
     * @param counters its counters on the last step, in the order the algorithm declared them
     * @param nanos    how long the run took, measured with System.nanoTime (0 when it could not run)
     * @param trace    the whole run, for playing it side by side (null when it could not run)
     */
    public record Side(String id, String name, String error, int steps, int reached, int treeEdges, double totalWeight,
                       Map<String, Long> counters, Result result, long nanos, Trace trace) {
        public boolean ran() {
            return error == null;
        }
    }

    public record Report(Side a, Side b, Agreement agreement) {
        /** The side with fewer steps, or null when they tie or one did not run. */
        public Side fewerSteps() {
            if (!a.ran() || !b.ran() || a.steps() == b.steps()) {
                return null;
            }
            return a.steps() < b.steps() ? a : b;
        }
    }

    private static final double EPS = 1e-9;

    private Comparison() {
    }

    /** The algorithms that can be compared: graph algorithms that need no end node (so not flow). */
    public static List<Algorithm> candidates() {
        List<Algorithm> out = new ArrayList<>();
        for (Algorithm algorithm : Algorithms.all()) {
            if (algorithm.inputKind() == com.graphlab.engine.input.InputKind.GRAPH && !algorithm.needsTarget()) {
                out.add(algorithm);
            }
        }
        return out;
    }

    /**
     * The algorithms the Compare screen offers: only those whose answers can be judged against each other (the two
     * shortest-distance algorithms, and the spanning-tree algorithms). Any {@link #candidates()} can still be run here.
     */
    public static List<Algorithm> comparable() {
        List<Algorithm> out = new ArrayList<>();
        for (Algorithm algorithm : candidates()) {
            if (algorithm.category() == AlgorithmCategory.SPANNING_TREES || algorithm.id().equals("dijkstra")
                    || algorithm.id().equals("bellman-ford")) {
                out.add(algorithm);
            }
        }
        return out;
    }

    /**
     * @param source the start node for algorithms that need one (ignored by the others)
     * @throws IllegalArgumentException for an unknown id, the same algorithm twice, or an algorithm that is not a
     *                                  candidate
     */
    public static Report run(Graph graph, String idA, String idB, int source) {
        if (idA.equals(idB)) {
            throw new IllegalArgumentException("Choose two different algorithms.");
        }
        Algorithm a = candidate(idA);
        Algorithm b = candidate(idB);
        Side sideA = runOne(a, graph, source);
        Side sideB = runOne(b, graph, source);
        return new Report(sideA, sideB, agree(graph, a, sideA, b, sideB));
    }

    private static Algorithm candidate(String id) {
        Algorithm algorithm = Algorithms.byId(id);
        if (!candidates().contains(algorithm)) {
            throw new IllegalArgumentException(algorithm.name() + " cannot be compared here.");
        }
        return algorithm;
    }

    private static Side runOne(Algorithm algorithm, Graph graph, int source) {
        Params params = algorithm.needsSource() ? Params.from(source).withSeed(1L) : Params.none().withSeed(1L);
        try {
            long started = System.nanoTime();
            Trace trace = algorithm.run(graph, params);
            long nanos = System.nanoTime() - started;
            Step last = trace.last();
            Result r = trace.result();
            return new Side(algorithm.id(), algorithm.name(), null, trace.size(), r.visitOrder().size(),
                    r.treeEdgeIds().size(), r.totalWeight(), last.counters(), r, nanos, trace);
        } catch (IllegalArgumentException e) {
            return new Side(algorithm.id(), algorithm.name(), e.getMessage(), 0, 0, 0, 0, Map.of(), null, 0L, null);
        }
    }

    private static Agreement agree(Graph graph, Algorithm a, Side sa, Algorithm b, Side sb) {
        if (!sa.ran() || !sb.ran()) {
            return Agreement.NOT_COMPARABLE;
        }
        if (a.category() == AlgorithmCategory.SPANNING_TREES && b.category() == AlgorithmCategory.SPANNING_TREES) {
            return Math.abs(sa.totalWeight() - sb.totalWeight()) <= EPS ? Agreement.SAME_ANSWER : Agreement.DIFFERENT_ANSWER;
        }
        boolean distA = a.id().equals("dijkstra") || a.id().equals("bellman-ford");
        boolean distB = b.id().equals("dijkstra") || b.id().equals("bellman-ford");
        if (distA && distB) {
            if (sa.result().negativeCycle() || sb.result().negativeCycle()) {
                return Agreement.NOT_COMPARABLE;
            }
            return sameDistances(graph, sa.result(), sb.result()) ? Agreement.SAME_ANSWER : Agreement.DIFFERENT_ANSWER;
        }
        return Agreement.NOT_COMPARABLE;
    }

    private static boolean sameDistances(Graph graph, Result x, Result y) {
        for (var node : graph.nodes()) {
            double dx = x.distances().getOrDefault(node.id(), Double.POSITIVE_INFINITY);
            double dy = y.distances().getOrDefault(node.id(), Double.POSITIVE_INFINITY);
            if (Double.isInfinite(dx) != Double.isInfinite(dy)) {
                return false;
            }
            if (!Double.isInfinite(dx) && Math.abs(dx - dy) > EPS) {
                return false;
            }
        }
        return true;
    }
}

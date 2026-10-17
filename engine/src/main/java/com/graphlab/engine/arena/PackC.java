package com.graphlab.engine.arena;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The two Priority 4 activities: Break the algorithm and Fog of war. Like every other question, the problem is random and
 * the right answer is worked out by running algorithms, not typed in. Both are button choices. Each method returns null when
 * the random problem is not good enough; the factory then tries again.
 */
final class PackC {
    private static final String ARROW = " → ";

    private PackC() {
    }

    static Question create(QuestionKind kind, Level level, Random r) {
        return switch (kind) {
            case BREAK_IT -> breakIt(level, r);
            case FOG_OF_WAR -> fogOfWar(level, r);
            default -> throw new IllegalArgumentException(kind + " is not in pack C");
        };
    }

    // =============================================================================
    // Break the algorithm: which one change to one edge makes Dijkstra give a wrong distance?
    // The reference solver is Bellman-Ford, which is correct for negative weights.
    // =============================================================================

    private static Question breakIt(Level level, Random r) {
        int n = level.minNodes() + r.nextInt(level.maxNodes() - level.minNodes() + 1);
        int extra = level.minExtraEdges() + r.nextInt(level.maxExtraEdges() - level.minExtraEdges() + 1) + 1;
        // Every edge goes from a lower to a higher node, so there is no cycle at all: a negative edge can never make a
        // negative cycle, and Bellman-Ford always has a true answer.
        Graph g = RandomGraphs.connected(r, n, extra, true, true, 1, Math.max(6, level.maxWeight()), false);
        int source = 0;
        int end = n - 1;
        int count = g.edgeCount();
        int[] newWeight = new int[count];
        boolean[] breaks = new boolean[count];
        double[] dijkstra = new double[count];
        double[] truth = new double[count];
        List<Integer> breaking = new ArrayList<>();
        List<Integer> fine = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            newWeight[i] = -(1 + r.nextInt(3));
            Graph changed = PackB.replaceWeight(g, g.edges().get(i).id(), newWeight[i]);
            dijkstra[i] = plainDijkstra(changed, source, end);
            truth[i] = Algorithms.byId("bellman-ford").run(changed, Params.from(source)).result().distances().get(end);
            breaks[i] = Math.abs(dijkstra[i] - truth[i]) > 1e-9;
            (breaks[i] ? breaking : fine).add(i);
        }
        if (breaking.isEmpty() || fine.size() < 3) {
            return null;
        }
        Collections.shuffle(breaking, r);
        Collections.shuffle(fine, r);
        List<Integer> pick = new ArrayList<>(fine.subList(0, 3));
        int right = breaking.get(0);
        pick.add(right);
        Collections.shuffle(pick, r);
        List<String> options = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        for (int i : pick) {
            String label = label(g, i, newWeight[i]);
            options.add(label);
            if (i != right) {
                detail.append(detail.length() == 0 ? "" : "; ").append(label).append(": Dijkstra and Bellman-Ford both give ")
                        .append(Choices.number(truth[i]));
            }
        }
        String rightLabel = label(g, right, newWeight[right]);
        String prompt = "Dijkstra settles the nearest unsettled node and never looks back, so it is only safe when no weight is "
                + "negative. You may change ONE edge to a negative weight (edges go only in the direction of the arrow). "
                + "Which change makes Dijkstra give a wrong shortest distance from " + g.nodeName(source) + " to "
                + g.nodeName(end) + "?";
        String why = rightLabel + " breaks it. Dijkstra reports " + Choices.number(dijkstra[right]) + ", but Bellman-Ford, "
                + "which handles negative weights, finds the true distance " + Choices.number(truth[right]) + ". Dijkstra had "
                + "already settled a node before the cheaper route through the negative edge reached it. The other changes "
                + "are harmless here (" + detail + ").";
        return new Question(QuestionKind.BREAK_IT, level, prompt, new GraphInput(g), source, end, options,
                options.indexOf(rightLabel), why, null, "dijkstra", null);
    }

    private static String label(Graph g, int edgeIndex, int weight) {
        Edge e = g.edges().get(edgeIndex);
        return g.nodeName(e.from()) + ARROW + g.nodeName(e.to()) + " becomes " + weight;
    }

    /**
     * The textbook Dijkstra: settle the nearest unsettled node, relax its out-edges, never revisit a settled node. It runs on
     * negative weights without complaint (the engine's own Dijkstra refuses them), which is exactly how it goes wrong.
     */
    static double plainDijkstra(Graph g, int source, int target) {
        int n = g.nodeCount();
        double[] dist = new double[n];
        boolean[] settled = new boolean[n];
        java.util.Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int v = 0; v < n; v++) {
                if (!settled[v] && Double.isFinite(dist[v]) && (u < 0 || dist[v] < dist[u])) {
                    u = v;
                }
            }
            if (u < 0) {
                break;
            }
            settled[u] = true;
            for (Edge e : g.edges()) {
                if (e.from() == u && !settled[e.to()] && dist[u] + e.weight() < dist[e.to()]) {
                    dist[e.to()] = dist[u] + e.weight();
                }
            }
        }
        return dist[target];
    }

    // =============================================================================
    // Fog of war: you see only the roads where you stand. Always taking the cheapest one: what does the trip cost?
    // =============================================================================

    private static Question fogOfWar(Level level, Random r) {
        int n = level.minNodes() + r.nextInt(level.maxNodes() - level.minNodes() + 1);
        int extra = level.minExtraEdges() + r.nextInt(level.maxExtraEdges() - level.minExtraEdges() + 1) + 1;
        int maxWeight = Math.max(level.maxWeight(), 2 * (n - 1 + extra));
        Graph g = RandomGraphs.connected(r, n, extra, false, false, 1, maxWeight, true);
        int source = 0;
        int end = n - 1;
        List<Integer> walk = new ArrayList<>();
        walk.add(source);
        boolean[] seen = new boolean[n];
        seen[source] = true;
        double cost = 0;
        int at = source;
        while (at != end) {
            Edge best = null;
            boolean tie = false;
            for (Edge e : g.edges()) {
                int other = e.from() == at ? e.to() : e.to() == at ? e.from() : -1;
                if (other < 0 || seen[other]) {
                    continue;
                }
                if (best == null || e.weight() < best.weight() - 1e-9) {
                    best = e;
                    tie = false;
                } else if (Math.abs(e.weight() - best.weight()) < 1e-9) {
                    tie = true;
                }
            }
            if (best == null || tie) {
                return null;   // the walker is stuck, or the rule does not pick one road
            }
            int next = best.from() == at ? best.to() : best.from();
            cost += best.weight();
            seen[next] = true;
            walk.add(next);
            at = next;
        }
        double optimal = Algorithms.byId("dijkstra").run(g, Params.from(source)).result().distances().get(end);
        if (cost <= optimal + 1e-9) {
            return null;   // the lesson needs the greedy trip to cost more than the best trip
        }
        List<String> wrong = Choices.wrongNumbers(r, cost, List.of(optimal), 1, 8);
        String right = Choices.number(cost);
        if (!Choices.enough(right, wrong, 4)) {
            return null;
        }
        Choices c = Choices.of(r, right, wrong, 4);
        StringBuilder route = new StringBuilder();
        for (int node : walk) {
            route.append(route.length() == 0 ? "" : ARROW).append(g.nodeName(node));
        }
        String prompt = "Fog of war: you can see only the roads at the town where you stand. Your rule is to always take the "
                + "cheapest road to a town you have not visited yet, until you reach " + g.nodeName(end) + ". The numbers are "
                + "the road costs. What does the whole trip from " + g.nodeName(source) + " cost?";
        String why = "The fog walker takes " + route + " and pays " + right + ". With the whole map, the best trip costs only "
                + Choices.number(optimal) + " (Dijkstra). Choosing the cheapest road you can see is a greedy rule: it is "
                + "fast, but with only local information it is not always optimal.";
        return new Question(QuestionKind.FOG_OF_WAR, level, prompt, new GraphInput(g), source, end, c.options,
                c.correctIndex, why, null, "dijkstra", null);
    }
}

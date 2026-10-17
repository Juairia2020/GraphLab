package com.graphlab.engine.arena;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.TextInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The six extra activities of MS17: Spot the bug, Complexity guess, Will it change, Reverse-engineer, Race bet and Cut the
 * network. Like every other question, the problem is random and the right answer is read from a real engine run. All six are
 * button choices. Each method returns null when the random problem is not good enough; the factory then tries again.
 */
final class PackB {
    static final String[] GROWTH = {"n", "n log n", "n²", "2ⁿ"};
    static final int[] SORT_SIZES = {2, 4, 8, 16};
    private static final String ARROW = " → ";

    private PackB() {
    }

    static Question create(QuestionKind kind, Level level, Random r) {
        return switch (kind) {
            case SPOT_BUG -> spotBug(level, r);
            case COMPLEXITY_GUESS -> complexity(level, r);
            case WILL_CHANGE -> willChange(level, r);
            case REVERSE_ENGINEER -> reverseEngineer(level, r);
            case RACE_BET -> raceBet(level, r);
            case CUT_NETWORK -> cutNetwork(level, r);
            default -> throw new IllegalArgumentException(kind + " is not in pack B");
        };
    }

    // =============================================================================
    // Spot the bug: a log of a real Dijkstra run, with one fault injected
    // =============================================================================

    static final int LOG_LINES = 4;

    private static Question spotBug(Level level, Random r) {
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r), false, false, 1, level.maxWeight(), true);
        Result res = Algorithms.byId("dijkstra").run(g, Params.from(0)).result();
        List<Integer> order = res.visitOrder();
        if (order.size() != g.nodeCount() || order.size() < LOG_LINES) {
            return null;
        }
        int first = r.nextInt(order.size() - LOG_LINES + 1);
        int bad = r.nextInt(LOG_LINES);
        StringBuilder log = new StringBuilder();
        double trueValue = 0;
        double shown = 0;
        for (int i = 0; i < LOG_LINES; i++) {
            int node = order.get(first + i);
            double d = res.distances().get(node);
            if (i == bad) {
                trueValue = d;
                int shift = 1 + r.nextInt(3);
                shown = r.nextBoolean() || d - shift < 0 ? d + shift : d - shift;
                d = shown;
            }
            log.append("\nLine ").append(i + 1).append(": settle ").append(g.nodeName(node)).append(" with distance ")
                    .append(Choices.number(d));
        }
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= LOG_LINES; i++) {
            options.add("Line " + i);
        }
        String prompt = "Dijkstra runs from " + g.nodeName(0) + ". A student wrote down " + LOG_LINES + " steps of the run, in "
                + "order. A fault was added to one line, and the distance on it is wrong. Which line is it?" + log;
        String why = "Line " + (bad + 1) + " is the bad one. The shortest distance to " + g.nodeName(order.get(first + bad))
                + " is " + Choices.number(trueValue) + ", not " + Choices.number(shown) + ". The other lines match the real run.";
        return new Question(QuestionKind.SPOT_BUG, level, prompt, new GraphInput(g), 0, -1, options, bad, why, null,
                "dijkstra", null);
    }

    // =============================================================================
    // Complexity guess: operation counts at growing sizes, from real runs
    // =============================================================================

    /** The operation counts of one scenario at four sizes, and the growth that fits them best. */
    record Counts(String scenario, int[] sizes, double[] counts) {
    }

    static Counts counts(int scenario, Random r, Level level) {
        switch (scenario) {
            case 0: {
                double[] c = new double[SORT_SIZES.length];
                for (int i = 0; i < c.length; i++) {
                    List<Integer> values = shuffled(SORT_SIZES[i], r);
                    c[i] = Algorithms.byId("merge-sort").run(new ArrayInput(values), Params.none()).result().value();
                }
                return new Counts("Merge sort on a mixed list: comparisons", SORT_SIZES, c);
            }
            case 1: {
                double[] c = new double[SORT_SIZES.length];
                for (int i = 0; i < c.length; i++) {
                    List<Integer> values = shuffled(SORT_SIZES[i], r);
                    Collections.sort(values);
                    c[i] = Algorithms.byId("quick-sort").run(new ArrayInput(values), Params.none()).result().value();
                }
                return new Counts("Quick sort (last number as pivot) on a list that is already sorted: comparisons", SORT_SIZES, c);
            }
            default: {
                int[] sizes = {4, 8, 16, 32};
                double[] c = new double[sizes.length];
                int quarter = r.nextInt(3);
                for (int i = 0; i < c.length; i++) {
                    Graph.Builder b = Graph.builder(false).nodes(sizes[i]);
                    for (int v = 0; v + 1 < sizes[i]; v++) {
                        b.edge(v, v + 1, 1);
                    }
                    int from = sizes[i] * quarter / 4;
                    c[i] = Algorithms.byId("bfs").run(b.build(), Params.from(from)).steps().size();
                }
                return new Counts("Breadth-first search on a path of n nodes: steps in the trace", sizes, c);
            }
        }
    }

    /** The index in {@link #GROWTH} of the growth that fits the counts best, or -1 when no growth fits clearly. */
    static int bestGrowth(Counts c) {
        double[] error = new double[GROWTH.length];
        for (int m = 0; m < GROWTH.length; m++) {
            double num = 0;
            double den = 0;
            for (int i = 0; i < c.sizes.length; i++) {
                double model = model(m, c.sizes[i]);
                // fit on relative error: minimise sum ((s*model - c) / c)^2
                num += model / c.counts[i];
                den += (model / c.counts[i]) * (model / c.counts[i]);
            }
            double scale = num / den;
            double sum = 0;
            for (int i = 0; i < c.sizes.length; i++) {
                double e = (scale * model(m, c.sizes[i]) - c.counts[i]) / c.counts[i];
                sum += e * e;
            }
            error[m] = sum;
        }
        int best = 0;
        for (int m = 1; m < error.length; m++) {
            if (error[m] < error[best]) {
                best = m;
            }
        }
        for (int m = 0; m < error.length; m++) {
            if (m != best && error[m] < error[best] * 4 + 1e-3) {
                return -1;
            }
        }
        return best;
    }

    static double model(int growth, int n) {
        return switch (growth) {
            case 0 -> n;
            case 1 -> n * Math.log(n) / Math.log(2);
            case 2 -> (double) n * n;
            default -> Math.pow(2, n);
        };
    }

    private static Question complexity(Level level, Random r) {
        int scenario = r.nextInt(3);
        Counts c = counts(scenario, r, level);
        int growth = bestGrowth(c);
        int expected = scenario == 0 ? 1 : scenario == 1 ? 2 : 0;
        if (growth != expected) {
            return null;
        }
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < c.sizes.length; i++) {
            table.append("\nn = ").append(c.sizes[i]).append(": ").append(Choices.number(c.counts[i]));
        }
        String prompt = c.scenario + ". Here is what the engine counted at four sizes." + table
                + "\nWhich growth fits the counts?";
        StringBuilder ratios = new StringBuilder();
        for (int i = 1; i < c.sizes.length; i++) {
            ratios.append(i == 1 ? "" : ", ").append("×").append(Choices.number(Math.round(c.counts[i] / c.counts[i - 1] * 10) / 10.0));
        }
        String why = "The best fit is " + GROWTH[growth] + ". When n doubles, the count grows by " + ratios + ". Doubling gives "
                + "about ×2 for n, a little more than ×2 for n log n, and about ×4 for n².";
        Choices ch = Choices.of(r, GROWTH[growth], List.of(GROWTH), 4);
        return new Question(QuestionKind.COMPLEXITY_GUESS, level, prompt, new TextInput(c.scenario), -1, -1, ch.options,
                ch.correctIndex, why, null, "general", null);
    }

    private static List<Integer> shuffled(int n, Random r) {
        List<Integer> pool = new ArrayList<>();
        for (int v = 1; v <= 99; v++) {
            pool.add(v);
        }
        Collections.shuffle(pool, r);
        return new ArrayList<>(pool.subList(0, n));
    }

    // =============================================================================
    // Will it change: one edge changes; does the answer change?
    // =============================================================================

    private static Question willChange(Level level, Random r) {
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r), false, false, 1, level.maxWeight(), true);
        boolean dijkstra = r.nextBoolean();
        int source = 0;
        int end = 1 + r.nextInt(g.nodeCount() - 1);
        double before = answer(g, dijkstra, source, end);
        boolean wantChange = r.nextBoolean();
        for (int attempt = 0; attempt < 40; attempt++) {
            Edge edge = g.edges().get(r.nextInt(g.edgeCount()));
            int weight = 1 + r.nextInt(level.maxWeight() + 3);
            if (weight == (int) edge.weight()) {
                continue;
            }
            Graph changed = replaceWeight(g, edge.id(), weight);
            double after = answer(changed, dijkstra, source, end);
            boolean differs = Math.abs(after - before) > 1e-9;
            if (differs != wantChange) {
                continue;
            }
            String thing = dijkstra ? "the shortest distance from " + g.nodeName(source) + " to " + g.nodeName(end)
                    : "the total weight of the minimum spanning tree";
            String prompt = "The edge " + g.nodeName(edge.from()) + "–" + g.nodeName(edge.to()) + " now has weight "
                    + weight + " instead of " + Choices.number(edge.weight()) + ". Does " + thing + " change?";
            String why = "Before the change " + thing + " is " + Choices.number(before) + ". After it, it is "
                    + Choices.number(after) + ", so the answer is " + (differs ? "yes." : "no.")
                    + (differs ? "" : " The edge is not needed by the best answer, or it stays just as good.");
            Choices ch = Choices.of(r, differs ? "Yes, it changes" : "No, it stays the same",
                    List.of("Yes, it changes", "No, it stays the same"), 2);
            return new Question(QuestionKind.WILL_CHANGE, level, prompt, new GraphInput(g), dijkstra ? source : -1,
                    dijkstra ? end : -1, ch.options, ch.correctIndex, why, null, dijkstra ? "dijkstra" : "kruskal", null);
        }
        return null;
    }

    private static double answer(Graph g, boolean dijkstra, int source, int end) {
        if (dijkstra) {
            return Algorithms.byId("dijkstra").run(g, Params.from(source)).result().distances().get(end);
        }
        return Algorithms.byId("kruskal").run(g, Params.none()).result().totalWeight();
    }

    static Graph replaceWeight(Graph g, int edgeId, double weight) {
        List<Edge> edges = new ArrayList<>();
        for (Edge e : g.edges()) {
            edges.add(e.id() == edgeId ? new Edge(e.id(), e.from(), e.to(), weight) : e);
        }
        return new Graph(g.isDirected(), g.nodes(), edges);
    }

    // =============================================================================
    // Reverse-engineer: which algorithm made this visiting order?
    // =============================================================================

    static final String[] SEARCHES = {"Breadth-first search", "Depth-first search", "Dijkstra"};

    private static Question reverseEngineer(Level level, Random r) {
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r), false, false, 1, level.maxWeight(), true);
        Result bfs = Algorithms.byId("bfs").run(g, Params.from(0)).result();
        Result dfs = Algorithms.byId("dfs").run(g, Params.from(0)).result();
        Result dij = Algorithms.byId("dijkstra").run(g, Params.from(0)).result();
        List<List<Integer>> orders = List.of(bfs.visitOrder(), dfs.visitOrder(), dij.visitOrder());
        for (List<Integer> o : orders) {
            if (o.size() != g.nodeCount()) {
                return null;
            }
        }
        if (orders.get(0).equals(orders.get(1)) || orders.get(0).equals(orders.get(2)) || orders.get(1).equals(orders.get(2))) {
            return null;
        }
        java.util.Set<Double> seen = new java.util.HashSet<>();
        for (double d : dij.distances().values()) {
            if (!seen.add(d)) {
                return null;   // a tie in distance would make the Dijkstra order depend on a tie-break rule
            }
        }
        int answer = r.nextInt(3);
        StringBuilder list = new StringBuilder();
        for (int id : orders.get(answer)) {
            list.append(list.length() == 0 ? "" : ", ").append(g.nodeName(id));
        }
        String prompt = "One of three algorithms started at " + g.nodeName(0) + " and visited the nodes of this graph in this "
                + "order: " + list + ". Ties are broken in alphabetical order. Which algorithm was it? (The numbers on the edges "
                + "are weights. Breadth-first and depth-first search ignore them.)";
        String why = "Breadth-first search gives " + names(g, orders.get(0)) + ". Depth-first search gives "
                + names(g, orders.get(1)) + ". Dijkstra (nearest node first) gives " + names(g, orders.get(2))
                + ". Only " + SEARCHES[answer] + " gives the order shown.";
        Choices ch = Choices.of(r, SEARCHES[answer], List.of(SEARCHES), 3);
        return new Question(QuestionKind.REVERSE_ENGINEER, level, prompt, new GraphInput(g), 0, -1, ch.options,
                ch.correctIndex, why, null, "general", null);
    }

    private static String names(Graph g, List<Integer> ids) {
        StringBuilder out = new StringBuilder();
        for (int id : ids) {
            out.append(out.length() == 0 ? "" : ", ").append(g.nodeName(id));
        }
        return out.toString();
    }

    // =============================================================================
    // Race bet: BFS and DFS race to a node; who gets there first?
    // =============================================================================

    private static Question raceBet(Level level, Random r) {
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r), false, false, 1, 1, false);
        int target = 1 + r.nextInt(g.nodeCount() - 1);
        List<Integer> bfs = Algorithms.byId("bfs").run(g, Params.from(0)).result().visitOrder();
        List<Integer> dfs = Algorithms.byId("dfs").run(g, Params.from(0)).result().visitOrder();
        int a = bfs.indexOf(target);
        int b = dfs.indexOf(target);
        if (a < 0 || b < 0) {
            return null;
        }
        if (a == b && r.nextInt(4) != 0) {
            return null;   // ties are fair but dull; keep a few
        }
        int answer = a < b ? 0 : b < a ? 1 : 2;
        String prompt = "Breadth-first search and depth-first search both start at " + g.nodeName(0) + " and visit neighbours in "
                + "alphabetical order. Place your bet: which one visits node " + g.nodeName(target) + " first?";
        String why = "Breadth-first search visits " + g.nodeName(target) + " as node number " + (a + 1) + " (" + names(g, bfs)
                + "). Depth-first search visits it as node number " + (b + 1) + " (" + names(g, dfs) + ")."
                + (answer == 2 ? " It is a tie." : "");
        String[] racers = {"Breadth-first search", "Depth-first search", "A tie"};
        Choices ch = Choices.of(r, racers[answer], List.of(racers), 3);
        return new Question(QuestionKind.RACE_BET, level, prompt, new GraphInput(g), 0, target, ch.options, ch.correctIndex,
                why, null, "general", null);
    }

    // =============================================================================
    // Cut the network: which edge hurts the flow most?
    // =============================================================================

    private static Question cutNetwork(Level level, Random r) {
        int cap = level.maxWeight() / 2 + 1;
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r) + 1, true, false, 1, cap, false);
        int sink = g.nodeCount() - 1;
        double flow = flow(g, sink);
        List<Double> drop = new ArrayList<>();
        for (Edge e : g.edges()) {
            drop.add(flow - flow(without(g, e.id()), sink));
        }
        int best = 0;
        for (int i = 1; i < drop.size(); i++) {
            if (drop.get(i) > drop.get(best)) {
                best = i;
            }
        }
        if (drop.get(best) <= 0) {
            return null;
        }
        for (int i = 0; i < drop.size(); i++) {
            if (i != best && drop.get(i) >= drop.get(best) - 1e-9) {
                return null;   // the best cut must be the only best cut
            }
        }
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < drop.size(); i++) {
            if (i != best) {
                others.add(i);
            }
        }
        if (others.size() < 3) {
            return null;
        }
        Collections.shuffle(others, r);
        List<Integer> pick = new ArrayList<>(others.subList(0, 3));
        pick.add(best);
        Collections.shuffle(pick, r);
        List<String> options = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        for (int i : pick) {
            Edge e = g.edges().get(i);
            String label = g.nodeName(e.from()) + ARROW + g.nodeName(e.to()) + " (" + Choices.number(e.weight()) + ")";
            options.add(label);
            detail.append(detail.length() == 0 ? "" : "; ").append(label).append(" lowers it by ")
                    .append(Choices.number(drop.get(i)));
        }
        Edge bestEdge = g.edges().get(best);
        String bestLabel = g.nodeName(bestEdge.from()) + ARROW + g.nodeName(bestEdge.to()) + " ("
                + Choices.number(bestEdge.weight()) + ")";
        String prompt = "The numbers are capacities and flow follows the arrows. The maximum flow from " + g.nodeName(0)
                + " to " + g.nodeName(sink) + " is " + Choices.number(flow) + ". You may cut one edge. Which cut lowers the "
                + "maximum flow the most?";
        String why = "Cutting " + bestLabel + " lowers the maximum flow the most, from " + Choices.number(flow) + " to "
                + Choices.number(flow - drop.get(best)) + ". The engine re-ran the flow after each cut: " + detail + ".";
        return new Question(QuestionKind.CUT_NETWORK, level, prompt, new GraphInput(g), 0, sink, options,
                options.indexOf(bestLabel), why, null, "edmonds-karp", null);
    }

    static double flow(Graph g, int sink) {
        return Algorithms.byId("edmonds-karp").run(g, new Params(0, 0L, sink)).result().value();
    }

    static Graph without(Graph g, int edgeId) {
        List<Edge> edges = new ArrayList<>();
        int id = 0;
        for (Edge e : g.edges()) {
            if (e.id() != edgeId) {
                edges.add(new Edge(id++, e.from(), e.to(), e.weight()));
            }
        }
        List<Node> nodes = g.nodes();
        return new Graph(g.isDirected(), nodes, edges);
    }

    // =============================================================================

    private static int nodeCount(Level level, Random r) {
        return level.minNodes() + r.nextInt(level.maxNodes() - level.minNodes() + 1);
    }

    private static int extra(Level level, Random r) {
        return level.minExtraEdges() + r.nextInt(level.maxExtraEdges() - level.minExtraEdges() + 1);
    }

}

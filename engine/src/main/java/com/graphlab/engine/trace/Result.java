package com.graphlab.engine.trace;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The final answer of a run, separate from the animation. The Arena scores the player's answers
 * against this, and tests check that the last frame of a trace agrees with it.
 *
 * <p>Fields an algorithm has no use for are simply empty: Kruskal has no distances, DFS has no
 * weights. Node and edge ids are the graph's own ids.
 */
public final class Result {
    private final List<Integer> visitOrder;
    private final List<Integer> finishOrder;
    private final Map<Integer, Double> distances;
    private final Map<Integer, Integer> parentEdge;
    private final List<Integer> treeEdgeIds;
    private final double totalWeight;
    private final boolean negativeCycle;
    // answers of the non-graph and flow algorithms (empty or zero for the others)
    private final List<Integer> sortedValues;
    private final double value;
    private final List<Integer> chosen;
    private final List<Double> amounts;
    private final Map<Integer, Integer> colours;
    private final List<List<Double>> matrix;
    private final Map<Integer, Double> edgeFlow;
    private final List<Integer> cutEdgeIds;
    private final List<Integer> sourceSide;
    private final String text;

    private Result(Builder b) {
        this.visitOrder = List.copyOf(b.visitOrder);
        this.finishOrder = List.copyOf(b.finishOrder);
        this.distances = Collections.unmodifiableMap(new LinkedHashMap<>(b.distances));
        this.parentEdge = Collections.unmodifiableMap(new LinkedHashMap<>(b.parentEdge));
        this.treeEdgeIds = List.copyOf(b.treeEdgeIds);
        this.totalWeight = b.totalWeight;
        this.negativeCycle = b.negativeCycle;
        this.sortedValues = List.copyOf(b.sortedValues);
        this.value = b.value;
        this.chosen = List.copyOf(b.chosen);
        this.amounts = List.copyOf(b.amounts);
        this.colours = Collections.unmodifiableMap(new LinkedHashMap<>(b.colours));
        List<List<Double>> rows = new ArrayList<>();
        for (List<Double> row : b.matrix) {
            rows.add(List.copyOf(row));
        }
        this.matrix = Collections.unmodifiableList(rows);
        this.edgeFlow = Collections.unmodifiableMap(new LinkedHashMap<>(b.edgeFlow));
        this.cutEdgeIds = List.copyOf(b.cutEdgeIds);
        this.sourceSide = List.copyOf(b.sourceSide);
        this.text = b.text;
    }

    /**
     * A text answer: the common subsequence of LCS, the edit script of edit distance (tokens like {@code keep:K},
     * {@code replace:S>M}, {@code delete:A}, {@code insert:B}, separated by spaces), the coins of coin change as "3 + 3".
     * Empty when an algorithm has none.
     */
    public String text() {
        return text;
    }

    /** Sorting algorithms: the list in sorted order. */
    public List<Integer> sortedValues() {
        return sortedValues;
    }

    /**
     * The one number a run is about: the maximum flow, the size of the cut Karger found, the
     * number of colours used, the best value in the knapsack. Zero when an algorithm has none.
     */
    public double value() {
        return value;
    }

    /** Knapsack: the items (0-based, in the order given) that are in the bag; for the fractional version, those taken at all. */
    public List<Integer> chosen() {
        return chosen;
    }

    /** Fractional knapsack: how much of each item (0 to 1), in the order the items were given. */
    public List<Double> amounts() {
        return amounts;
    }

    /** Colouring: node id to colour number (starting at 1). Karger: node id to 0 or 1 (which side of the cut). */
    public Map<Integer, Integer> colours() {
        return colours;
    }

    /** Floyd-Warshall: shortest distance from node i to node j, in node order. Unreachable is +infinity. */
    public List<List<Double>> matrix() {
        return matrix;
    }

    /** Flow: net flow on each edge id (negative means against the edge's direction in an undirected graph). */
    public Map<Integer, Double> edgeFlow() {
        return edgeFlow;
    }

    /** Edges that cross the cut (min cut for flow, the random cut for Karger). */
    public List<Integer> cutEdgeIds() {
        return cutEdgeIds;
    }

    /** Flow: the nodes still reachable from the start in the last residual graph (one side of the min cut). */
    public List<Integer> sourceSide() {
        return sourceSide;
    }

    /** Nodes in the order the algorithm reached them (BFS and DFS discovery, Dijkstra settling, Prim adding). */
    public List<Integer> visitOrder() {
        return visitOrder;
    }

    /** DFS only: nodes in the order their search finished. */
    public List<Integer> finishOrder() {
        return finishOrder;
    }

    /** Hops (BFS) or weighted distance (Dijkstra, Bellman-Ford) from the source. Unreached nodes are +infinity. */
    public Map<Integer, Double> distances() {
        return distances;
    }

    /** For each reached node except the root, the id of the edge it was reached by. */
    public Map<Integer, Integer> parentEdge() {
        return parentEdge;
    }

    /** Edges of the resulting tree (search tree, shortest-path tree or spanning tree), in the order they were added. */
    public List<Integer> treeEdgeIds() {
        return treeEdgeIds;
    }

    /** Sum of the weights of {@link #treeEdgeIds()}. */
    public double totalWeight() {
        return totalWeight;
    }

    /** Bellman-Ford only: a negative cycle is reachable from the source, so distances do not exist. */
    public boolean negativeCycle() {
        return negativeCycle;
    }

    /**
     * Node ids along the tree branch that leads to {@code target}, root first. Empty when the
     * target was not reached. For shortest-path algorithms the root is the source.
     */
    public List<Integer> pathTo(Graph graph, int target) {
        if (!visitOrder.contains(target)) {
            return List.of();
        }
        List<Integer> reversed = new ArrayList<>();
        int current = target;
        reversed.add(current);
        while (parentEdge.containsKey(current) && reversed.size() <= graph.nodeCount()) {
            Edge edge = graph.edge(parentEdge.get(current));
            current = edge.to() == current ? edge.from() : edge.to();
            reversed.add(current);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Result that)) {
            return false;
        }
        return Double.compare(totalWeight, that.totalWeight) == 0
                && negativeCycle == that.negativeCycle
                && visitOrder.equals(that.visitOrder)
                && finishOrder.equals(that.finishOrder)
                && distances.equals(that.distances)
                && parentEdge.equals(that.parentEdge)
                && treeEdgeIds.equals(that.treeEdgeIds)
                && sortedValues.equals(that.sortedValues)
                && Double.compare(value, that.value) == 0
                && chosen.equals(that.chosen)
                && amounts.equals(that.amounts)
                && colours.equals(that.colours)
                && matrix.equals(that.matrix)
                && edgeFlow.equals(that.edgeFlow)
                && cutEdgeIds.equals(that.cutEdgeIds)
                && sourceSide.equals(that.sourceSide)
                && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(visitOrder, finishOrder, distances, parentEdge, treeEdgeIds,
                totalWeight, negativeCycle, sortedValues, value, chosen, amounts, colours, matrix, edgeFlow,
                cutEdgeIds, sourceSide, text);
    }

    @Override
    public String toString() {
        return "Result{visitOrder=" + visitOrder + ", distances=" + distances
                + ", treeEdgeIds=" + treeEdgeIds + ", totalWeight=" + totalWeight
                + ", negativeCycle=" + negativeCycle
                + (sortedValues.isEmpty() ? "" : ", sorted=" + sortedValues)
                + (value == 0 ? "" : ", value=" + value) + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Integer> visitOrder = new ArrayList<>();
        private final List<Integer> finishOrder = new ArrayList<>();
        private final Map<Integer, Double> distances = new LinkedHashMap<>();
        private final Map<Integer, Integer> parentEdge = new LinkedHashMap<>();
        private final List<Integer> treeEdgeIds = new ArrayList<>();
        private double totalWeight;
        private boolean negativeCycle;
        private final List<Integer> sortedValues = new ArrayList<>();
        private double value;
        private final List<Integer> chosen = new ArrayList<>();
        private final List<Double> amounts = new ArrayList<>();
        private final Map<Integer, Integer> colours = new LinkedHashMap<>();
        private final List<List<Double>> matrix = new ArrayList<>();
        private final Map<Integer, Double> edgeFlow = new LinkedHashMap<>();
        private final List<Integer> cutEdgeIds = new ArrayList<>();
        private final List<Integer> sourceSide = new ArrayList<>();
        private String text = "";

        private Builder() {
        }

        public Builder text(String answer) {
            this.text = answer == null ? "" : answer;
            return this;
        }

        public Builder sortedValues(List<Integer> values) {
            sortedValues.clear();
            sortedValues.addAll(values);
            return this;
        }

        public Builder value(double v) {
            this.value = v;
            return this;
        }

        public Builder chosen(List<Integer> items) {
            chosen.clear();
            chosen.addAll(items);
            return this;
        }

        public Builder amounts(List<Double> fractions) {
            amounts.clear();
            amounts.addAll(fractions);
            return this;
        }

        public Builder colour(int nodeId, int colour) {
            colours.put(nodeId, colour);
            return this;
        }

        public Builder matrix(List<List<Double>> rows) {
            matrix.clear();
            matrix.addAll(rows);
            return this;
        }

        public Builder flow(int edgeId, double netFlow) {
            edgeFlow.put(edgeId, netFlow);
            return this;
        }

        public Builder cutEdge(int edgeId) {
            cutEdgeIds.add(edgeId);
            return this;
        }

        public Builder sourceSide(List<Integer> nodeIds) {
            sourceSide.clear();
            sourceSide.addAll(nodeIds);
            return this;
        }

        public Builder visit(int nodeId) {
            visitOrder.add(nodeId);
            return this;
        }

        public Builder finish(int nodeId) {
            finishOrder.add(nodeId);
            return this;
        }

        public Builder distance(int nodeId, double distance) {
            distances.put(nodeId, distance);
            return this;
        }

        public Builder parent(int nodeId, int edgeId) {
            parentEdge.put(nodeId, edgeId);
            return this;
        }

        public Builder treeEdge(int edgeId) {
            treeEdgeIds.add(edgeId);
            return this;
        }

        public Builder totalWeight(double weight) {
            this.totalWeight = weight;
            return this;
        }

        public Builder negativeCycle(boolean found) {
            this.negativeCycle = found;
            return this;
        }

        public Result build() {
            return new Result(this);
        }
    }
}

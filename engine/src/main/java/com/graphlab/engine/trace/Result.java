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

    private Result(Builder b) {
        this.visitOrder = List.copyOf(b.visitOrder);
        this.finishOrder = List.copyOf(b.finishOrder);
        this.distances = Collections.unmodifiableMap(new LinkedHashMap<>(b.distances));
        this.parentEdge = Collections.unmodifiableMap(new LinkedHashMap<>(b.parentEdge));
        this.treeEdgeIds = List.copyOf(b.treeEdgeIds);
        this.totalWeight = b.totalWeight;
        this.negativeCycle = b.negativeCycle;
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
                && treeEdgeIds.equals(that.treeEdgeIds);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(visitOrder, finishOrder, distances, parentEdge, treeEdgeIds,
                totalWeight, negativeCycle);
    }

    @Override
    public String toString() {
        return "Result{visitOrder=" + visitOrder + ", distances=" + distances
                + ", treeEdgeIds=" + treeEdgeIds + ", totalWeight=" + totalWeight
                + ", negativeCycle=" + negativeCycle + "}";
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

        private Builder() {
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

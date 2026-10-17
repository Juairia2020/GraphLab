package com.graphlab.engine.arena;

import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.List;

/**
 * The route the player draws in "Path pick" (MS11). The player taps the nodes one after the other; a tap on a node that is
 * joined to the last one adds that edge (the line snaps to the graph's real edges), a tap on the last node takes it back.
 * Anything else is ignored. Plain Java, so the rules are tested here.
 */
public final class PathBoard {
    private final Graph graph;
    private final int start;
    private final int end;
    private final List<Integer> nodes = new ArrayList<>();
    private final List<Integer> edges = new ArrayList<>();

    public PathBoard(Graph graph, int start, int end) {
        if (!graph.hasNode(start) || !graph.hasNode(end) || start == end) {
            throw new IllegalArgumentException("A path needs two different nodes of the graph");
        }
        this.graph = graph;
        this.start = start;
        this.end = end;
        nodes.add(start);
    }

    /** What a tap did. */
    public enum Result { ADDED, REMOVED, IGNORED }

    public Result tap(int node) {
        int last = nodes.get(nodes.size() - 1);
        if (node == last) {
            if (nodes.size() > 1) {
                nodes.remove(nodes.size() - 1);
                edges.remove(edges.size() - 1);
                return Result.REMOVED;
            }
            return Result.IGNORED;
        }
        if (!graph.hasNode(node) || nodes.contains(node)) {
            return Result.IGNORED;   // no walking in circles
        }
        for (Arc arc : graph.arcsFrom(last)) {
            if (arc.to() == node) {
                nodes.add(node);
                edges.add(arc.edge().id());
                return Result.ADDED;
            }
        }
        return Result.IGNORED;
    }

    public void reset() {
        nodes.clear();
        edges.clear();
        nodes.add(start);
    }

    public List<Integer> nodes() {
        return List.copyOf(nodes);
    }

    /** The edge ids drawn so far, in order. */
    public List<Integer> edges() {
        return List.copyOf(edges);
    }

    /** The route reaches the end node. */
    public boolean isComplete() {
        return nodes.get(nodes.size() - 1) == end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    /** Total weight of the edges drawn. */
    public double weight() {
        double sum = 0;
        for (int id : edges) {
            sum += graph.edge(id).weight();
        }
        return sum;
    }

    /** Mistakes against the task's solution; see {@link Task#mistakes}. */
    public int mistakes(Task task) {
        int[] given = new int[edges.size()];
        for (int i = 0; i < given.length; i++) {
            given[i] = edges.get(i);
        }
        return task.mistakes(given);
    }
}

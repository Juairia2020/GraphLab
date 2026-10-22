package com.graphlab.engine.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.play.StepSummary.NodeRow;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import org.junit.Test;

public class StepSummaryTest {
    private final Graph graph = SampleGraph.sixNodeUndirected();

    @Test
    public void oneRowPerNodeInNodeOrder() {
        List<NodeRow> rows = StepSummary.nodeRows(graph, GraphFrame.initial(graph));
        assertEquals(6, rows.size());
        assertEquals("A", rows.get(0).name());
        assertEquals("F", rows.get(5).name());
        assertEquals(State.IDLE, rows.get(3).state());
        assertEquals("", rows.get(3).label());
    }

    @Test
    public void frontierIsOrderedBySmallestDistanceFirst() {
        GraphFrame frame = GraphFrame.builder(graph)
                .node(SampleGraph.B, State.FRONTIER).label(SampleGraph.B, "7")
                .node(SampleGraph.C, State.FRONTIER).label(SampleGraph.C, "3")
                .node(SampleGraph.F, State.FRONTIER).label(SampleGraph.F, "14")
                .node(SampleGraph.A, State.VISITED).label(SampleGraph.A, "0")
                .build();
        List<NodeRow> frontier = StepSummary.frontier(StepSummary.nodeRows(graph, frame(frame)));
        assertEquals(List.of("C", "B", "F"), frontier.stream().map(NodeRow::name).toList());
        assertEquals("C 3", StepSummary.chipText(frontier.get(0)));
    }

    private static GraphFrame frame(GraphFrame f) {
        return f;
    }

    @Test
    public void frontierRowsWithoutNumbersKeepNodeOrder() {
        GraphFrame frame = GraphFrame.builder(graph)
                .node(SampleGraph.E, State.FRONTIER)
                .node(SampleGraph.B, State.FRONTIER)
                .node(SampleGraph.D, State.FRONTIER).label(SampleGraph.D, "3/5")   // a DFS style label, not a number
                .build();
        List<NodeRow> frontier = StepSummary.frontier(StepSummary.nodeRows(graph, frame));
        assertEquals(List.of("B", "D", "E"), frontier.stream().map(NodeRow::name).toList());
        assertEquals("B", StepSummary.chipText(frontier.get(0)));
    }

    @Test
    public void countersAreListedInDeclarationOrder() {
        Trace trace = Algorithms.byId("dijkstra").run(graph, Params.from(SampleGraph.A));
        List<String> counters = StepSummary.counters(trace.last());
        assertEquals(2, counters.size());
        assertTrue(counters.get(0).startsWith("settled "));
        assertTrue(counters.get(1).startsWith("relaxed "));
        assertEquals("settled 6", counters.get(0));
    }

    @Test
    public void dijkstraFrontierIsEmptyAtTheEndAndNonEmptyInTheMiddle() {
        Trace trace = Algorithms.byId("dijkstra").run(graph, Params.from(SampleGraph.A));
        boolean sawWaiting = false;
        for (int i = 0; i < trace.size(); i++) {
            var rows = StepSummary.nodeRows(graph, (GraphFrame) trace.step(i).frame());
            sawWaiting |= !StepSummary.frontier(rows).isEmpty();
        }
        assertTrue(sawWaiting);
        var lastRows = StepSummary.nodeRows(graph, (GraphFrame) trace.last().frame());
        assertTrue(StepSummary.frontier(lastRows).isEmpty());
    }

    @Test
    public void kruskalNeedsNoSourceAndTheOthersDo() {
        java.util.Set<String> noSource = java.util.Set.of("kruskal", "floyd-warshall", "graph-colouring", "karger",
                "merge-sort", "quick-sort", "randomized-quick-sort", "fractional-knapsack", "knapsack-01", "coin-change", "lcs",
                "edit-distance", "lis", "topological-sort", "tarjan-scc", "bipartite-matching", "huffman", "closest-pair",
                "reservoir-sampling", "matrix-chain");
        for (var algorithm : Algorithms.all()) {
            assertEquals(algorithm.id(), !noSource.contains(algorithm.id()), algorithm.needsSource());
        }
    }
}

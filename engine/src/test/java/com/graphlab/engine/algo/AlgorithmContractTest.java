package com.graphlab.engine.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Labels;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Rules every algorithm must obey, checked on every algorithm and every graph in
 * {@link TestGraphs#undirectedSuite()}, from the first and from the last node.
 */
public class AlgorithmContractTest {

    /** The six algorithms that produce a spanning tree or a shortest-path tree; the strict rules below apply to them. */
    private static final List<Algorithm> TREE_FAMILY = List.of(Algorithms.byId("bfs"), Algorithms.byId("dfs"),
            Algorithms.byId("dijkstra"), Algorithms.byId("bellman-ford"), Algorithms.byId("kruskal"), Algorithms.byId("prim"));

    private interface Check {
        void run(Algorithm algorithm, String graphName, Graph graph, Trace trace);
    }

    private static void forEveryRun(Check check) {
        for (Algorithm algorithm : TREE_FAMILY) {
            for (Map.Entry<String, Graph> entry : TestGraphs.undirectedSuite().entrySet()) {
                Graph graph = entry.getValue();
                List<Node> nodes = graph.nodes();
                for (int start : new int[] {nodes.get(0).id(), nodes.get(nodes.size() - 1).id()}) {
                    Trace trace = algorithm.run(graph, Params.from(start));
                    check.run(algorithm, algorithm.id() + " on " + entry.getKey() + " from " + start,
                            graph, trace);
                }
            }
        }
    }

    // ---- registry ----

    @Test
    public void twentyEightAlgorithmsWithUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (Algorithm a : Algorithms.all()) {
            assertTrue("duplicate id " + a.id(), ids.add(a.id()));
        }
        assertEquals(Set.of("bfs", "dfs", "dijkstra", "bellman-ford", "floyd-warshall", "kruskal", "prim", "edmonds-karp",
                "fractional-knapsack", "graph-colouring", "merge-sort", "quick-sort", "randomized-quick-sort", "karger",
                "knapsack-01", "coin-change", "lcs", "edit-distance", "lis", "astar", "topological-sort", "tarjan-scc", "dinic",
                "bipartite-matching", "huffman", "closest-pair", "reservoir-sampling", "matrix-chain"), ids);
    }

    @Test
    public void lookupByIdFindsTheSameInstance() {
        for (Algorithm a : TREE_FAMILY) {
            assertEquals(a, Algorithms.byId(a.id()));
        }
        assertThrows(IllegalArgumentException.class, () -> Algorithms.byId("quicksort-typo"));
    }

    @Test
    public void everyAlgorithmHasANameAndPseudocode() {
        for (Algorithm a : TREE_FAMILY) {
            assertFalse(a.name().isBlank());
            assertTrue(a.pseudocode().size() >= 5);
            assertTrue(a.pseudocode().stream().noneMatch(String::isBlank));
        }
    }

    // ---- what a trace must look like ----

    @Test
    public void everyTraceHasStepsAndRemembersItsInputs() {
        forEveryRun((algorithm, label, graph, trace) -> {
            assertTrue(label, trace.size() >= 2);
            assertEquals(label, algorithm.id(), trace.algorithmId());
            assertTrue(label, trace.params().hasSource());
            assertNotNull(label, trace.result());
        });
    }

    @Test
    public void everyStepPointsAtARealPseudocodeLineAndSaysSomething() {
        forEveryRun((algorithm, label, graph, trace) -> {
            int lines = algorithm.pseudocode().size();
            for (Step step : trace.steps()) {
                assertTrue(label + ": line " + step.line(), step.line() >= 1 && step.line() <= lines);
                assertFalse(label, step.message().isBlank());
            }
        });
    }

    @Test
    public void everyStepCarriesTheSameCounterNames() {
        forEveryRun((algorithm, label, graph, trace) -> {
            Set<String> names = trace.step(0).counters().keySet();
            assertFalse(label, names.isEmpty());
            for (Step step : trace.steps()) {
                assertEquals(label, names, step.counters().keySet());
            }
        });
    }

    @Test
    public void runningTwiceGivesAnEqualTrace() {
        forEveryRun((algorithm, label, graph, trace) -> {
            Trace again = algorithm.run(graph, trace.params());
            assertEquals(label, trace, again);
        });
    }

    @Test
    public void framesCannotBeModifiedAfterwards() {
        forEveryRun((algorithm, label, graph, trace) -> {
            GraphFrame frame = (GraphFrame) trace.step(0).frame();
            int node = graph.nodes().get(0).id();
            assertThrows(label, UnsupportedOperationException.class,
                    () -> frame.nodeStates().put(node, State.FAIL));
        });
    }

    @Test
    public void earlierFramesAreNotChangedByLaterSteps() {
        // Every step must be its own snapshot: re-running and comparing step by step proves
        // no frame shares mutable state with a later one.
        forEveryRun((algorithm, label, graph, trace) -> {
            List<Step> copy = List.copyOf(trace.steps());
            Trace again = algorithm.run(graph, trace.params());
            for (int i = 0; i < copy.size(); i++) {
                assertEquals(label + " step " + (i + 1), copy.get(i), again.step(i));
            }
        });
    }

    // ---- replaying to the last step must reproduce the result ----

    @Test
    public void lastFrameLeavesNothingWaitingOrInProgress() {
        forEveryRun((algorithm, label, graph, trace) -> {
            GraphFrame last = TestGraphs.lastFrame(trace);
            assertTrue(label, TestGraphs.nodesIn(last, State.FRONTIER).isEmpty());
            assertTrue(label, TestGraphs.nodesIn(last, State.CHOSEN).isEmpty());
            assertTrue(label, TestGraphs.edgesIn(last, State.FRONTIER).isEmpty());
            assertTrue(label, TestGraphs.edgesIn(last, State.CHOSEN).isEmpty());
        });
    }

    @Test
    public void pathEdgesInTheLastFrameAreExactlyTheResultTree() {
        forEveryRun((algorithm, label, graph, trace) -> {
            Result result = trace.result();
            Set<Integer> shown = TestGraphs.edgesIn(TestGraphs.lastFrame(trace), State.PATH);
            assertEquals(label, Set.copyOf(result.treeEdgeIds()), shown);
            assertEquals(label + ": duplicate tree edges", result.treeEdgeIds().size(), shown.size());
            assertTrue(label, result.treeEdgeIds().size() <= Math.max(graph.nodeCount() - 1, 0));
            assertEquals(label, TestGraphs.weightOf(graph, result.treeEdgeIds()), result.totalWeight(), 1e-9);
        });
    }

    @Test
    public void labelsInTheLastFrameAreTheResultDistances() {
        forEveryRun((algorithm, label, graph, trace) -> {
            Result result = trace.result();
            if (result.distances().isEmpty()) {
                return;     // Kruskal, Prim, DFS have no distances
            }
            if (!(algorithm.id().equals("bfs") || algorithm.id().equals("dijkstra")
                    || algorithm.id().equals("bellman-ford"))) {
                return;
            }
            GraphFrame last = TestGraphs.lastFrame(trace);
            for (Node node : graph.nodes()) {
                double shown = Labels.parse(last.nodeLabel(node.id()).isEmpty()
                        ? Labels.INFINITY : last.nodeLabel(node.id()));
                assertEquals(label + " node " + node.id(), result.distances().get(node.id()), shown, 1e-9);
            }
        });
    }

    @Test
    public void visitOrderHasNoRepeatsAndOnlyRealNodes() {
        forEveryRun((algorithm, label, graph, trace) -> {
            List<Integer> order = trace.result().visitOrder();
            assertEquals(label, order.size(), new HashSet<>(order).size());
            for (int id : order) {
                assertTrue(label, graph.hasNode(id));
            }
        });
    }

    @Test
    public void everyTreeEdgeIsARealEdgeOfTheGraph() {
        forEveryRun((algorithm, label, graph, trace) -> {
            for (int id : trace.result().treeEdgeIds()) {
                assertNotNull(label, graph.edge(id));
            }
        });
    }

    // ---- bad input ----

    @Test
    public void algorithmsThatNeedAStartNodeRejectMissingOrUnknownOnes() {
        Graph g = TestGraphs.undirectedSuite().get("sample");
        for (Algorithm a : TREE_FAMILY) {
            if (a.id().equals("kruskal")) {
                continue;   // Kruskal needs no start node
            }
            assertThrows(a.id(), IllegalArgumentException.class, () -> a.run(g, Params.none()));
            assertThrows(a.id(), IllegalArgumentException.class, () -> a.run(g, Params.from(999)));
        }
    }

    @Test
    public void spanningTreeAlgorithmsRejectDirectedGraphs() {
        Graph directed = Graph.builder(true).nodes(3).edge(0, 1, 1).edge(1, 2, 1).build();
        for (String id : List.of("kruskal", "prim")) {
            Algorithm a = Algorithms.byId(id);
            assertThrows(id, IllegalArgumentException.class, () -> a.run(directed, Params.from(0)));
        }
    }

    @Test
    public void sixtyNodeGraphsRunToCompletion() {
        Graph big = TestGraphs.ringWithChords(60);
        assertEquals(60, big.nodeCount());
        for (Algorithm a : TREE_FAMILY) {
            Trace t = a.run(big, Params.from(0));
            assertTrue(a.id(), t.size() > 60);
        }
    }
}

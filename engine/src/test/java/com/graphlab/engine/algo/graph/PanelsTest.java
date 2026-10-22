package com.graphlab.engine.algo.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Panel;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.util.List;
import org.junit.Test;

/** The State tab tables (MS10 S4): queue, stack, priority queue and union-find parents. */
public class PanelsTest {
    private static Panel panel(Step step, String title) {
        for (Panel p : ((GraphFrame) step.frame()).panels()) {
            if (p.title().equals(title)) {
                return p;
            }
        }
        return null;
    }

    private static Trace run(Algorithm algorithm, boolean needsSource) {
        Graph g = SampleGraph.sixNodeUndirected();
        return algorithm.run(g, needsSource ? Params.from(SampleGraph.A) : Params.none());
    }

    @Test
    public void bfsQueueStartsWithTheSourceAndEndsEmpty() {
        Trace t = run(new Bfs(), true);
        assertEquals(List.of("A"), panel(t.step(0), "Queue").items());
        assertTrue(panel(t.last(), "Queue").items().isEmpty());
    }

    @Test
    public void bfsQueueSizeMatchesTheQueueCounterOnEveryStep() {
        Trace t = run(new Bfs(), true);
        for (Step step : t.steps()) {
            assertEquals((long) step.counters().get("queue"), panel(step, "Queue").items().size());
        }
    }

    @Test
    public void dfsStackIsEmptyAtTheEndAndNeverDeeperThanTheNodeCount() {
        Trace t = run(new Dfs(), true);
        int deepest = 0;
        for (Step step : t.steps()) {
            Panel p = panel(step, "Stack");
            if (p != null) {
                deepest = Math.max(deepest, p.items().size());
            }
        }
        assertTrue(deepest >= 2 && deepest <= 6);
        assertTrue(panel(t.last(), "Stack").items().isEmpty());
    }

    @Test
    public void dijkstraPriorityQueueIsSortedAndEmptyAtTheEnd() {
        Trace t = run(new Dijkstra(), true);
        assertEquals(List.of("A:0"), panel(t.step(0), "Priority queue").items());
        for (Step step : t.steps()) {
            List<String> items = panel(step, "Priority queue").items();
            double previous = -1;
            for (String item : items) {
                double d = Double.parseDouble(item.substring(item.indexOf(':') + 1));
                assertTrue(d >= previous);
                previous = d;
            }
        }
        assertTrue(panel(t.last(), "Priority queue").items().isEmpty());
    }

    @Test
    public void primQueueHoldsWaitingEdgesCheapestFirst() {
        Trace t = run(new Prim(), true);
        boolean sawEdges = false;
        for (Step step : t.steps()) {
            List<String> items = panel(step, "Queue").items();
            sawEdges |= !items.isEmpty();
            double previous = -1;
            for (String item : items) {
                double w = Double.parseDouble(item.substring(item.indexOf(':') + 1));
                assertTrue(w >= previous);
                previous = w;
            }
        }
        assertTrue(sawEdges);
    }

    @Test
    public void kruskalStartsWithEverySetAloneAndEndsWithOneLeader() {
        Trace t = run(new Kruskal(), false);
        Panel first = panel(t.step(0), "Union-find parents");
        assertEquals(6, first.items().size());
        for (String item : first.items()) {
            assertTrue(item.endsWith("(leader)"));
        }
        long leaders = panel(t.last(), "Union-find parents").items().stream()
                .filter(i -> i.endsWith("(leader)")).count();
        assertEquals(1, leaders);   // the sample graph is connected
    }

    @Test
    public void everyStepOfEveryPanelAlgorithmCarriesItsPanel() {
        for (Algorithm a : List.of(new Bfs(), new Dfs(), new Dijkstra(), new Prim())) {
            Trace t = run(a, true);
            for (Step step : t.steps()) {
                assertTrue(a.id(), !((GraphFrame) step.frame()).panels().isEmpty()
                        || step == t.step(0) || a instanceof Dfs);
            }
        }
    }

    @Test
    public void panelCopiesItsItems() {
        java.util.ArrayList<String> list = new java.util.ArrayList<>(List.of("a"));
        Panel p = new Panel("x", list);
        list.add("b");
        assertEquals(1, p.items().size());
    }
}

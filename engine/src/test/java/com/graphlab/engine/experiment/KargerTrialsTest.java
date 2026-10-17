package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.graph.Karger;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Params;
import org.junit.Test;

public class KargerTrialsTest {
    private static Graph cycle(int n) {
        Graph.Builder b = Graph.builder(false);
        for (int i = 0; i < n; i++) {
            b.node(i, i, 0, "N" + i);
        }
        for (int i = 0; i < n; i++) {
            b.edge(i, i, (i + 1) % n, 1);
        }
        return b.build();
    }

    private static Graph complete(int n) {
        Graph.Builder b = Graph.builder(false);
        for (int i = 0; i < n; i++) {
            b.node(i, i, 0, "N" + i);
        }
        int id = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                b.edge(id++, i, j, 1);
            }
        }
        return b.build();
    }

    private static void sameAsTheTracedAlgorithm(Graph graph) {
        KargerTrials trials = new KargerTrials(graph);
        for (long seed = 0; seed < 150; seed++) {
            int fast = trials.trial(seed);
            int traced = (int) new Karger().run(graph, Params.none().withSeed(seed)).result().value();
            assertEquals("seed " + seed, traced, fast);
        }
    }

    @Test
    public void matchesKargerRunOnTheSampleGraph() {
        sameAsTheTracedAlgorithm(SampleGraph.sixNodeUndirected());
    }

    @Test
    public void matchesKargerRunOnCyclesAndCompleteGraphs() {
        sameAsTheTracedAlgorithm(cycle(7));
        sameAsTheTracedAlgorithm(complete(5));
    }

    @Test
    public void aDisconnectedGraphHasCutZero() {
        Graph g = Graph.builder(false).node(0, 0, 0, "A").node(1, 1, 0, "B").node(2, 2, 0, "C").edge(0, 0, 1, 1).build();
        KargerTrials trials = new KargerTrials(g);
        assertFalse(trials.isGraphConnected());
        assertEquals(0, trials.trial(1));
        assertEquals((int) new Karger().run(g, Params.none()).result().value(), trials.trial(1));
    }

    @Test
    public void manyTrialsFindTheTrueMinimumCut() {
        // The sample graph's node E has two edges, so the minimum cut is 2.
        KargerTrials trials = new KargerTrials(SampleGraph.sixNodeUndirected());
        Histogram h = new Histogram();
        for (int i = 0; i < 500; i++) {
            h.add(trials.trial(KargerTrials.seedFor(7, i)));
        }
        assertEquals(500, h.total());
        assertEquals(2, h.smallest());
        assertTrue(h.countOf(2) > 0);
        assertTrue("a cut can never be smaller than the minimum", h.smallest() >= 2);
    }

    @Test
    public void everyCutOfACycleHasExactlyTwoEdges() {
        KargerTrials trials = new KargerTrials(cycle(9));
        for (int i = 0; i < 100; i++) {
            assertEquals(2, trials.trial(i));
        }
    }

    @Test
    public void sameSeedSameResult() {
        KargerTrials trials = new KargerTrials(complete(6));
        assertEquals(trials.trial(99), trials.trial(99));
        assertEquals(11, KargerTrials.seedFor(10, 1));
    }

    @Test
    public void refusesWhatKargerRefuses() {
        assertThrows(IllegalArgumentException.class, () -> new KargerTrials(Graph.builder(true).nodes(3).edge(0, 1, 1).build()));
        assertThrows(IllegalArgumentException.class, () -> new KargerTrials(Graph.builder(false).build()));
        assertThrows(IllegalArgumentException.class, () -> new KargerTrials(Graph.builder(false).nodes(1).build()));
    }
}

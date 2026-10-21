package com.graphlab.engine.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import org.junit.Test;

public class RaceTest {
    private Comparison.Report report(String a, String b) {
        Graph g = SampleGraph.sixNodeUndirected();
        return Comparison.run(g, a, b, SampleGraph.A);
    }

    @Test
    public void lengthIsTheLongerTrace() {
        Comparison.Report r = report("dijkstra", "bellman-ford");
        Race race = Race.of(r);
        assertEquals(Math.max(r.a().steps(), r.b().steps()), race.length());
    }

    @Test
    public void theShorterSideStaysOnItsLastStep() {
        Comparison.Report r = report("dijkstra", "bellman-ford");
        Race race = Race.of(r);
        Comparison.Side shorter = r.a().steps() <= r.b().steps() ? r.a() : r.b();
        boolean aShorter = shorter == r.a();
        int past = race.length() - 1;
        assertSame(aShorter ? r.a().trace().last() : r.b().trace().last(), aShorter ? race.stepA(past) : race.stepB(past));
        assertTrue(aShorter ? race.doneA(past) : race.doneB(past));
    }

    @Test
    public void bothSidesStartAtStepZero() {
        Comparison.Report r = report("kruskal", "prim");
        Race race = Race.of(r);
        assertSame(r.a().trace().step(0), race.stepA(0));
        assertSame(r.b().trace().step(0), race.stepB(0));
    }

    @Test
    public void aFailedSideShowsNothingAndTheOtherStillPlays() {
        Graph g = Graph.builder(true).nodes(3).edge(0, 1, 4).edge(1, 2, -2).build();
        Comparison.Report r = Comparison.run(g, "dijkstra", "bellman-ford", 0);
        Race race = Race.of(r);
        assertNull(race.stepA(0));
        assertEquals(r.b().steps(), race.length());
        assertEquals(0, race.compareCounter(0, "relaxed"));
    }

    @Test
    public void twoMissingTracesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Race(null, null));
    }

    @Test
    public void negativeIndexIsRefused() {
        Race race = Race.of(report("kruskal", "prim"));
        assertThrows(IllegalArgumentException.class, () -> race.stepA(-1));
    }

    @Test
    public void runTimeIsMeasured() {
        Comparison.Report r = report("dijkstra", "bellman-ford");
        assertTrue(r.a().nanos() > 0);
        assertTrue(r.b().nanos() > 0);
    }

    @Test
    public void nanosAreWrittenInPlainUnits() {
        assertEquals("1 µs", Race.formatNanos(400));
        assertEquals("830 µs", Race.formatNanos(830_000));
        assertEquals("1.25 ms", Race.formatNanos(1_250_000));
    }

    @Test
    public void relaxationCountersAreComparableAtTheEnd() {
        Race race = Race.of(report("dijkstra", "bellman-ford"));
        int end = race.length() - 1;
        int cmp = race.compareCounter(end, "relaxed");
        assertTrue(cmp >= -1 && cmp <= 1);
    }
}

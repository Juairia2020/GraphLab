package com.graphlab.engine.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Test;

public class MasteryTest {
    private static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");

    @Test
    public void unknownTopicsAreZeroAndNeverDue() {
        Mastery m = new Mastery();
        assertEquals(0.0, m.value("bfs", T0), 0);
        assertFalse(m.isDue("bfs", T0));
        assertFalse(m.practised("bfs"));
    }

    @Test
    public void rightAnswersRaiseAndWrongAnswersLower() {
        Mastery m = new Mastery();
        m.record("bfs", true, T0);
        double one = m.value("bfs", T0);
        assertEquals(Mastery.RATE, one, 1e-12);
        m.record("bfs", true, T0);
        assertTrue(m.value("bfs", T0) > one);
        double high = m.value("bfs", T0);
        m.record("bfs", false, T0);
        assertEquals(high * (1 - Mastery.RATE), m.value("bfs", T0), 1e-12);
    }

    @Test
    public void masteryDecaysAsExpOfMinusDaysOverTau() {
        Mastery m = new Mastery(10.0);
        for (int i = 0; i < 6; i++) {
            m.record("dfs", true, T0);
        }
        double start = m.value("dfs", T0);
        assertEquals(start * Math.exp(-3.0 / 10.0), m.value("dfs", T0.plus(Duration.ofDays(3))), 1e-9);
        assertEquals(start * Math.exp(-0.5 / 10.0), m.value("dfs", T0.plus(Duration.ofHours(12))), 1e-9);
        // never grows when time goes backwards
        assertEquals(start, m.value("dfs", T0.minus(Duration.ofDays(2))), 1e-12);
    }

    @Test
    public void aTopicBecomesDueAsItIsForgotten() {
        Mastery m = new Mastery();
        for (int i = 0; i < 6; i++) {
            m.record("prim", true, T0);
        }
        assertFalse(m.isDue("prim", T0));
        assertTrue(m.isDue("prim", T0.plus(Duration.ofDays(30))));
        assertEquals(List.of("prim"), m.dueTopics(T0.plus(Duration.ofDays(30))));
    }

    @Test
    public void dueTopicsAreListedWeakestFirst() {
        Mastery m = new Mastery();
        m.record("a", true, T0);
        m.record("b", true, T0);
        m.record("b", false, T0);
        m.record("c", true, T0);
        m.record("c", true, T0);
        m.record("c", true, T0);
        m.record("c", true, T0);
        m.record("c", true, T0);
        m.record("c", true, T0);
        List<String> due = m.dueTopics(T0);
        assertEquals(List.of("b", "a"), due);
    }

    @Test
    public void timeUsesInstantsSoATimeZoneChangeCannotMatter() {
        // the value depends only on the instants, never on a zone
        Mastery m = new Mastery();
        m.record("x", true, Instant.parse("2026-10-01T23:30:00Z"));
        assertEquals(m.value("x", Instant.parse("2026-10-02T00:30:00Z")),
                Mastery.parse(m.encode()).value("x", Instant.parse("2026-10-02T00:30:00Z")), 1e-12);
    }

    @Test
    public void saveAndLoadRoundTripAndDamageIsSkipped() {
        Mastery m = new Mastery();
        m.record("bfs", true, T0);
        m.record("dijkstra", false, T0);
        m.record("dijkstra", true, T0);
        Mastery back = Mastery.parse(m.encode());
        assertEquals(m.value("bfs", T0), back.value("bfs", T0), 1e-12);
        assertEquals(m.encode(), back.encode());
        Mastery damaged = Mastery.parse("bfs=0.5@100,oops,x=2@5,y=0.1@zz,=0.1@4,z=0.2@7");
        assertEquals(List.of("bfs", "z"), damaged.topics());
        assertTrue(Mastery.parse(null).topics().isEmpty());
    }

    @Test
    public void mergeKeepsTheNewestEntryOfEachTopic() {
        Mastery a = new Mastery();
        a.record("bfs", true, T0);
        Mastery b = new Mastery();
        b.record("bfs", false, T0.plus(Duration.ofDays(1)));
        b.record("dfs", true, T0);
        Mastery joined = a.merge(b);
        assertEquals(b.value("bfs", T0.plus(Duration.ofDays(1))), joined.value("bfs", T0.plus(Duration.ofDays(1))), 1e-12);
        assertTrue(joined.practised("dfs"));
        assertEquals(joined.encode(), b.merge(a).encode());
    }
}

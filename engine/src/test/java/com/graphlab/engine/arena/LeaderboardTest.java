package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;

import com.graphlab.engine.arena.Leaderboard.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class LeaderboardTest {
    @Test
    public void rankingMatchesBruteForceAndKeepsEachPlayersBest() {
        Random r = new Random(5);
        for (int round = 0; round < 200; round++) {
            List<Entry> all = new ArrayList<>();
            int n = r.nextInt(30);
            for (int i = 0; i < n; i++) {
                all.add(new Entry("u" + r.nextInt(12), "n", r.nextInt(7) * 20, r.nextInt(5) * 10, r.nextInt(4)));
            }
            // reference: pick the best entry per player by scanning, then sort with a different comparator style
            Map<String, Entry> best = new HashMap<>();
            for (Entry e : all) {
                Entry o = best.get(e.uid());
                if (o == null || e.score() > o.score() || (e.score() == o.score() && (e.seconds() < o.seconds()
                        || (e.seconds() == o.seconds() && e.submittedMillis() < o.submittedMillis())))) {
                    best.put(e.uid(), e);
                }
            }
            List<Entry> expected = new ArrayList<>(best.values());
            expected.sort(Comparator.comparing((Entry e) -> -e.score()).thenComparing(e -> e.seconds())
                    .thenComparing(e -> e.submittedMillis()).thenComparing(e -> e.uid()));
            assertEquals(expected, Leaderboard.top(all, 100));
            assertEquals(Math.min(5, expected.size()), Leaderboard.top(all, 5).size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(i + 1, Leaderboard.rankOf(all, expected.get(i).uid()));
            }
            assertEquals(0, Leaderboard.rankOf(all, "nobody"));
        }
    }

    @Test
    public void ordersAreTheSameWhateverTheInputOrder() {
        List<Entry> a = new ArrayList<>(List.of(new Entry("a", "A", 300, 40, 1), new Entry("b", "B", 300, 30, 2),
                new Entry("c", "C", 450, 90, 3)));
        List<Entry> b = new ArrayList<>(a);
        java.util.Collections.reverse(b);
        assertEquals(Leaderboard.top(a, 3), Leaderboard.top(b, 3));
        assertEquals("c", Leaderboard.top(a, 3).get(0).uid());
        assertEquals("b", Leaderboard.top(a, 3).get(1).uid());
        assertEquals(0, Leaderboard.top(a, 0).size());
        assertEquals(0, Leaderboard.top(a, -2).size());
    }
}

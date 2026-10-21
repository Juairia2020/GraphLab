package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.arena.MistakeHeap.Mistake;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class MistakeHeapTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;

    /** Brute force: sum the weights with Math.pow, sort with a plain comparator. */
    private static List<QuestionKind> reference(List<Mistake> ms, Set<QuestionKind> allowed) {
        Map<QuestionKind, Double> w = new LinkedHashMap<>();
        Map<QuestionKind, Long> latest = new LinkedHashMap<>();
        for (Mistake m : ms) {
            if (allowed != null && !allowed.contains(m.kind())) {
                continue;
            }
            double age = Math.max(0, NOW - m.atMillis()) / (double) DAY;
            w.merge(m.kind(), m.level().multiplier() * Math.pow(2, -age / 7), Double::sum);
            latest.merge(m.kind(), m.atMillis(), Math::max);
        }
        List<QuestionKind> kinds = new ArrayList<>(w.keySet());
        kinds.sort(Comparator.<QuestionKind>comparingDouble(k -> -w.get(k)).thenComparingLong(k -> -latest.get(k))
                .thenComparing(QuestionKind::id));
        return kinds;
    }

    @Test
    public void rankingMatchesBruteForce() {
        Random r = new Random(17);
        QuestionKind[] kinds = QuestionKind.values();
        for (int round = 0; round < 300; round++) {
            List<Mistake> ms = new ArrayList<>();
            int n = r.nextInt(40);
            for (int i = 0; i < n; i++) {
                ms.add(new Mistake(kinds[r.nextInt(kinds.length)], Level.values()[r.nextInt(3)], NOW - r.nextInt(60) * DAY / 3));
            }
            List<QuestionKind> got = new ArrayList<>();
            for (MistakeHeap.Entry e : MistakeHeap.ranked(ms, NOW, null)) {
                got.add(e.kind());
            }
            assertEquals(reference(ms, null), got);
        }
    }

    @Test
    public void recentAndSevereMistakesComeFirst() {
        List<Mistake> ms = List.of(
                new Mistake(QuestionKind.BFS_ORDER, Level.HARD, NOW - 60 * DAY),     // old
                new Mistake(QuestionKind.DFS_ORDER, Level.EASY, NOW - DAY),          // recent, easy
                new Mistake(QuestionKind.MAX_FLOW, Level.HARD, NOW - DAY));          // recent, hard
        List<MistakeHeap.Entry> r = MistakeHeap.ranked(ms, NOW, null);
        assertEquals(QuestionKind.MAX_FLOW, r.get(0).kind());
        assertEquals(QuestionKind.DFS_ORDER, r.get(1).kind());
        assertEquals(QuestionKind.BFS_ORDER, r.get(2).kind());
    }

    @Test
    public void aWeekHalvesTheWeight() {
        Mistake m = new Mistake(QuestionKind.BFS_ORDER, Level.HARD, NOW - 7 * DAY);
        assertEquals(1.5, m.weight(NOW), 1e-9);
        assertEquals(3.0, new Mistake(QuestionKind.BFS_ORDER, Level.HARD, NOW + DAY).weight(NOW), 1e-9);
    }

    @Test
    public void repeatedMistakesAddUp() {
        List<Mistake> ms = new ArrayList<>();
        ms.add(new Mistake(QuestionKind.MAX_FLOW, Level.MEDIUM, NOW));
        for (int i = 0; i < 3; i++) {
            ms.add(new Mistake(QuestionKind.BFS_ORDER, Level.EASY, NOW));
        }
        assertEquals(QuestionKind.BFS_ORDER, MistakeHeap.ranked(ms, NOW, null).get(0).kind());   // 3 > 2
    }

    @Test
    public void ghostUsesDistinctKindsAtMostFiveAndTheLevelThatHurtMost() {
        List<Mistake> ms = new ArrayList<>();
        QuestionKind[] kinds = QuestionKind.values();
        for (int i = 0; i < 12; i++) {
            ms.add(new Mistake(kinds[i], Level.EASY, NOW - i * DAY));
            ms.add(new Mistake(kinds[i], Level.EASY, NOW - i * DAY - 1000));
        }
        ms.add(new Mistake(kinds[3], Level.HARD, NOW - 3 * DAY));
        List<QuestionSpec> specs = MistakeHeap.ghost(ms, NOW, 5L, null);
        assertEquals(5, specs.size());
        assertEquals(5, MistakeHeap.kinds(specs).size());
        for (QuestionSpec s : specs) {
            if (s.kind() == kinds[3]) {
                assertEquals(Level.HARD, s.level());
            }
        }
        assertEquals(specs, MistakeHeap.ghost(ms, NOW, 5L, null));
        assertTrue(!specs.equals(MistakeHeap.ghost(ms, NOW, 6L, null)));
        ArenaRound round = ArenaRound.fromSpecs(Level.MEDIUM, 5L, specs);
        assertEquals(5, round.size());
    }

    @Test
    public void ghostWithFewMistakesIsShortAndWithNoneIsEmpty() {
        assertTrue(MistakeHeap.ghost(List.of(), NOW, 1L, null).isEmpty());
        List<Mistake> two = List.of(new Mistake(QuestionKind.BFS_ORDER, Level.EASY, NOW),
                new Mistake(QuestionKind.BFS_ORDER, Level.EASY, NOW - DAY));
        assertEquals(1, MistakeHeap.ghost(two, NOW, 1L, null).size());
    }

    @Test
    public void lockedActivitiesAreLeftOut() {
        List<Mistake> ms = List.of(new Mistake(QuestionKind.MAX_FLOW, Level.HARD, NOW),
                new Mistake(QuestionKind.BFS_ORDER, Level.EASY, NOW));
        Set<QuestionKind> allowed = new HashSet<>(List.of(QuestionKind.BFS_ORDER));
        List<QuestionSpec> specs = MistakeHeap.ghost(ms, NOW, 1L, allowed);
        assertEquals(1, specs.size());
        assertEquals(QuestionKind.BFS_ORDER, specs.get(0).kind());
    }
}

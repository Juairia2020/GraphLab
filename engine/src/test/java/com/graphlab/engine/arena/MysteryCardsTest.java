package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MysteryCardsTest {
    @Test
    public void threeDifferentActivitiesFromThePool() {
        List<QuestionKind> pool = List.of(QuestionKind.values());
        for (long seed = 0; seed < 500; seed++) {
            List<QuestionKind> cards = MysteryCards.draw(pool, seed);
            assertEquals(3, cards.size());
            assertEquals(3, new HashSet<>(cards).size());
            assertTrue(pool.containsAll(cards));
            assertEquals(cards, MysteryCards.draw(pool, seed));
        }
    }

    @Test
    public void smallPoolsGiveFewerCardsAndDuplicatesAreIgnored() {
        assertEquals(2, MysteryCards.draw(List.of(QuestionKind.BFS_ORDER, QuestionKind.DFS_ORDER), 1L).size());
        assertEquals(0, MysteryCards.draw(List.of(), 1L).size());
        assertEquals(1, MysteryCards.draw(List.of(QuestionKind.BFS_ORDER, QuestionKind.BFS_ORDER, QuestionKind.BFS_ORDER), 1L).size());
    }

    @Test
    public void everyActivityAndEveryPositionIsEquallyLikely() {
        List<QuestionKind> pool = new ArrayList<>(List.of(QuestionKind.values()).subList(0, 6));
        int[] count = new int[6];
        int[][] place = new int[3][6];
        int trials = 12000;
        for (long seed = 0; seed < trials; seed++) {
            List<QuestionKind> cards = MysteryCards.draw(pool, seed * 7919 + 13);
            for (int p = 0; p < 3; p++) {
                int idx = pool.indexOf(cards.get(p));
                count[idx]++;
                place[p][idx]++;
            }
        }
        for (int i = 0; i < 6; i++) {
            assertEquals("kind " + i, trials * 3 / 6.0, count[i], trials * 0.03);
            for (int p = 0; p < 3; p++) {
                assertEquals("kind " + i + " place " + p, trials / 6.0, place[p][i], trials * 0.02);
            }
        }
    }

    @Test
    public void allTwentySixPlusSixActivitiesCanBeDrawn() {
        Set<QuestionKind> seen = new HashSet<>();
        List<QuestionKind> pool = List.of(QuestionKind.values());
        for (long seed = 0; seed < 2000; seed++) {
            seen.addAll(MysteryCards.draw(pool, seed));
        }
        assertEquals(pool.size(), seen.size());
    }

    @Test
    public void specsSurviveEncodingAndBuildTheSameRound() {
        List<QuestionSpec> specs = DailyPuzzle.specs("2026-10-15");
        List<QuestionSpec> back = QuestionSpec.decode(QuestionSpec.encode(specs));
        assertEquals(specs, back);
        ArenaRound a = ArenaRound.fromSpecs(Level.MEDIUM, 1L, specs);
        ArenaRound b = ArenaRound.fromSpecs(Level.MEDIUM, 1L, back);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.question(i), b.question(i));
        }
        for (String bad : new String[] {"", "bfs-order:EASY", "nope:EASY:1", "bfs-order:LOW:1", "bfs-order:EASY:x"}) {
            try {
                QuestionSpec.decode(bad);
                org.junit.Assert.fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }
}

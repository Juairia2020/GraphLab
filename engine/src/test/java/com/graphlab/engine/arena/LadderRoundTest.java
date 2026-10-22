package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** MS20: tapping an activity plays three questions, Easy then Medium then Hard, with fresh random questions each time. */
public class LadderRoundTest {

    private static void play(ArenaRound r, boolean[] right) {
        for (int i = 0; i < right.length; i++) {
            Question q = r.current();
            r.answer(right[i] ? q.correctIndex() : (q.correctIndex() + 1) % Math.max(2, q.options().size()));
            if (r.hasNext()) {
                r.advance();
            }
        }
    }

    @Test
    public void everyActivityGivesEasyThenMediumThenHard() {
        for (QuestionKind kind : QuestionKind.values()) {
            ArenaRound r = ArenaRound.ladder(kind, 5L);
            assertEquals(kind.id(), 3, r.size());
            assertEquals(kind.id(), Level.EASY, r.question(0).level());
            assertEquals(kind.id(), Level.MEDIUM, r.question(1).level());
            assertEquals(kind.id(), Level.HARD, r.question(2).level());
            assertEquals(kind, r.kind());
            assertFalse(r.isMixed());
        }
    }

    @Test
    public void theSameSeedRebuildsTheSameRoundForARotation() {
        for (QuestionKind kind : QuestionKind.values()) {
            ArenaRound a = ArenaRound.ladder(kind, 77L);
            ArenaRound b = ArenaRound.ladder(kind, 77L);
            for (int i = 0; i < 3; i++) {
                assertEquals(kind.id(), a.question(i).prompt(), b.question(i).prompt());
                assertEquals(kind.id(), a.question(i).correctIndex(), b.question(i).correctIndex());
            }
        }
    }

    @Test
    public void aNewSeedGivesNewQuestions() {
        for (QuestionKind kind : QuestionKind.values()) {
            Set<String> hardQuestions = new HashSet<>();
            for (long seed = 1; seed <= 12; seed++) {
                Question q = ArenaRound.ladder(kind, seed * 7919L).question(2);
                hardQuestions.add(q.prompt() + "|" + q.input() + "|" + q.options() + "|" + q.explanation());
            }
            assertTrue(kind.id() + " gave the same Hard question every time", hardQuestions.size() > 1);
        }
    }

    @Test
    public void pointsAreTenTwentyThirty() {
        ArenaRound r = ArenaRound.ladder(QuestionKind.BFS_ORDER, 3L);
        assertEquals(60, r.maxPoints());
        play(r, new boolean[] {true, true, true});
        assertEquals(60, r.points());
        assertEquals(3, r.stars());
    }

    @Test
    public void starsFollowThePointsWon() {
        ArenaRound onlyEasy = ArenaRound.ladder(QuestionKind.BFS_ORDER, 3L);
        play(onlyEasy, new boolean[] {true, false, false});
        assertEquals(10, onlyEasy.points());
        assertEquals(1, onlyEasy.stars());

        ArenaRound hardOnly = ArenaRound.ladder(QuestionKind.BFS_ORDER, 3L);
        play(hardOnly, new boolean[] {false, false, true});
        assertEquals(30, hardOnly.points());
        assertEquals("half of the points", 2, hardOnly.stars());

        ArenaRound none = ArenaRound.ladder(QuestionKind.BFS_ORDER, 3L);
        play(none, new boolean[] {false, false, false});
        assertEquals(0, none.points());
        assertEquals(0, none.stars());
    }

    @Test
    public void starsByPointsEdges() {
        assertEquals(3, ArenaScoring.starsByPoints(60, 60));
        assertEquals(2, ArenaScoring.starsByPoints(59, 60));
        assertEquals(2, ArenaScoring.starsByPoints(30, 60));
        assertEquals(1, ArenaScoring.starsByPoints(29, 60));
        assertEquals(1, ArenaScoring.starsByPoints(10, 60));
        assertEquals(0, ArenaScoring.starsByPoints(0, 60));
    }

    @Test(expected = IllegalArgumentException.class)
    public void starsByPointsRefusesMorePointsThanOnOffer() {
        ArenaScoring.starsByPoints(61, 60);
    }

    @Test
    public void climbRampsFromEasyToHard() {
        assertEquals(Level.EASY, ArenaScoring.climb(0, 5));
        assertEquals(Level.EASY, ArenaScoring.climb(1, 5));
        assertEquals(Level.MEDIUM, ArenaScoring.climb(2, 5));
        assertEquals(Level.MEDIUM, ArenaScoring.climb(3, 5));
        assertEquals(Level.HARD, ArenaScoring.climb(4, 5));
        assertEquals(Level.EASY, ArenaScoring.climb(0, 1));
        assertEquals(Level.HARD, ArenaScoring.climb(29, 30));
    }

    @Test
    public void mixedAndPlannedRoundsClimbAndNeverRepeatAnActivityBackToBack() {
        List<QuestionKind> all = List.of(QuestionKind.values());
        for (long seed = 1; seed <= 30; seed++) {
            ArenaRound m = ArenaRound.mixedClimbing(seed, NeedModel.neutral(), all);
            assertEquals(5, m.size());
            assertEquals(Level.EASY, m.question(0).level());
            assertEquals(Level.HARD, m.question(4).level());
            for (int i = 1; i < 5; i++) {
                assertTrue(m.question(i).kind() != m.question(i - 1).kind());
            }
        }
        ArenaRound p = ArenaRound.plannedClimbing(9L, List.of(QuestionKind.BFS_ORDER, QuestionKind.DFS_ORDER, QuestionKind.BFS_ORDER));
        assertEquals(Level.EASY, p.question(0).level());
        assertEquals(Level.MEDIUM, p.question(1).level());
        assertEquals(Level.HARD, p.question(2).level());
    }

    @Test
    public void aFinishedActivityRoundIsKeptOnceAndOnlyImproves() {
        ArenaProgress p = new ArenaProgress();
        QuestionKind k = QuestionKind.BFS_ORDER;
        assertEquals(0, p.starsOf(k));
        ArenaProgress.Outcome first = p.recordActivity(k, 1, 10);
        assertTrue(first.newBest());
        ArenaProgress.Outcome worse = p.recordActivity(k, 0, 0);
        assertFalse(worse.newBest());
        assertEquals(1, p.starsOf(k));
        p.recordActivity(k, 3, 60);
        assertEquals(3, p.starsOf(k));
        assertEquals(new ArenaProgress.Best(3, 60, 3), p.bestActivity(k));
        assertEquals(3, p.totalStars());
        assertEquals(60, p.totalPoints());
        assertEquals(3, p.totalPlays());
    }

    @Test
    public void activityResultsSurviveSavingAndOldSavesStillCount() {
        ArenaProgress p = new ArenaProgress();
        p.recordActivity(QuestionKind.BFS_ORDER, 2, 40);
        p.recordMixedRound(1, 30);
        p.record(QuestionKind.DFS_ORDER, Level.EASY, 5, 5);   // an earlier build's result
        ArenaProgress copy = ArenaProgress.decode(p.encode());
        assertEquals(p.bestActivity(QuestionKind.BFS_ORDER), copy.bestActivity(QuestionKind.BFS_ORDER));
        assertEquals(p.bestMixedRound(), copy.bestMixedRound());
        assertEquals(3, copy.starsOf(QuestionKind.DFS_ORDER));
        assertTrue(UnlockGraph.clearedIn(copy).test(QuestionKind.DFS_ORDER));
        assertTrue(UnlockGraph.clearedIn(copy).test(QuestionKind.BFS_ORDER));
        assertFalse(UnlockGraph.clearedIn(copy).test(QuestionKind.NEGATIVE_CYCLE));
    }

    @Test
    public void oneStarInAThreeStepRoundOpensTheNextActivity() {
        UnlockGraph graph = UnlockGraph.bundled();
        ArenaProgress p = new ArenaProgress();
        assertFalse(graph.isUnlocked(QuestionKind.DIJKSTRA_DISTANCE, UnlockGraph.clearedIn(p)));
        p.recordActivity(QuestionKind.BFS_ORDER, 1, 10);
        assertTrue(graph.isUnlocked(QuestionKind.DIJKSTRA_DISTANCE, UnlockGraph.clearedIn(p)));
    }
}

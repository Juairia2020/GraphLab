package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArenaProgressTest {
    private static final QuestionKind K = QuestionKind.DIJKSTRA_DISTANCE;

    @Test
    public void starThresholdsForFiveQuestions() {
        assertEquals(0, ArenaScoring.stars(0, 5));
        assertEquals(0, ArenaScoring.stars(2, 5));
        assertEquals(1, ArenaScoring.stars(3, 5));
        assertEquals(2, ArenaScoring.stars(4, 5));
        assertEquals(3, ArenaScoring.stars(5, 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void starsRefuseMoreRightThanAsked() {
        ArenaScoring.stars(6, 5);
    }

    @Test
    public void pointsGrowWithTheLevel() {
        assertEquals(30, ArenaScoring.points(3, Level.EASY));
        assertEquals(60, ArenaScoring.points(3, Level.MEDIUM));
        assertEquals(90, ArenaScoring.points(3, Level.HARD));
    }

    @Test
    public void easyIsAlwaysOpenOthersNeedStars() {
        ArenaProgress p = new ArenaProgress();
        assertTrue(p.isUnlocked(K, Level.EASY));
        assertFalse(p.isUnlocked(K, Level.MEDIUM));
        assertFalse(p.isUnlocked(K, Level.HARD));
        p.record(K, Level.EASY, 2, 5);                 // no star
        assertFalse(p.isUnlocked(K, Level.MEDIUM));
        p.record(K, Level.EASY, 3, 5);                 // one star
        assertTrue(p.isUnlocked(K, Level.MEDIUM));
        assertFalse(p.isUnlocked(K, Level.HARD));
        p.record(K, Level.MEDIUM, 3, 5);               // one star is not enough for Hard
        assertFalse(p.isUnlocked(K, Level.HARD));
        p.record(K, Level.MEDIUM, 4, 5);               // two stars
        assertTrue(p.isUnlocked(K, Level.HARD));
        assertFalse("another activity stays locked", p.isUnlocked(QuestionKind.BFS_ORDER, Level.MEDIUM));
    }

    @Test
    public void recordKeepsTheBestAndCountsPlays() {
        ArenaProgress p = new ArenaProgress();
        ArenaProgress.Outcome first = p.record(K, Level.EASY, 4, 5);
        assertEquals(2, first.stars());
        assertEquals(40, first.points());
        assertTrue(first.newBest());
        ArenaProgress.Outcome worse = p.record(K, Level.EASY, 3, 5);
        assertFalse(worse.newBest());
        assertEquals(new ArenaProgress.Best(2, 40, 2), p.best(K, Level.EASY));
        ArenaProgress.Outcome better = p.record(K, Level.EASY, 5, 5);
        assertTrue(better.newBest());
        assertEquals(new ArenaProgress.Best(3, 50, 3), p.best(K, Level.EASY));
    }

    @Test
    public void recordReportsANewlyOpenedLevelOnlyOnce() {
        ArenaProgress p = new ArenaProgress();
        assertFalse(p.record(K, Level.EASY, 1, 5).unlockedNextLevel());
        assertTrue(p.record(K, Level.EASY, 3, 5).unlockedNextLevel());
        assertFalse("already open", p.record(K, Level.EASY, 5, 5).unlockedNextLevel());
        assertFalse("Hard has no next level", p.record(K, Level.HARD, 5, 5).unlockedNextLevel());
    }

    @Test
    public void totalsAddUpTheBestOfEveryEntry() {
        ArenaProgress p = new ArenaProgress();
        p.record(K, Level.EASY, 5, 5);                       // 3 stars, 50
        p.record(QuestionKind.BFS_ORDER, Level.EASY, 4, 5);  // 2 stars, 40
        p.recordMixed(Level.EASY, 3, 5);                     // 1 star, 30
        assertEquals(6, p.totalStars());
        assertEquals(120, p.totalPoints());
        assertEquals(3, p.totalPlays());
        assertEquals("mixed rounds are not part of the per-activity count", 5, p.starsAt(Level.EASY));
    }

    @Test
    public void mixedRoundsUnlockOverAllActivities() {
        ArenaProgress p = new ArenaProgress();
        assertTrue(p.isMixedUnlocked(Level.EASY));
        assertFalse(p.isMixedUnlocked(Level.MEDIUM));
        p.record(K, Level.EASY, 5, 5);                 // 3 stars at Easy
        assertTrue(p.isMixedUnlocked(Level.MEDIUM));
        assertFalse(p.isMixedUnlocked(Level.HARD));
        p.record(QuestionKind.BFS_ORDER, Level.MEDIUM, 5, 5);
        p.record(QuestionKind.DFS_ORDER, Level.MEDIUM, 5, 5);
        assertTrue(p.isMixedUnlocked(Level.HARD));     // 6 stars at Medium
    }

    @Test
    public void unlockRulesAreExposedForTheScreen() {
        assertEquals(0, ArenaProgress.starsToUnlock(Level.EASY));
        assertEquals(1, ArenaProgress.starsToUnlock(Level.MEDIUM));
        assertEquals(2, ArenaProgress.starsToUnlock(Level.HARD));
        assertEquals(3, ArenaProgress.mixedStarsToUnlock(Level.MEDIUM));
        assertEquals(6, ArenaProgress.mixedStarsToUnlock(Level.HARD));
    }

    @Test
    public void encodeAndDecodeRoundTrip() {
        ArenaProgress p = new ArenaProgress();
        p.record(K, Level.EASY, 5, 5);
        p.record(K, Level.MEDIUM, 4, 5);
        p.record(QuestionKind.NEGATIVE_CYCLE, Level.HARD, 3, 5);
        p.recordMixed(Level.MEDIUM, 3, 5);
        ArenaProgress copy = ArenaProgress.decode(p.encode());
        assertEquals(p.encode(), copy.encode());
        assertEquals(p.totalStars(), copy.totalStars());
        assertEquals(p.totalPoints(), copy.totalPoints());
        assertEquals(p.best(K, Level.MEDIUM), copy.best(K, Level.MEDIUM));
        assertEquals(p.bestMixed(Level.MEDIUM), copy.bestMixed(Level.MEDIUM));
    }

    @Test
    public void decodeSkipsDamagedAndUnknownEntries() {
        String text = "dijkstra-distance:EASY=3,50,2;;garbage;bfs-order:NOPE=1,1,1;unknown-kind:EASY=1,1,1;"
                + "dfs-order:EASY=x,1,1;dfs-order:EASY=1,2;mixed:HARD=9,-5,1";
        ArenaProgress p = ArenaProgress.decode(text);
        assertEquals(new ArenaProgress.Best(3, 50, 2), p.best(QuestionKind.DIJKSTRA_DISTANCE, Level.EASY));
        assertEquals("stars clamp to 3, points to 0", new ArenaProgress.Best(3, 0, 1), p.bestMixed(Level.HARD));
        assertEquals(0, p.stars(QuestionKind.DFS_ORDER, Level.EASY));
        assertEquals(6, p.totalStars());
    }

    @Test
    public void decodeOfNothingIsEmpty() {
        assertEquals("", ArenaProgress.decode(null).encode());
        assertEquals("", ArenaProgress.decode("  ").encode());
    }

    @Test
    public void clearForgetsEverything() {
        ArenaProgress p = new ArenaProgress();
        p.record(K, Level.EASY, 5, 5);
        p.clear();
        assertEquals(0, p.totalStars());
        assertEquals("", p.encode());
    }
}

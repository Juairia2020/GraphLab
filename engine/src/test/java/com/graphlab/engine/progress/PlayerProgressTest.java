package com.graphlab.engine.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.content.ContentException;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public class PlayerProgressTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant D1 = Instant.parse("2026-10-12T10:00:00Z");

    private static Instant day(int n) {
        return D1.plusSeconds(86_400L * (n - 1));
    }

    @Test
    public void aRoundGivesXpGemsAndStartsTheStreak() {
        PlayerProgress p = PlayerProgress.EMPTY.withRound(60, 2, D1, UTC);
        assertEquals(60, p.xp());
        assertEquals(2, p.gems());
        assertEquals(1, p.streak().current());
    }

    @Test
    public void aPuzzlePaysOnlyForNewStars() {
        PlayerProgress p = PlayerProgress.EMPTY.withPuzzle("w3-1", 2, D1, UTC);
        assertEquals(2 * PlayerProgress.XP_PER_PUZZLE_STAR, p.xp());
        assertEquals(2, p.gems());
        PlayerProgress again = p.withPuzzle("w3-1", 2, D1, UTC);
        assertEquals(p.xp(), again.xp());
        assertEquals(p.gems(), again.gems());
        PlayerProgress better = again.withPuzzle("w3-1", 3, D1, UTC);
        assertEquals(p.xp() + PlayerProgress.XP_PER_PUZZLE_STAR, better.xp());
        assertEquals(3, better.puzzleStarsOf("w3-1"));
        PlayerProgress worse = better.withPuzzle("w3-1", 1, D1, UTC);
        assertEquals(3, worse.puzzleStarsOf("w3-1"));
        assertEquals(better.xp(), worse.xp());
        assertEquals(3, worse.totalPuzzleStars());
    }

    @Test
    public void aSevenDayStreakPaysABonusOnce() {
        PlayerProgress p = PlayerProgress.EMPTY;
        for (int d = 1; d <= 7; d++) {
            p = p.withRound(10, 0, day(d), UTC);
        }
        assertEquals(7, p.streak().current());
        assertEquals(PlayerProgress.WEEK_BONUS_GEMS, p.gems());
        p = p.withRound(10, 0, day(7), UTC);   // same day again: no second bonus
        assertEquals(PlayerProgress.WEEK_BONUS_GEMS, p.gems());
    }

    @Test
    public void levelsFollowTheXpCurve() {
        PlayerProgress p = PlayerProgress.EMPTY;
        assertEquals(1, p.level());
        assertEquals(50, p.xpToNextLevel());
        p = p.withRound(50, 0, D1, UTC);
        assertEquals(2, p.level());
        assertEquals(150, p.xpToNextLevel());
        p = p.withRound(200, 0, D1, UTC);
        assertEquals(3, p.level());   // 250 xp: level 3 starts at 200, level 4 at 450
        assertEquals(200, p.xpToNextLevel());
    }

    @Test
    public void jsonRoundTripKeepsEverything() throws Exception {
        PlayerProgress p = PlayerProgress.EMPTY.withRound(80, 3, D1, UTC).withPuzzle("w3-2", 3, day(2), UTC);
        assertEquals(p, PlayerProgress.fromJson(p.toJson()));
        assertEquals(PlayerProgress.EMPTY, PlayerProgress.fromJson(PlayerProgress.EMPTY.toJson()));
    }

    @Test
    public void damagedJsonIsRefusedWithAMessage() {
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("[]"));
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("{\"v\":9,\"xp\":1,\"gems\":1}"));
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("{\"v\":1,\"xp\":-1,\"gems\":1}"));
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("{\"v\":1,\"xp\":1,\"gems\":1,\"stars\":{\"a\":7}}"));
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("{\"v\":1,\"xp\":1,\"gems\":1,\"streak\":{\"current\":5,\"best\":2}}"));
        assertThrows(ContentException.class, () -> PlayerProgress.fromJson("not json"));
    }

    @Test
    public void mergeTakesTheBestOfEachAndIsSymmetricAndRepeatable() {
        PlayerProgress phone = PlayerProgress.EMPTY.withRound(100, 2, day(1), UTC).withPuzzle("a", 3, day(1), UTC);
        PlayerProgress tablet = PlayerProgress.EMPTY.withRound(300, 1, day(1), UTC).withPuzzle("a", 1, day(1), UTC)
                .withPuzzle("b", 2, day(2), UTC);
        PlayerProgress joined = phone.merge(tablet);
        assertEquals(joined, tablet.merge(phone));
        assertEquals(joined, joined.merge(phone));
        assertEquals(joined, joined.merge(joined));
        assertTrue(joined.xp() >= Math.max(phone.xp(), tablet.xp()));
        assertEquals(3, joined.puzzleStarsOf("a"));
        assertEquals(2, joined.puzzleStarsOf("b"));
        assertEquals(2, joined.streak().current());
    }

    @Test
    public void invalidEarningsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PlayerProgress.EMPTY.withRound(-1, 0, D1, UTC));
        assertThrows(IllegalArgumentException.class, () -> PlayerProgress.EMPTY.withRound(1, 4, D1, UTC));
        assertThrows(IllegalArgumentException.class, () -> PlayerProgress.EMPTY.withPuzzle("x", 4, D1, UTC));
    }
}

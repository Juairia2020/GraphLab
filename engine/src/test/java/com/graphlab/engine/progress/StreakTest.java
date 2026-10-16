package com.graphlab.engine.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.Test;

public class StreakTest {
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");        // UTC+6
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");  // UTC-7 in October
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    public void firstActivityStartsAtOne() {
        Streak s = Streak.NONE.onActivity(at("2026-10-12T10:00:00Z"), UTC);
        assertEquals(1, s.current());
        assertEquals(LocalDate.of(2026, 10, 12), s.lastDay());
    }

    @Test
    public void twiceInOneDayCountsOnce() {
        Streak s = Streak.NONE.onActivity(at("2026-10-12T01:00:00Z"), UTC).onActivity(at("2026-10-12T23:00:00Z"), UTC);
        assertEquals(1, s.current());
    }

    @Test
    public void consecutiveDaysGrowAndAMissedDayResets() {
        Streak s = Streak.NONE;
        for (int day = 12; day <= 15; day++) {
            s = s.onActivity(at("2026-10-" + day + "T12:00:00Z"), UTC);
        }
        assertEquals(4, s.current());
        s = s.onActivity(at("2026-10-18T12:00:00Z"), UTC);   // 16 and 17 missed
        assertEquals(1, s.current());
        assertEquals(4, s.best());
    }

    @Test
    public void midnightIsMeasuredInTheLocalZone() {
        // 23:30 and 00:30 local are on different calendar days even though only an hour passed
        Streak s = Streak.NONE.onActivity(at("2026-10-12T17:30:00Z"), DHAKA);   // 23:30 in Dhaka
        s = s.onActivity(at("2026-10-12T18:30:00Z"), DHAKA);                      // 00:30 on the 13th
        assertEquals(2, s.current());
    }

    @Test
    public void flyingWestDoesNotCountADayTwice() {
        Streak s = Streak.NONE.onActivity(at("2026-10-12T17:00:00Z"), DHAKA);   // 12 Oct 23:00 in Dhaka
        // later the same 24 hours, now in Los Angeles it is still 12 Oct
        Streak after = s.onActivity(at("2026-10-12T20:00:00Z"), LA);
        assertEquals(1, after.current());
    }

    @Test
    public void flyingEastDoesNotBreakTheStreak() {
        Streak s = Streak.NONE.onActivity(at("2026-10-11T12:00:00Z"), LA).onActivity(at("2026-10-13T05:00:00Z"), LA);
        assertEquals(2, s.current());   // 11 Oct 05:00 and 12 Oct 22:00 local: two days
        // 20 hours later the player is in Dhaka and it is already the 14th there: two calendar dates later, one day for the person
        Streak after = s.onActivity(at("2026-10-14T01:00:00Z"), DHAKA);
        assertEquals(3, after.current());
    }

    @Test
    public void theClockGoingBackwardsChangesNothing() {
        Streak s = Streak.NONE.onActivity(at("2026-10-12T12:00:00Z"), UTC).onActivity(at("2026-10-13T12:00:00Z"), UTC);
        Streak back = s.onActivity(at("2026-10-09T12:00:00Z"), UTC);
        assertEquals(2, back.current());
        assertEquals(s.lastDay(), back.lastDay());
    }

    @Test
    public void shownStreakEndsWhenADayIsMissedButIsNotChanged() {
        Streak s = Streak.NONE.onActivity(at("2026-10-12T12:00:00Z"), UTC);
        assertEquals(1, s.shownAt(at("2026-10-13T20:00:00Z"), UTC));   // today is still open
        assertEquals(0, s.shownAt(at("2026-10-14T08:00:00Z"), UTC));   // 13th passed with nothing
        assertEquals(1, s.current());
        assertEquals(0, Streak.NONE.shownAt(at("2026-10-14T08:00:00Z"), UTC));
    }

    @Test
    public void mergeKeepsTheLaterCopyAndTheBestAndIsSymmetric() {
        Streak a = Streak.NONE.onActivity(at("2026-10-10T12:00:00Z"), UTC).onActivity(at("2026-10-11T12:00:00Z"), UTC);
        Streak b = a.onActivity(at("2026-10-12T12:00:00Z"), UTC);
        assertEquals(b, a.merge(b));
        assertEquals(b, b.merge(a));
        assertEquals(b, b.merge(b));
        assertEquals(Streak.NONE, Streak.NONE.merge(Streak.NONE));
        assertEquals(a, Streak.NONE.merge(a));
    }

    @Test
    public void inconsistentValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Streak(3, 2, LocalDate.of(2026, 1, 1), 5L));
        assertThrows(IllegalArgumentException.class, () -> new Streak(1, 1, null, 5L));
    }
}

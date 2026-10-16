package com.graphlab.engine.remind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class ReminderPlannerTest {
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private static ZonedDateTime at(String zone, int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi, 30), ZoneId.of(zone));
    }

    @Test
    public void laterTodayWhenTheTimeIsStillAhead() {
        ZonedDateTime now = at("Asia/Dhaka", 2026, 10, 9, 10, 0);   // 10:00:30
        long delay = ReminderPlanner.delayUntilNextMillis(now, 18, 0);
        assertEquals(Duration.ofHours(8).minusSeconds(30).toMillis(), delay);
    }

    @Test
    public void tomorrowWhenTheTimeHasPassed() {
        ZonedDateTime now = at("Asia/Dhaka", 2026, 10, 9, 19, 0);
        long delay = ReminderPlanner.delayUntilNextMillis(now, 18, 0);
        assertEquals(Duration.ofHours(23).minusSeconds(30).toMillis(), delay);
    }

    @Test
    public void exactlyNowCountsAsPassed() {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2026, 10, 9, 18, 0, 0), DHAKA);
        assertEquals(Duration.ofHours(24).toMillis(), ReminderPlanner.delayUntilNextMillis(now, 18, 0));
    }

    @Test
    public void justBeforeIsAboutOneSecondButNeverLess() {
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2026, 10, 9, 17, 59, 59, 600_000_000), DHAKA);
        assertEquals(1_000L, ReminderPlanner.delayUntilNextMillis(now, 18, 0));
    }

    @Test
    public void crossesMonthAndYearEnds() {
        ZonedDateTime now = at("Asia/Dhaka", 2026, 12, 31, 23, 50);
        long delay = ReminderPlanner.delayUntilNextMillis(now, 6, 30);
        assertEquals(Duration.ofHours(6).plusMinutes(40).minusSeconds(30).toMillis(), delay);
    }

    @Test
    public void followsDaylightSavingTimeNotJust24Hours() {
        // London springs forward on 2026-03-29: from 12:00 on the 28th to 12:00 on the 29th is only 23 real hours.
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 28, 12, 0, 0), ZoneId.of("Europe/London"));
        assertEquals(Duration.ofHours(23).toMillis(), ReminderPlanner.delayUntilNextMillis(now, 12, 0));
        // And in autumn (25 hours) on 2026-10-25.
        ZonedDateTime autumn = ZonedDateTime.of(LocalDateTime.of(2026, 10, 24, 12, 0, 0), ZoneId.of("Europe/London"));
        assertEquals(Duration.ofHours(25).toMillis(), ReminderPlanner.delayUntilNextMillis(autumn, 12, 0));
    }

    @Test
    public void aTimeThatDoesNotExistToday() {
        // 01:30 does not exist in London on 2026-03-29; the planner must still return a positive delay under 25 hours.
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 28, 22, 0, 0), ZoneId.of("Europe/London"));
        long delay = ReminderPlanner.delayUntilNextMillis(now, 1, 30);
        assertTrue(delay > 0 && delay < Duration.ofHours(25).toMillis());
    }

    @Test
    public void alwaysBetweenOneSecondAndOneDay() {
        for (int h = 0; h < 24; h++) {
            for (int m = 0; m < 60; m += 13) {
                ZonedDateTime now = at("Asia/Dhaka", 2026, 10, 9, 13, 37);
                long delay = ReminderPlanner.delayUntilNextMillis(now, h, m);
                assertTrue(delay >= 1_000L && delay <= Duration.ofHours(25).toMillis());
            }
        }
    }

    @Test
    public void rejectsImpossibleTimes() {
        ZonedDateTime now = at("Asia/Dhaka", 2026, 10, 9, 13, 0);
        assertThrows(IllegalArgumentException.class, () -> ReminderPlanner.delayUntilNextMillis(now, 24, 0));
        assertThrows(IllegalArgumentException.class, () -> ReminderPlanner.delayUntilNextMillis(now, 5, 60));
    }

    @Test
    public void theMessageChangesEveryDayAndNeverGoesOutOfRange() {
        int count = 5;
        for (long day = -10; day < 400; day++) {
            int i = ReminderPlanner.messageIndex(day, count);
            assertTrue(i >= 0 && i < count);
            assertEquals((i + 1) % count, ReminderPlanner.messageIndex(day + 1, count));
        }
        assertEquals(0, ReminderPlanner.messageIndex(12345, 1));
        assertThrows(IllegalArgumentException.class, () -> ReminderPlanner.messageIndex(1, 0));
    }

    @Test
    public void thePeriodIsOneDay() {
        assertEquals(24, ReminderPlanner.PERIOD_HOURS);
    }
}

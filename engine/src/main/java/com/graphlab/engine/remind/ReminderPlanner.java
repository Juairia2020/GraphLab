package com.graphlab.engine.remind;

import java.time.Duration;
import java.time.ZonedDateTime;

/** When the next reminder is due, and which message to show. Pure functions, so they are easy to test. */
public final class ReminderPlanner {
    /** The reminder repeats once a day. */
    public static final long PERIOD_HOURS = 24;
    private static final long MIN_DELAY_MILLIS = 1_000L;

    private ReminderPlanner() {
    }

    /**
     * Milliseconds from {@code now} until the next time the clock shows {@code hour:minute}: today if
     * that is still ahead, tomorrow otherwise (including exactly now). Daylight saving is handled by
     * working in the given time zone. Never less than one second.
     */
    public static long delayUntilNextMillis(ZonedDateTime now, int hour, int minute) {
        ReminderSettings.of(true, hour, minute);   // validates the time
        ZonedDateTime target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!target.isAfter(now)) {
            target = now.plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        }
        return Math.max(MIN_DELAY_MILLIS, Duration.between(now, target).toMillis());
    }

    /** Which of {@code count} messages to show on this day, so the text changes every day but repeats predictably. */
    public static int messageIndex(long epochDay, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("There must be at least one message");
        }
        return (int) Math.floorMod(epochDay, (long) count);
    }
}

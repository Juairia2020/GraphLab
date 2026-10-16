package com.graphlab.engine.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * The daily streak (MS12): how many days in a row the player did something. It is counted by calendar day, and it is safe
 * against a change of time zone.
 *
 * <p>Each day with activity is stored as a calendar date <em>and</em> as the exact instant. When the next activity comes, the
 * gap in days is measured twice: from the stored date, and from the stored instant read in the zone the player is in now.
 * The smaller gap counts. So flying east or west never breaks a streak that a person would say is unbroken, and the clock
 * going backwards (a zone change, or a wrong clock) never counts a day twice or loses one.
 *
 * <ul>
 *   <li>gap of 0 or less: the same day, nothing changes;</li>
 *   <li>gap of 1: the next day, the streak grows by one;</li>
 *   <li>gap of 2 or more: a whole day was missed, the streak starts again at 1.</li>
 * </ul>
 *
 * @param current   days in a row, 0 before any activity
 * @param best      the longest streak ever
 * @param lastDay   the calendar date of the last activity, in the zone the player was in then; null before any activity
 * @param lastMilli the instant of the last activity, in epoch milliseconds; 0 before any activity
 */
public record Streak(int current, int best, LocalDate lastDay, long lastMilli) {
    public static final Streak NONE = new Streak(0, 0, null, 0L);

    public Streak {
        if (current < 0 || best < current) {
            throw new IllegalArgumentException("A streak needs 0 <= current <= best");
        }
        if ((lastDay == null) != (lastMilli == 0L)) {
            throw new IllegalArgumentException("lastDay and lastMilli are both set or both empty");
        }
    }

    /** The streak after the player did something at {@code now}, in the zone they are in. */
    public Streak onActivity(Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (lastDay == null) {
            return new Streak(1, Math.max(best, 1), today, now.toEpochMilli());
        }
        long fromDate = ChronoUnit.DAYS.between(lastDay, today);
        long fromInstant = ChronoUnit.DAYS.between(LocalDate.ofInstant(Instant.ofEpochMilli(lastMilli), zone), today);
        long gap = Math.min(fromDate, fromInstant);
        if (gap <= 0) {
            // the same day (or the clock went back): nothing to count; remember the later instant
            return new Streak(current, best, lastDay, Math.max(lastMilli, now.toEpochMilli()));
        }
        int next = gap == 1 ? current + 1 : 1;
        return new Streak(next, Math.max(best, next), today, now.toEpochMilli());
    }

    /**
     * The streak as it should be shown at {@code now}: if more than a whole day was missed it has already ended, even
     * though nothing was recorded yet. Does not change the stored value.
     */
    public int shownAt(Instant now, ZoneId zone) {
        if (lastDay == null) {
            return 0;
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        long gap = Math.min(ChronoUnit.DAYS.between(lastDay, today),
                ChronoUnit.DAYS.between(LocalDate.ofInstant(Instant.ofEpochMilli(lastMilli), zone), today));
        return gap >= 2 ? 0 : current;
    }

    /**
     * Joins two copies of the same player's streak (two devices): the copy with the later activity wins; when both are on the
     * same day the larger count wins. The best is the larger best. Symmetric, and joining twice changes nothing.
     */
    public Streak merge(Streak other) {
        int bestEver = Math.max(best, other.best);
        if (lastDay == null && other.lastDay == null) {
            return new Streak(0, bestEver, null, 0L);
        }
        Streak later;
        if (lastMilli != other.lastMilli) {
            later = lastMilli > other.lastMilli ? this : other;
        } else {
            later = current >= other.current ? this : other;
        }
        int cur = later.current;
        if (lastDay != null && lastDay.equals(other.lastDay)) {
            cur = Math.max(current, other.current);
        }
        return new Streak(cur, Math.max(bestEver, cur), later.lastDay, later.lastMilli);
    }
}

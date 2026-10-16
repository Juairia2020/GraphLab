package com.graphlab.engine.progress;

import com.graphlab.engine.content.ContentException;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the player has earned (MS12): xp, gems, the daily {@link Streak} and the stars of every puzzle level. Immutable: every
 * change returns a new value. The app stores {@link #toJson()} in its database and in the player's cloud document, and joins
 * two copies with {@link #merge}. Plain Java, no Android.
 *
 * <p>Rules (starting values, easy to tune): a finished round gives its points as xp and one gem per star; a puzzle level gives
 * {@value #XP_PER_PUZZLE_STAR} xp and one gem for every star that is new (a replay earns only the difference); a streak of 7, 14,
 * 21 ... days gives {@value #WEEK_BONUS_GEMS} bonus gems once. Any of these counts as activity for the streak.
 */
public final class PlayerProgress {
    public static final int FORMAT_VERSION = 1;
    public static final int XP_PER_PUZZLE_STAR = 15;
    public static final int WEEK_BONUS_GEMS = 5;
    /** xp for level 2; level n needs 50 * (n - 1)^2 xp in all. */
    public static final int XP_UNIT = 50;

    public static final PlayerProgress EMPTY = new PlayerProgress(0L, 0, Streak.NONE, Map.of());

    private final long xp;
    private final int gems;
    private final Streak streak;
    private final Map<String, Integer> puzzleStars;

    private PlayerProgress(long xp, int gems, Streak streak, Map<String, Integer> puzzleStars) {
        if (xp < 0 || gems < 0) {
            throw new IllegalArgumentException("xp and gems cannot be negative");
        }
        this.xp = xp;
        this.gems = gems;
        this.streak = streak;
        for (Map.Entry<String, Integer> e : puzzleStars.entrySet()) {
            if (e.getValue() < 0 || e.getValue() > 3) {
                throw new IllegalArgumentException("Stars of " + e.getKey() + " must be 0 to 3");
            }
        }
        this.puzzleStars = Collections.unmodifiableMap(new TreeMap<>(puzzleStars));
    }

    public long xp() {
        return xp;
    }

    public int gems() {
        return gems;
    }

    public Streak streak() {
        return streak;
    }

    /** Best stars of each puzzle level id, only levels with 1 or more. */
    public Map<String, Integer> puzzleStars() {
        return puzzleStars;
    }

    public int puzzleStarsOf(String levelId) {
        return puzzleStars.getOrDefault(levelId, 0);
    }

    public int totalPuzzleStars() {
        int total = 0;
        for (int s : puzzleStars.values()) {
            total += s;
        }
        return total;
    }

    /** The player's level, from 1: level n starts at {@code 50 * (n - 1)^2} xp. */
    public int level() {
        return 1 + (int) Math.floor(Math.sqrt(xp / (double) XP_UNIT));
    }

    /** xp still needed for the next level. */
    public long xpToNextLevel() {
        long next = (long) XP_UNIT * level() * level();
        return next - xp;
    }

    /** The streak to show now (it has ended if a whole day was missed). */
    public int streakShown(Instant now, ZoneId zone) {
        return streak.shownAt(now, zone);
    }

    // ---- earning ---------------------------------------------------------------------

    /** A finished Arena round: its points as xp and one gem per star. */
    public PlayerProgress withRound(int points, int stars, Instant now, ZoneId zone) {
        if (points < 0 || stars < 0 || stars > 3) {
            throw new IllegalArgumentException("A round has 0 or more points and 0 to 3 stars");
        }
        return earn(points, stars, puzzleStars, now, zone);
    }

    /** A finished puzzle level with {@code stars}: pays only for stars better than the best so far. */
    public PlayerProgress withPuzzle(String levelId, int stars, Instant now, ZoneId zone) {
        if (stars < 0 || stars > 3) {
            throw new IllegalArgumentException("Stars must be 0 to 3");
        }
        int before = puzzleStarsOf(levelId);
        Map<String, Integer> next = new TreeMap<>(puzzleStars);
        int gained = Math.max(0, stars - before);
        if (stars > before) {
            next.put(levelId, stars);
        }
        return earn((long) gained * XP_PER_PUZZLE_STAR, gained, next, now, zone);
    }

    private PlayerProgress earn(long addXp, int addGems, Map<String, Integer> stars, Instant now, ZoneId zone) {
        Streak after = streak.onActivity(now, zone);
        int bonus = 0;
        if (after.current() > streak.current() && after.current() % 7 == 0) {
            bonus = WEEK_BONUS_GEMS;
        }
        return new PlayerProgress(xp + addXp, gems + addGems + bonus, after, stars);
    }

    // ---- joining two copies ----------------------------------------------------------

    /**
     * Joins this copy with another copy of the same player (another device, or the cloud). xp and gems take the larger value,
     * every level keeps its best stars, and the streak is joined by {@link Streak#merge}. Joining is symmetric and repeating it
     * changes nothing, so devices can exchange copies in any order.
     */
    public PlayerProgress merge(PlayerProgress other) {
        Map<String, Integer> stars = new TreeMap<>(puzzleStars);
        for (Map.Entry<String, Integer> e : other.puzzleStars.entrySet()) {
            stars.merge(e.getKey(), e.getValue(), Math::max);
        }
        return new PlayerProgress(Math.max(xp, other.xp), Math.max(gems, other.gems), streak.merge(other.streak), stars);
    }

    // ---- saving ----------------------------------------------------------------------

    public String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\"v\":").append(FORMAT_VERSION).append(",\"xp\":").append(xp).append(",\"gems\":").append(gems)
                .append(",\"streak\":{\"current\":").append(streak.current()).append(",\"best\":").append(streak.best());
        if (streak.lastDay() != null) {
            out.append(",\"lastDay\":\"").append(streak.lastDay()).append("\",\"lastMilli\":").append(streak.lastMilli());
        }
        out.append("},\"stars\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : puzzleStars.entrySet()) {
            out.append(first ? "" : ",").append('"').append(e.getKey().replace("\\", "").replace("\"", "")).append("\":").append(e.getValue());
            first = false;
        }
        return out.append("}}").toString();
    }

    /** @throws ContentException if the text is not a progress document this version can read */
    public static PlayerProgress fromJson(String json) throws ContentException {
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (GraphFormatException e) {
            throw new ContentException("The progress is not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> top)) {
            throw new ContentException("The progress must be a JSON object.");
        }
        int version = (int) whole(top.get("v"), "v");
        if (version < 1 || version > FORMAT_VERSION) {
            throw new ContentException("Progress format " + version + " is not known.");
        }
        long xp = whole(top.get("xp"), "xp");
        int gems = (int) whole(top.get("gems"), "gems");
        Streak streak = Streak.NONE;
        if (top.get("streak") instanceof Map<?, ?> s) {
            int current = (int) whole(s.get("current"), "streak.current");
            int best = (int) whole(s.get("best"), "streak.best");
            LocalDate day = null;
            long milli = 0L;
            if (s.get("lastDay") != null) {
                try {
                    day = LocalDate.parse(String.valueOf(s.get("lastDay")));
                } catch (java.time.format.DateTimeParseException e) {
                    throw new ContentException("streak.lastDay is not a date.");
                }
                milli = whole(s.get("lastMilli"), "streak.lastMilli");
            }
            try {
                streak = new Streak(current, best, day, milli);
            } catch (IllegalArgumentException e) {
                throw new ContentException("The streak is not consistent: " + e.getMessage());
            }
        }
        Map<String, Integer> stars = new TreeMap<>();
        if (top.get("stars") instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                stars.put(String.valueOf(e.getKey()), (int) whole(e.getValue(), "stars"));
            }
        }
        try {
            return new PlayerProgress(xp, gems, streak, stars);
        } catch (IllegalArgumentException e) {
            throw new ContentException(e.getMessage());
        }
    }

    private static long whole(Object value, String what) throws ContentException {
        if (!(value instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            throw new ContentException("\"" + what + "\" must be a whole number.");
        }
        return n.longValue();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerProgress p && xp == p.xp && gems == p.gems && streak.equals(p.streak)
                && puzzleStars.equals(p.puzzleStars);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(xp, gems, streak, puzzleStars);
    }

    @Override
    public String toString() {
        return "PlayerProgress{xp=" + xp + ", gems=" + gems + ", streak=" + streak.current() + ", stars=" + totalPuzzleStars() + "}";
    }
}

package com.graphlab.engine.arena;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the player has achieved: the best stars and points for every activity, and the same for mixed rounds. Plain Java; the
 * app stores {@link #encode()} in its preferences.
 *
 * <p>Since MS20 an activity is played as one three-step round (Easy, Medium, Hard), so its best result is kept once, under
 * {@link #ALL_LEVELS}, with {@link #recordActivity} and {@link #recordMixedRound}. The per-level methods below belong to the
 * older design (one level at a time with star locks) and are kept so that progress saved by earlier builds still loads and
 * still counts as a clear.
 *
 * <p>Old rules, per level: Easy is always open. Medium opens when the same activity has at least one star at Easy,
 * and Hard when it has at least two stars at Medium. The mixed round opens Medium after 3 stars and Hard
 * after 6 stars earned at the level before, counted over all activities.
 */
public final class ArenaProgress {
    /** The key of mixed rounds in saved progress. */
    public static final String MIXED = "mixed";
    /** The saved level tag of the one-round-per-activity results (MS20). */
    public static final String ALL_LEVELS = "ALL";

    private static final int MEDIUM_NEEDS = 1;
    private static final int HARD_NEEDS = 2;
    private static final int MIXED_MEDIUM_NEEDS = 3;
    private static final int MIXED_HARD_NEEDS = 6;

    /** The best result for one activity at one level. */
    public record Best(int stars, int points, int plays) {
    }

    /** What recording a finished round changed. */
    public record Outcome(int stars, int points, boolean newBest, boolean unlockedNextLevel) {
    }

    private final Map<String, Best> best = new LinkedHashMap<>();

    public ArenaProgress() {
    }

    // ---- reading ---------------------------------------------------------------------

    public Best best(QuestionKind kind, Level level) {
        return best(kind.id(), level);
    }

    public Best bestMixed(Level level) {
        return best(MIXED, level);
    }

    private Best best(String id, Level level) {
        return best.getOrDefault(key(id, level), new Best(0, 0, 0));
    }

    /** The best three-step round of an activity (MS20); nothing played gives 0 stars, 0 points, 0 plays. */
    public Best bestActivity(QuestionKind kind) {
        return best.getOrDefault(kind.id() + ":" + ALL_LEVELS, new Best(0, 0, 0));
    }

    /** The best mixed round (MS20). */
    public Best bestMixedRound() {
        return best.getOrDefault(MIXED + ":" + ALL_LEVELS, new Best(0, 0, 0));
    }

    /** The most stars an activity has earned: from a three-step round, or from any level of an earlier build. */
    public int starsOf(QuestionKind kind) {
        int most = bestActivity(kind).stars();
        for (Level level : Level.values()) {
            most = Math.max(most, stars(kind, level));
        }
        return most;
    }

    public int stars(QuestionKind kind, Level level) {
        return best(kind, level).stars();
    }

    /** Stars earned over all activities (not the mixed round) at one level. */
    public int starsAt(Level level) {
        int total = 0;
        for (QuestionKind kind : QuestionKind.values()) {
            total += stars(kind, level);
        }
        return total;
    }

    public int totalStars() {
        int total = 0;
        for (Map.Entry<String, Best> entry : best.entrySet()) {
            total += entry.getValue().stars();
        }
        return total;
    }

    /** The sum of the best points of every activity and level. */
    public int totalPoints() {
        int total = 0;
        for (Best b : best.values()) {
            total += b.points();
        }
        return total;
    }

    /** How many rounds have been finished. */
    public int totalPlays() {
        int total = 0;
        for (Best b : best.values()) {
            total += b.plays();
        }
        return total;
    }

    // ---- unlocking -------------------------------------------------------------------

    public boolean isUnlocked(QuestionKind kind, Level level) {
        Level before = level.previous();
        if (before == null) {
            return true;
        }
        return stars(kind, before) >= (level == Level.MEDIUM ? MEDIUM_NEEDS : HARD_NEEDS);
    }

    public boolean isMixedUnlocked(Level level) {
        Level before = level.previous();
        if (before == null) {
            return true;
        }
        return starsAt(before) >= (level == Level.MEDIUM ? MIXED_MEDIUM_NEEDS : MIXED_HARD_NEEDS);
    }

    /** Stars needed on the level before to open {@code level} for one activity; 0 for Easy. */
    public static int starsToUnlock(Level level) {
        return level == Level.EASY ? 0 : level == Level.MEDIUM ? MEDIUM_NEEDS : HARD_NEEDS;
    }

    /** Stars needed on the level before, over all activities, to open {@code level} for mixed rounds; 0 for Easy. */
    public static int mixedStarsToUnlock(Level level) {
        return level == Level.EASY ? 0 : level == Level.MEDIUM ? MIXED_MEDIUM_NEEDS : MIXED_HARD_NEEDS;
    }

    // ---- writing ---------------------------------------------------------------------

    /** Records a finished round of one activity. */
    public Outcome record(QuestionKind kind, Level level, int correct, int total) {
        return record(kind.id(), level, correct, total);
    }

    /** Records a finished mixed round. */
    public Outcome recordMixed(Level level, int correct, int total) {
        return record(MIXED, level, correct, total);
    }

    private Outcome record(String id, Level level, int correct, int total) {
        QuestionKind kind = QuestionKind.findById(id);
        Level next = level.next();
        boolean wasOpen = next == null || (kind != null ? isUnlocked(kind, next) : isMixedUnlocked(next));
        int stars = ArenaScoring.stars(correct, total);
        int points = ArenaScoring.points(correct, level);
        Best old = best(id, level);
        Best updated = new Best(Math.max(old.stars(), stars), Math.max(old.points(), points), old.plays() + 1);
        best.put(key(id, level), updated);
        boolean nowOpen = next == null || (kind != null ? isUnlocked(kind, next) : isMixedUnlocked(next));
        return new Outcome(stars, points, stars > old.stars() || points > old.points(), !wasOpen && nowOpen);
    }

    /** Records a finished three-step round of one activity (MS20). */
    public Outcome recordActivity(QuestionKind kind, int stars, int points) {
        return recordAll(kind.id(), stars, points);
    }

    /** Records a finished mixed round (MS20). */
    public Outcome recordMixedRound(int stars, int points) {
        return recordAll(MIXED, stars, points);
    }

    private Outcome recordAll(String id, int stars, int points) {
        if (stars < 0 || stars > ArenaScoring.MAX_STARS || points < 0) {
            throw new IllegalArgumentException("stars " + stars + ", points " + points + " is not a result");
        }
        String key = id + ":" + ALL_LEVELS;
        Best old = best.getOrDefault(key, new Best(0, 0, 0));
        best.put(key, new Best(Math.max(old.stars(), stars), Math.max(old.points(), points), old.plays() + 1));
        return new Outcome(stars, points, stars > old.stars() || points > old.points(), false);
    }

    public void clear() {
        best.clear();
    }

    // ---- saving ----------------------------------------------------------------------

    /** One line per entry: {@code id:LEVEL=stars,points,plays}, separated by semicolons. */
    public String encode() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Best> entry : best.entrySet()) {
            Best b = entry.getValue();
            out.append(out.length() == 0 ? "" : ";").append(entry.getKey()).append('=')
                    .append(b.stars()).append(',').append(b.points()).append(',').append(b.plays());
        }
        return out.toString();
    }

    /** Reads {@link #encode()} output. Anything that does not parse is skipped, so a damaged value never crashes the app. */
    public static ArenaProgress decode(String text) {
        ArenaProgress progress = new ArenaProgress();
        if (text == null || text.isBlank()) {
            return progress;
        }
        for (String entry : text.split(";")) {
            int eq = entry.indexOf('=');
            int colon = entry.indexOf(':');
            if (eq < 0 || colon < 0 || colon > eq) {
                continue;
            }
            String id = entry.substring(0, colon);
            String tag = entry.substring(colon + 1, eq);
            Level level = null;
            if (!tag.equals(ALL_LEVELS)) {
                try {
                    level = Level.valueOf(tag);
                } catch (IllegalArgumentException unknownLevel) {
                    continue;
                }
            }
            if (!id.equals(MIXED) && QuestionKind.findById(id) == null) {
                continue;
            }
            String[] numbers = entry.substring(eq + 1).split(",");
            if (numbers.length != 3) {
                continue;
            }
            try {
                int stars = Math.max(0, Math.min(ArenaScoring.MAX_STARS, Integer.parseInt(numbers[0].trim())));
                int points = Math.max(0, Integer.parseInt(numbers[1].trim()));
                int plays = Math.max(0, Integer.parseInt(numbers[2].trim()));
                progress.best.put(level == null ? id + ":" + ALL_LEVELS : key(id, level), new Best(stars, points, plays));
            } catch (NumberFormatException notANumber) {
                // skip this entry
            }
        }
        return progress;
    }

    private static String key(String id, Level level) {
        return id + ":" + level.name();
    }
}

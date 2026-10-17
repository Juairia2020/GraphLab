package com.graphlab.engine.arena;

/** How a round is scored. Plain Java, so the rules are tested without a phone. */
public final class ArenaScoring {
    /** Points for one right answer at {@link Level#EASY}; Medium is worth twice as much, Hard three times. */
    public static final int BASE_POINTS = 10;
    public static final int MAX_STARS = 3;

    private ArenaScoring() {
    }

    /** Points for {@code correct} right answers at a level. */
    public static int points(int correct, Level level) {
        return correct * BASE_POINTS * level.multiplier();
    }

    /**
     * Stars for a finished round: one for at least 60 % right, two for at least 80 %, three for all of
     * them. With five questions that is 3, 4 and 5 right.
     */
    public static int stars(int correct, int total) {
        if (total <= 0 || correct < 0 || correct > total) {
            throw new IllegalArgumentException("correct " + correct + " of " + total + " is not a score");
        }
        if (correct == total) {
            return 3;
        }
        if (total <= 2 && correct >= 1) {
            return 1;   // a two-question round: one right of two is a star (otherwise it would be 0 or 3)
        }
        if (correct * 5 >= total * 4) {
            return 2;
        }
        if (correct * 5 >= total * 3) {
            return 1;
        }
        return 0;
    }

    /**
     * Stars for a round whose questions are worth different points (the three-step ladder of one activity, and mixed or
     * planned rounds that climb from easy to hard). One star for any points, two for at least half of the points on offer,
     * three only for every point.
     *
     * @param earned points won
     * @param max    points on offer (all questions right)
     */
    public static int starsByPoints(int earned, int max) {
        if (max <= 0 || earned < 0 || earned > max) {
            throw new IllegalArgumentException("points " + earned + " of " + max + " is not a score");
        }
        if (earned == max) {
            return 3;
        }
        if (earned * 2 >= max) {
            return 2;
        }
        return earned > 0 ? 1 : 0;
    }

    /** The difficulty of question {@code index} (from 0) in a round of {@code size} questions that climbs from Easy to Hard. */
    public static Level climb(int index, int size) {
        if (size <= 0 || index < 0 || index >= size) {
            throw new IllegalArgumentException("question " + index + " of " + size);
        }
        Level[] levels = Level.values();
        return levels[Math.min(levels.length - 1, index * levels.length / size)];
    }
}

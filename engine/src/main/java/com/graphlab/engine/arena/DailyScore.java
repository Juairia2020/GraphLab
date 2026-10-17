package com.graphlab.engine.arena;

/**
 * The score of a daily puzzle: 100 for every right answer, plus a speed bonus of one point for every second under
 * {@value #FAST_SECONDS} that the answer took (0 to {@value #FAST_SECONDS}). A wrong answer scores 0. The most is
 * {@value #MAX_SCORE}.
 */
public final class DailyScore {
    public static final int RIGHT = 100;
    public static final int FAST_SECONDS = 20;
    public static final int MAX_SCORE = DailyPuzzle.QUESTIONS * (RIGHT + FAST_SECONDS);

    private DailyScore() {
    }

    /** The score of one answer; {@code seconds} below 0 count as 0. */
    public static int answer(boolean correct, long seconds) {
        if (!correct) {
            return 0;
        }
        return RIGHT + (int) Math.max(0, FAST_SECONDS - Math.max(0, seconds));
    }
}

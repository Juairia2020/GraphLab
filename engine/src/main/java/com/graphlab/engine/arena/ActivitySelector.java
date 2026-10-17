package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The basic Surprise Engine (MS6, M10): picks the next activity of a mixed round. Each activity gets a score, its need plus a
 * little noise, and is drawn with probability proportional to exp(score / T). A small T follows the needs closely, a large T
 * is closer to random. The activity that was just played is never offered again, so a round never repeats one in a row.
 */
public final class ActivitySelector {
    /** The temperature T. At 0.5 an activity with a need 0.5 higher is about 2.7 times as likely. */
    public static final double TEMPERATURE = 0.5;
    /** The noise added to every score is a number from 0 up to this. */
    public static final double NOISE = 0.2;

    private ActivitySelector() {
    }

    /**
     * @param kinds       the activities to choose from (two or more)
     * @param previous    the activity played last, or null
     * @param temperature greater than 0
     */
    public static QuestionKind next(List<QuestionKind> kinds, NeedModel needs, QuestionKind previous, Random random,
            double temperature) {
        return next(kinds, needs::need, previous, random, temperature);
    }

    /**
     * The same draw with any base score per activity (MS12: {@link SurpriseEngine} adds mastery, novelty and due topics to the
     * need). The noise, the temperature and the no-repeat rule are unchanged.
     */
    public static QuestionKind next(List<QuestionKind> kinds, java.util.function.ToDoubleFunction<QuestionKind> base,
            QuestionKind previous, Random random, double temperature) {
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to choose from");
        }
        if (!(temperature > 0)) {
            throw new IllegalArgumentException("The temperature must be greater than 0");
        }
        List<QuestionKind> pool = new ArrayList<>(kinds);
        if (pool.size() > 1) {
            pool.remove(previous);
        }
        double[] weight = new double[pool.size()];
        double max = Double.NEGATIVE_INFINITY;
        double[] score = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            score[i] = base.applyAsDouble(pool.get(i)) + NOISE * random.nextDouble();
            max = Math.max(max, score[i]);
        }
        double total = 0;
        for (int i = 0; i < weight.length; i++) {
            weight[i] = Math.exp((score[i] - max) / temperature);   // minus the maximum keeps exp() from overflowing
            total += weight[i];
        }
        double pick = random.nextDouble() * total;
        for (int i = 0; i < weight.length; i++) {
            pick -= weight[i];
            if (pick <= 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }
}

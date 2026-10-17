package com.graphlab.engine.arena;

import com.graphlab.engine.progress.Mastery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The Surprise Engine, version 2 (MS12). It scores every activity and the selector draws one with probability proportional to
 * {@code exp(score / T)}. The score of an activity is
 *
 * <pre>
 *   score = wNeed * need + wWeak * (1 - mastery) + wNovel * novelty + wDue * due
 * </pre>
 *
 * where {@code need} is the share of wrong answers (from {@link NeedModel}, scaled to 0..1), {@code mastery} is the decayed
 * mastery of the activity's topic ({@link Mastery}), {@code novelty} is {@code 1 / (1 + attempts)} and {@code due} is 1 when
 * the topic was practised but has been forgotten. The four weights are starting guesses ({@link Weights#DEFAULT}: 0.4, 0.25,
 * 0.25, 0.5); the debug screen shows every part of every score so they can be tuned. Plain Java, no Android.
 */
public final class SurpriseEngine {
    /** The four weights of the score. */
    public record Weights(double need, double weakness, double novelty, double due) {
        public static final Weights DEFAULT = new Weights(0.4, 0.25, 0.25, 0.5);

        public Weights {
            if (need < 0 || weakness < 0 || novelty < 0 || due < 0 || need > 5 || weakness > 5 || novelty > 5 || due > 5) {
                throw new IllegalArgumentException("Weights must be between 0 and 5");
            }
        }

        /** {@code need,weakness,novelty,due} as text for saving. */
        public String encode() {
            return need + "," + weakness + "," + novelty + "," + due;
        }

        /** Reads {@link #encode()} text; anything damaged gives {@link #DEFAULT}. */
        public static Weights parse(String text) {
            try {
                String[] p = text.split(",");
                return new Weights(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            } catch (RuntimeException e) {
                return DEFAULT;
            }
        }
    }

    /** One activity's score and its parts, for the debug screen. {@code probability} ignores the noise (its average). */
    public record Row(QuestionKind kind, double need, double mastery, double novelty, double due, double score,
                      double probability, int seconds) {
    }

    private final NeedModel needs;
    private final Mastery mastery;
    private final Weights weights;
    private final Instant now;

    public SurpriseEngine(NeedModel needs, Mastery mastery, Weights weights, Instant now) {
        this.needs = needs;
        this.mastery = mastery;
        this.weights = weights;
        this.now = now;
    }

    /** An engine with no history: it behaves like the basic one, with equal parts everywhere. */
    public static SurpriseEngine neutral(Instant now) {
        return new SurpriseEngine(NeedModel.neutral(), new Mastery(), Weights.DEFAULT, now);
    }

    public Weights weights() {
        return weights;
    }

    public Mastery mastery() {
        return mastery;
    }

    public Instant now() {
        return now;
    }

    /** The score without noise. */
    public double score(QuestionKind kind) {
        return row(kind, 1.0, 0.0).score();
    }

    private Row row(QuestionKind kind, double total, double max) {
        double need = Math.min(1.0, needs.need(kind) / 1.3);
        String topic = kind.topic();
        double m = Math.min(1.0, mastery.value(topic, now));
        double novelty = 1.0 / (1.0 + needs.attempts(kind));
        double due = mastery.isDue(topic, now) ? 1.0 : 0.0;
        double score = weights.need() * need + weights.weakness() * (1.0 - m) + weights.novelty() * novelty + weights.due() * due;
        return new Row(kind, need, m, novelty, due, score, 0.0, kind.seconds());
    }

    /**
     * Every activity with its parts, best score first, and the chance each has of being drawn next at temperature
     * {@code ActivitySelector.TEMPERATURE} (the activity just played is left out, as in a real draw).
     */
    public List<Row> explain(List<QuestionKind> kinds, QuestionKind previous) {
        List<Row> rows = new ArrayList<>();
        double max = Double.NEGATIVE_INFINITY;
        for (QuestionKind kind : kinds) {
            Row r = row(kind, 1.0, 0.0);
            rows.add(r);
            if (kind != previous) {
                max = Math.max(max, r.score());
            }
        }
        double total = 0;
        for (Row r : rows) {
            if (r.kind() != previous) {
                total += Math.exp((r.score() - max) / ActivitySelector.TEMPERATURE);
            }
        }
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            double p = r.kind() == previous ? 0.0 : Math.exp((r.score() - max) / ActivitySelector.TEMPERATURE) / total;
            out.add(new Row(r.kind(), r.need(), r.mastery(), r.novelty(), r.due(), r.score(), p, r.seconds()));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }
}

package com.graphlab.engine.arena;

import java.util.HashMap;
import java.util.Map;

/**
 * How much the player needs each activity, from their past answers (the "need" of the selector). A need is a number from 0
 * to about 1.3: the share of answers that were wrong, smoothed so that one lucky answer does not hide a weakness, plus a
 * small bonus for activities that were hardly tried. The activity's own record counts for 60 percent and the record of the
 * algorithm topic it is about for 40 percent. Plain Java, no Android.
 */
public final class NeedModel {
    private static final double NOVELTY = 0.3;

    private final Map<String, int[]> activity;
    private final Map<String, int[]> topic;

    /**
     * @param activity activity id to {attempts, correct}
     * @param topic    topic to {attempts, correct}
     */
    public NeedModel(Map<String, int[]> activity, Map<String, int[]> topic) {
        this.activity = copy(activity);
        this.topic = copy(topic);
    }

    /** A model with no history: every activity has the same need. */
    public static NeedModel neutral() {
        return new NeedModel(Map.of(), Map.of());
    }

    public double need(QuestionKind kind) {
        double byActivity = errorRate(activity.get(kind.id()));
        int[] topicStats = kind.fromCards() ? null : topic.get(kind.topic());
        double byTopic = topicStats == null ? byActivity : errorRate(topicStats);
        double attempts = activity.containsKey(kind.id()) ? activity.get(kind.id())[0] : 0;
        return 0.6 * byActivity + 0.4 * byTopic + NOVELTY / (1.0 + attempts);
    }

    /** How many times the player has answered this activity. */
    public int attempts(QuestionKind kind) {
        int[] stats = activity.get(kind.id());
        return stats == null ? 0 : stats[0];
    }

    /** The model as text, so it can travel in an intent: {@code a:kind=attempts/correct,t:topic=attempts/correct}. */
    public String encode() {
        StringBuilder out = new StringBuilder();
        append(out, "a:", new java.util.TreeMap<>(activity));
        append(out, "t:", new java.util.TreeMap<>(topic));
        return out.toString();
    }

    private static void append(StringBuilder out, String prefix, Map<String, int[]> map) {
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            out.append(out.length() == 0 ? "" : ",").append(prefix).append(e.getKey()).append('=')
                    .append(e.getValue()[0]).append('/').append(e.getValue()[1]);
        }
    }

    /** Reads {@link #encode()} text. Damaged parts are skipped; null or empty gives {@link #neutral()}. */
    public static NeedModel parse(String text) {
        Map<String, int[]> activity = new HashMap<>();
        Map<String, int[]> topic = new HashMap<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(",")) {
                try {
                    int eq = part.indexOf('=');
                    int slash = part.indexOf('/', eq);
                    if (part.length() < 3 || eq < 3 || slash < 0) {
                        continue;
                    }
                    int attempts = Integer.parseInt(part.substring(eq + 1, slash));
                    int correct = Integer.parseInt(part.substring(slash + 1));
                    if (attempts < 0 || correct < 0 || correct > attempts) {
                        continue;
                    }
                    String key = part.substring(2, eq);
                    (part.startsWith("a:") ? activity : topic).put(key, new int[] {attempts, correct});
                } catch (NumberFormatException skipped) {
                    // a damaged entry is ignored
                }
            }
        }
        return new NeedModel(activity, topic);
    }

    /** Wrong share with a prior of one right and one wrong answer: 0.5 with no history. */
    static double errorRate(int[] stats) {
        int attempts = stats == null ? 0 : stats[0];
        int correct = stats == null ? 0 : stats[1];
        return 1.0 - (correct + 1.0) / (attempts + 2.0);
    }

    private static Map<String, int[]> copy(Map<String, int[]> source) {
        Map<String, int[]> out = new HashMap<>();
        for (Map.Entry<String, int[]> e : source.entrySet()) {
            int[] v = e.getValue();
            if (v.length != 2 || v[0] < 0 || v[1] < 0 || v[1] > v[0]) {
                throw new IllegalArgumentException("Stats for " + e.getKey() + " must be {attempts, correct} with correct <= attempts");
            }
            out.put(e.getKey(), v.clone());
        }
        return out;
    }
}

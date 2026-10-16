package com.graphlab.engine.progress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How well the player knows each topic (an algorithm id, or "general"), with forgetting (MS12). Every answer moves the
 * stored value towards 1 (right) or 0 (wrong); between answers it decays as {@code mastery = mastery * exp(-days / tau)}.
 * Time is counted in exact days from instants, so a change of time zone does not matter. Plain Java.
 */
public final class Mastery {
    /** The forgetting time in days: after tau days, a topic is at about 37 percent of what it was. A starting guess to tune. */
    public static final double TAU_DAYS = 14.0;
    /** How far one answer moves the value. */
    public static final double RATE = 0.35;
    /** A practised topic below this is due for another go. */
    public static final double DUE_BELOW = 0.6;

    private static final double MILLIS_PER_DAY = 86_400_000.0;

    private record Entry(double value, long atMilli) {
    }

    private final double tau;
    private final Map<String, Entry> entries = new TreeMap<>();

    public Mastery() {
        this(TAU_DAYS);
    }

    public Mastery(double tauDays) {
        if (!(tauDays > 0)) {
            throw new IllegalArgumentException("tau must be greater than 0");
        }
        this.tau = tauDays;
    }

    /** The decayed value at {@code now}: 0 for a topic never practised. Never negative, never above the stored value. */
    public double value(String topic, Instant now) {
        Entry e = entries.get(topic);
        if (e == null) {
            return 0.0;
        }
        double days = Math.max(0.0, (now.toEpochMilli() - e.atMilli) / MILLIS_PER_DAY);
        return e.value * Math.exp(-days / tau);
    }

    /** Records one answer: decay to {@code now}, then move towards 1 or 0. */
    public void record(String topic, boolean correct, Instant now) {
        double current = value(topic, now);
        double next = current + RATE * ((correct ? 1.0 : 0.0) - current);
        entries.put(topic, new Entry(next, Math.max(now.toEpochMilli(), entries.containsKey(topic) ? entries.get(topic).atMilli : 0L)));
    }

    public boolean practised(String topic) {
        return entries.containsKey(topic);
    }

    /** Whether the topic was practised and has fallen below {@link #DUE_BELOW}. */
    public boolean isDue(String topic, Instant now) {
        return entries.containsKey(topic) && value(topic, now) < DUE_BELOW;
    }

    /** The topics that are due, the weakest first (ties by name). */
    public List<String> dueTopics(Instant now) {
        List<String> due = new ArrayList<>();
        for (String topic : entries.keySet()) {
            if (isDue(topic, now)) {
                due.add(topic);
            }
        }
        due.sort((a, b) -> {
            int c = Double.compare(value(a, now), value(b, now));
            return c != 0 ? c : a.compareTo(b);
        });
        return Collections.unmodifiableList(due);
    }

    public List<String> topics() {
        return List.copyOf(entries.keySet());
    }

    /** Text for saving: {@code topic=value@millis,topic=value@millis}. */
    public String encode() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            out.append(out.length() == 0 ? "" : ",").append(e.getKey()).append('=')
                    .append(e.getValue().value).append('@').append(e.getValue().atMilli);
        }
        return out.toString();
    }

    /** Reads {@link #encode()} text; damaged parts are skipped, null or empty gives an empty record. */
    public static Mastery parse(String text) {
        Mastery m = new Mastery();
        if (text == null || text.isBlank()) {
            return m;
        }
        for (String part : text.split(",")) {
            try {
                int eq = part.indexOf('=');
                int at = part.indexOf('@', eq);
                if (eq < 1 || at < 0) {
                    continue;
                }
                double value = Double.parseDouble(part.substring(eq + 1, at));
                long millis = Long.parseLong(part.substring(at + 1));
                if (Double.isNaN(value) || value < 0 || value > 1 || millis < 0) {
                    continue;
                }
                m.entries.put(part.substring(0, eq), new Entry(value, millis));
            } catch (NumberFormatException skipped) {
                // a damaged entry is ignored
            }
        }
        return m;
    }

    /** Joins two copies (two devices): for each topic the one that was updated last wins. */
    public Mastery merge(Mastery other) {
        Mastery out = new Mastery(tau);
        out.entries.putAll(entries);
        for (Map.Entry<String, Entry> e : other.entries.entrySet()) {
            Entry mine = out.entries.get(e.getKey());
            if (mine == null || e.getValue().atMilli > mine.atMilli) {
                out.entries.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}

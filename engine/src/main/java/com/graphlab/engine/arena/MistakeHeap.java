package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * The Ghost round (MS17): the player meets again the activities where mistakes were made. Mistakes are kept in a heap
 * ordered by a weight that grows with the severity of the mistake (the level it was made at) and fades with its age, so
 * a recent hard mistake comes before an old easy one. The same mistakes, time and seed always give the same round.
 */
public final class MistakeHeap {
    /** A mistake loses half of its weight after this many days. */
    public static final double HALF_LIFE_DAYS = 7.0;
    private static final double DAY_MILLIS = 24 * 60 * 60 * 1000.0;

    /** One wrong answer: the activity, the level of the round and when it happened (milliseconds since 1970, UTC). */
    public record Mistake(QuestionKind kind, Level level, long atMillis) {
        public Mistake {
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(level, "level");
        }

        /** How serious it was: 1 for Easy, 2 for Medium, 3 for Hard. */
        public int severity() {
            return level.multiplier();
        }

        /** The weight now: the severity, halved for every {@link #HALF_LIFE_DAYS} days of age. A mistake from the future counts as new. */
        public double weight(long nowMillis) {
            double ageDays = Math.max(0, nowMillis - atMillis) / DAY_MILLIS;
            return severity() * Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
        }
    }

    /** An activity with its total weight, as kept in the heap. */
    public record Entry(QuestionKind kind, double weight, Level level, long latestMillis) {
    }

    private MistakeHeap() {
    }

    /** The entries, one per activity, in the order the heap gives them: heaviest first. */
    public static List<Entry> ranked(List<Mistake> mistakes, long nowMillis, Set<QuestionKind> allowed) {
        Map<QuestionKind, double[]> total = new HashMap<>();   // weight, best single weight, latest time
        Map<QuestionKind, Level> level = new HashMap<>();
        for (Mistake m : mistakes) {
            if (allowed != null && !allowed.contains(m.kind())) {
                continue;
            }
            double w = m.weight(nowMillis);
            double[] t = total.computeIfAbsent(m.kind(), k -> new double[] {0, -1, Long.MIN_VALUE});
            t[0] += w;
            if (w > t[1] || (w == t[1] && m.atMillis() > (long) t[2])) {
                t[1] = w;
                level.put(m.kind(), m.level());
            }
            t[2] = Math.max(t[2], m.atMillis());
        }
        PriorityQueue<Entry> heap = new PriorityQueue<>((a, b) -> {
            int byWeight = Double.compare(b.weight(), a.weight());
            if (byWeight != 0) {
                return byWeight;
            }
            int byTime = Long.compare(b.latestMillis(), a.latestMillis());
            return byTime != 0 ? byTime : a.kind().id().compareTo(b.kind().id());
        });
        for (Map.Entry<QuestionKind, double[]> e : total.entrySet()) {
            heap.add(new Entry(e.getKey(), e.getValue()[0], level.get(e.getKey()), (long) e.getValue()[2]));
        }
        List<Entry> out = new ArrayList<>();
        while (!heap.isEmpty()) {
            out.add(heap.poll());
        }
        return out;
    }

    /**
     * The specs of a Ghost round: up to {@link ArenaRound#QUESTIONS} different activities, heaviest first, each at the level where
     * it hurt most. Every question has a fresh seed taken from {@code seed}. Empty when there are no mistakes to replay.
     */
    public static List<QuestionSpec> ghost(List<Mistake> mistakes, long nowMillis, long seed, Set<QuestionKind> allowed) {
        Random random = new Random(seed);
        List<QuestionSpec> specs = new ArrayList<>();
        for (Entry e : ranked(mistakes, nowMillis, allowed)) {
            if (specs.size() == ArenaRound.QUESTIONS) {
                break;
            }
            specs.add(new QuestionSpec(e.kind(), e.level(), random.nextLong()));
        }
        return specs;
    }

    /** The kinds of a list of specs (helper for screens and tests). */
    public static Set<QuestionKind> kinds(List<QuestionSpec> specs) {
        Set<QuestionKind> out = new HashSet<>();
        for (QuestionSpec s : specs) {
            out.add(s.kind());
        }
        return out;
    }
}

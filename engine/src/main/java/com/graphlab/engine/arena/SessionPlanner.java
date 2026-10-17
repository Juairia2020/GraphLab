package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans a practice session for a time budget (MS12, Surprise Engine v2). Three classic algorithms do the work:
 *
 * <ol>
 *   <li><b>0/1 knapsack</b> picks the questions: each candidate costs {@link QuestionKind#seconds()} and is worth its
 *       {@link SurpriseEngine} score, and the knapsack keeps the most value that fits in the budget. Every activity is a
 *       candidate twice; the second copy is worth 60 percent of the first.</li>
 *   <li><b>Bipartite matching</b> covers the topics that are due: each due topic is matched to a different candidate that
 *       is about it. The matched questions are placed <em>last</em> in the session. When time is short (the budget is less
 *       than twice the time of that cover) the cover is dropped first and the whole budget goes to the knapsack.</li>
 *   <li><b>Backtracking</b> puts the questions in order so that no two neighbours are the same activity. If no such order
 *       exists (for example a session of one activity) the least valuable question of the most repeated activity is removed
 *       and the search tries again.</li>
 * </ol>
 *
 * The plan never needs more time than the budget: every step only chooses from, or removes from, a selection that fits.
 * Plain Java, no Android.
 */
public final class SessionPlanner {
    /** How many times one activity may appear in a session. */
    public static final int COPIES = 2;
    /** The value of the second copy of an activity, compared with the first. */
    public static final double SECOND_COPY = 0.6;

    /** One candidate question slot. */
    public record Entry(QuestionKind kind, int copy, int seconds, double value) {
    }

    /**
     * The result. {@code entries} are in play order; the last {@code coverCount} of them are the cover of the due topics.
     */
    public record Plan(List<Entry> entries, int coverCount, List<String> coveredTopics, boolean coverDropped, int budgetSeconds) {
        public Plan {
            entries = List.copyOf(entries);
            coveredTopics = List.copyOf(coveredTopics);
        }

        public int seconds() {
            int total = 0;
            for (Entry e : entries) {
                total += e.seconds();
            }
            return total;
        }

        public List<QuestionKind> kinds() {
            List<QuestionKind> out = new ArrayList<>();
            for (Entry e : entries) {
                out.add(e.kind());
            }
            return out;
        }
    }

    private SessionPlanner() {
    }

    public static Plan plan(SurpriseEngine engine, int budgetSeconds) {
        if (budgetSeconds < 0) {
            throw new IllegalArgumentException("The budget cannot be negative");
        }
        List<Entry> all = new ArrayList<>();
        for (QuestionKind kind : QuestionKind.values()) {
            double score = Math.max(0.01, engine.score(kind));
            for (int copy = 0; copy < COPIES; copy++) {
                all.add(new Entry(kind, copy, kind.seconds(), score * (copy == 0 ? 1.0 : SECOND_COPY)));
            }
        }

        List<String> due = engine.mastery().dueTopics(engine.now());
        List<Entry> cover = cover(all, due);
        int coverSeconds = 0;
        for (Entry e : cover) {
            coverSeconds += e.seconds();
        }
        boolean dropped = false;
        List<Entry> core;
        List<Entry> tail;
        if (cover.isEmpty()) {
            core = knapsack(all, budgetSeconds);
            tail = List.of();
        } else if (budgetSeconds < 2 * coverSeconds) {
            dropped = true;                       // time is short: the cover goes first
            core = knapsack(all, budgetSeconds);
            tail = List.of();
        } else {
            List<Entry> rest = new ArrayList<>(all);
            rest.removeAll(cover);
            core = knapsack(rest, budgetSeconds - coverSeconds);
            tail = cover;
        }

        List<Entry> coreLeft = new ArrayList<>(core);
        List<Entry> tailLeft = new ArrayList<>(tail);
        while (true) {
            List<Entry> order = arrange(coreLeft, tailLeft);
            if (order != null) {
                Set<String> covered = new HashSet<>();
                for (Entry e : tailLeft) {
                    covered.add(e.kind().topic());
                }
                List<String> coveredTopics = new ArrayList<>();
                for (String topic : due) {
                    if (covered.contains(topic)) {
                        coveredTopics.add(topic);
                    }
                }
                return new Plan(order, tailLeft.size(), coveredTopics, dropped, budgetSeconds);
            }
            // no order without neighbours of the same activity: drop the least valuable of the most repeated activity
            List<Entry> from = !coreLeft.isEmpty() ? coreLeft : tailLeft;
            from.remove(leastValuableOfMostRepeated(from));
        }
    }

    // ---- bipartite matching: due topics to candidate questions -------------------------

    static List<Entry> cover(List<Entry> all, List<String> dueTopics) {
        int n = all.size();
        int[] matchOfEntry = new int[n];   // which topic index is matched to this entry
        java.util.Arrays.fill(matchOfEntry, -1);
        for (int t = 0; t < dueTopics.size(); t++) {
            augment(t, dueTopics, all, matchOfEntry, new boolean[n]);
        }
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (matchOfEntry[i] >= 0) {
                out.add(all.get(i));
            }
        }
        return out;
    }

    /** Kuhn's algorithm: tries to give topic {@code t} an entry, moving earlier matches when that helps. */
    private static boolean augment(int t, List<String> topics, List<Entry> all, int[] matchOfEntry, boolean[] seen) {
        // cheaper, more valuable entries are tried first, so the cover is as cheap as it can be
        List<Integer> options = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).kind().topic().equals(topics.get(t))) {
                options.add(i);
            }
        }
        options.sort(Comparator.comparingInt((Integer i) -> all.get(i).seconds()).thenComparingInt(i -> i));
        for (int i : options) {
            if (seen[i]) {
                continue;
            }
            seen[i] = true;
            if (matchOfEntry[i] < 0 || augment(matchOfEntry[i], topics, all, matchOfEntry, seen)) {
                matchOfEntry[i] = t;
                return true;
            }
        }
        return false;
    }

    // ---- 0/1 knapsack ----------------------------------------------------------------

    static List<Entry> knapsack(List<Entry> items, int budget) {
        int n = items.size();
        if (budget <= 0 || n == 0) {
            return List.of();
        }
        long[][] best = new long[n + 1][budget + 1];
        for (int i = 1; i <= n; i++) {
            Entry e = items.get(i - 1);
            long value = Math.round(e.value() * 1000.0);
            for (int w = 0; w <= budget; w++) {
                best[i][w] = best[i - 1][w];
                if (e.seconds() <= w) {
                    best[i][w] = Math.max(best[i][w], best[i - 1][w - e.seconds()] + value);
                }
            }
        }
        List<Entry> chosen = new ArrayList<>();
        int w = budget;
        for (int i = n; i >= 1; i--) {
            if (best[i][w] != best[i - 1][w]) {
                chosen.add(0, items.get(i - 1));
                w -= items.get(i - 1).seconds();
            }
        }
        return chosen;
    }

    // ---- backtracking: no two neighbours of the same activity ---------------------------

    /** The order of core then tail with no equal neighbours, best questions first inside each part; null if none exists. */
    private static List<Entry> arrange(List<Entry> core, List<Entry> tail) {
        List<Entry> coreSorted = byValue(core);
        List<Entry> tailSorted = byValue(tail);
        List<Entry> out = new ArrayList<>();
        boolean[] coreUsed = new boolean[coreSorted.size()];
        boolean[] tailUsed = new boolean[tailSorted.size()];
        return place(coreSorted, coreUsed, tailSorted, tailUsed, out) ? out : null;
    }

    private static boolean place(List<Entry> core, boolean[] coreUsed, List<Entry> tail, boolean[] tailUsed, List<Entry> out) {
        boolean inCore = out.size() < core.size();
        List<Entry> pool = inCore ? core : tail;
        boolean[] used = inCore ? coreUsed : tailUsed;
        if (out.size() == core.size() + tail.size()) {
            return true;
        }
        QuestionKind previous = out.isEmpty() ? null : out.get(out.size() - 1).kind();
        Set<QuestionKind> tried = new HashSet<>();
        for (int i = 0; i < pool.size(); i++) {
            Entry e = pool.get(i);
            if (used[i] || e.kind() == previous || !tried.add(e.kind())) {
                continue;
            }
            used[i] = true;
            out.add(e);
            if (place(core, coreUsed, tail, tailUsed, out)) {
                return true;
            }
            out.remove(out.size() - 1);
            used[i] = false;
        }
        return false;
    }

    private static List<Entry> byValue(List<Entry> items) {
        List<Entry> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingDouble(Entry::value).reversed().thenComparing(e -> e.kind().id()).thenComparingInt(Entry::copy));
        return sorted;
    }

    private static Entry leastValuableOfMostRepeated(List<Entry> items) {
        java.util.Map<QuestionKind, Integer> counts = new java.util.HashMap<>();
        int most = 0;
        for (Entry e : items) {
            most = Math.max(most, counts.merge(e.kind(), 1, Integer::sum));
        }
        Entry worst = null;
        for (Entry e : items) {
            if (counts.get(e.kind()) == most && (worst == null || e.value() < worst.value())) {
                worst = e;
            }
        }
        return worst;
    }
}

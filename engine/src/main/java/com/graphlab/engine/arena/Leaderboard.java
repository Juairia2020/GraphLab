package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ranking of a daily puzzle (MS17). One entry for each player (the best one counts); higher score first, then less time,
 * then the earlier submission, then the player id, so the order is the same on every device. The app reads the entries from
 * Firestore and ranks them here. Plain Java.
 */
public final class Leaderboard {
    /** One player's result for the day. */
    public record Entry(String uid, String name, int score, int seconds, long submittedMillis) {
        public Entry {
            java.util.Objects.requireNonNull(uid, "uid");
            java.util.Objects.requireNonNull(name, "name");
        }
    }

    private Leaderboard() {
    }

    private static final Comparator<Entry> BETTER_FIRST = Comparator.comparingInt(Entry::score).reversed()
            .thenComparingInt(Entry::seconds)
            .thenComparingLong(Entry::submittedMillis)
            .thenComparing(Entry::uid);

    /** The best {@code limit} players, best first. */
    public static List<Entry> top(List<Entry> entries, int limit) {
        Map<String, Entry> best = new HashMap<>();
        for (Entry e : entries) {
            Entry old = best.get(e.uid());
            if (old == null || BETTER_FIRST.compare(e, old) < 0) {
                best.put(e.uid(), e);
            }
        }
        List<Entry> all = new ArrayList<>(best.values());
        all.sort(BETTER_FIRST);
        return all.subList(0, Math.max(0, Math.min(limit, all.size())));
    }

    /** The place of a player (1 is best), or 0 when the player has no entry. Equal in every way except the id still get different places. */
    public static int rankOf(List<Entry> entries, String uid) {
        List<Entry> all = top(entries, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uid().equals(uid)) {
                return i + 1;
            }
        }
        return 0;
    }
}

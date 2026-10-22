package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;

public class UnlockGraphTest {
    private static final UnlockGraph G = UnlockGraph.bundled();

    private static Predicate<QuestionKind> in(Set<QuestionKind> done) {
        return done::contains;
    }

    @Test
    public void orderPutsEveryActivityAfterItsRequirements() {
        List<QuestionKind> order = G.order();
        assertEquals(QuestionKind.values().length, order.size());
        assertEquals(order.size(), new HashSet<>(order).size());
        for (QuestionKind k : order) {
            for (QuestionKind need : G.requirements(k)) {
                assertTrue(k + " before " + need, order.indexOf(need) < order.indexOf(k));
            }
        }
    }

    @Test
    public void everyActivityCanEventuallyBePlayed() {
        // Brute force: keep clearing whatever is open until nothing changes.
        Set<QuestionKind> cleared = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (QuestionKind k : QuestionKind.values()) {
                if (!cleared.contains(k) && G.isUnlocked(k, in(cleared))) {
                    cleared.add(k);
                    changed = true;
                }
            }
        }
        assertEquals(QuestionKind.values().length, cleared.size());
    }

    @Test
    public void startingActivitiesAreOpenAndTheDemoUnlockWorks() {
        Set<QuestionKind> none = new HashSet<>();
        List<QuestionKind> open = G.unlocked(in(none));
        assertEquals("a new player (or a reset) starts with one activity", List.of(QuestionKind.BFS_ORDER), open);
        assertFalse(open.contains(QuestionKind.DIJKSTRA_DISTANCE));
        assertFalse(open.contains(QuestionKind.SPOT_BUG));
        assertTrue(G.unlockedBy(QuestionKind.BFS_ORDER, in(none)).containsAll(List.of(QuestionKind.DIJKSTRA_DISTANCE, QuestionKind.DFS_ORDER)));
        assertEquals(List.of(QuestionKind.BFS_ORDER), G.missing(QuestionKind.DIJKSTRA_DISTANCE, in(none)));
    }

    @Test
    public void anActivityWithTwoRequirementsNeedsBoth() {
        Set<QuestionKind> done = new HashSet<>(List.of(QuestionKind.DIJKSTRA_DISTANCE));
        assertFalse(G.isUnlocked(QuestionKind.WILL_CHANGE, in(done)));
        assertEquals(List.of(QuestionKind.KRUSKAL_WEIGHT), G.missing(QuestionKind.WILL_CHANGE, in(done)));
        done.add(QuestionKind.KRUSKAL_WEIGHT);
        assertTrue(G.isUnlocked(QuestionKind.WILL_CHANGE, in(done)));
        // Clearing the second requirement is what opens it.
        Set<QuestionKind> before = new HashSet<>(List.of(QuestionKind.DIJKSTRA_DISTANCE));
        assertTrue(G.unlockedBy(QuestionKind.KRUSKAL_WEIGHT, in(before)).contains(QuestionKind.WILL_CHANGE));
    }

    @Test
    public void unlockedByEqualsTheDifferenceOfTheOpenSets() {
        java.util.Random r = new java.util.Random(3);
        QuestionKind[] all = QuestionKind.values();
        for (int t = 0; t < 300; t++) {
            Set<QuestionKind> before = new HashSet<>();
            for (QuestionKind k : all) {
                if (r.nextInt(3) == 0) {
                    before.add(k);
                }
            }
            QuestionKind kind = all[r.nextInt(all.length)];
            Set<QuestionKind> after = new HashSet<>(before);
            after.add(kind);
            List<QuestionKind> diff = new ArrayList<>(G.unlocked(in(after)));
            diff.removeAll(G.unlocked(in(before)));
            assertEquals(diff, G.unlockedBy(kind, in(before)));
        }
    }

    @Test
    public void aCycleIsRefused() {
        Map<QuestionKind, List<QuestionKind>> m = new EnumMap<>(QuestionKind.class);
        m.put(QuestionKind.BFS_ORDER, List.of(QuestionKind.DFS_ORDER));
        m.put(QuestionKind.DFS_ORDER, List.of(QuestionKind.DFS_FINISH));
        m.put(QuestionKind.DFS_FINISH, List.of(QuestionKind.BFS_ORDER));
        try {
            UnlockGraph.of(m);
            fail("cycle accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("cycle"));
        }
        try {
            UnlockGraph.of(Map.of(QuestionKind.BFS_ORDER, List.of(QuestionKind.BFS_ORDER)));
            fail("self requirement accepted");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void badFilesAreRefused() {
        for (String bad : new String[] {"{", "[]", "{\"requires\":{\"nope\":[]}}", "{\"requires\":{\"bfs-order\":[\"nope\"]}}",
                "{\"requires\":{\"bfs-order\":\"dfs-order\"}}"}) {
            try {
                UnlockGraph.parse(bad);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void clearedMeansOneStarAtAnyLevel() {
        ArenaProgress p = new ArenaProgress();
        assertFalse(UnlockGraph.clearedIn(p).test(QuestionKind.BFS_ORDER));
        p.record(QuestionKind.BFS_ORDER, Level.EASY, 5, 5);
        assertTrue(UnlockGraph.clearedIn(p).test(QuestionKind.BFS_ORDER));
        assertTrue(G.isUnlocked(QuestionKind.DIJKSTRA_DISTANCE, UnlockGraph.clearedIn(p)));
    }
}

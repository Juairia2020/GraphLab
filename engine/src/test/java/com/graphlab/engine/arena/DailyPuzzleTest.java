package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

public class DailyPuzzleTest {
    /** Computed outside Java (Python hashlib), so the seed does not just agree with itself. */
    @Test
    public void seedsMatchAnIndependentSha256() {
        assertEquals(7054158867304282813L, DailyPuzzle.seed("2026-10-15"));
        assertEquals(5860620089725781777L, DailyPuzzle.seed("2026-02-28"));
        assertEquals(7000459741761906685L, DailyPuzzle.seed("2024-02-29"));
    }

    private static String fingerprint(String date) {
        ArenaRound r = DailyPuzzle.round(date);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.size(); i++) {
            Question q = r.question(i);
            sb.append(q.kind().id()).append('|').append(q.prompt()).append('|').append(q.options()).append('|')
                    .append(q.correctIndex()).append('|').append(q.input()).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void everyDeviceBuildsTheSamePuzzle() {
        String a = fingerprint("2026-10-15");
        Locale old = Locale.getDefault();
        try {
            for (Locale l : new Locale[] {Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("bn-BD"),
                    Locale.forLanguageTag("ar-EG"), Locale.US}) {
                Locale.setDefault(l);
                assertEquals(l.toString(), a, fingerprint("2026-10-15"));
            }
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    public void goldenFingerprintDoesNotDrift() {
        // If a change to the question generators alters this number, every old daily puzzle changes too. Update it on purpose only.
        assertEquals(GOLDEN, fingerprint("2026-10-15").hashCode());
    }

    // Updated on purpose when Break the algorithm and Fog of war joined the Arena (Priority 4).
    private static final int GOLDEN = -138410794;

    @Test
    public void dayChangesAtUtcMidnight() {
        assertEquals("2026-10-14", DailyPuzzle.dateString(1792022400000L - 1));
        assertEquals("2026-10-15", DailyPuzzle.dateString(1792022400000L));
        assertEquals("2026-10-15", DailyPuzzle.dateString(1792022400000L + 86400000L - 1));
    }

    @Test
    public void differentDaysGiveDifferentPuzzles() {
        Set<String> seen = new HashSet<>();
        Set<Long> seeds = new HashSet<>();
        for (int d = 1; d <= 28; d++) {
            String date = String.format("2026-02-%02d", d);
            seen.add(fingerprint(date));
            seeds.add(DailyPuzzle.seed(date));
        }
        assertEquals(28, seen.size());
        assertEquals(28, seeds.size());
    }

    @Test
    public void aPuzzleHasFiveDifferentNonCardActivities() {
        for (int d = 1; d <= 60; d++) {
            String date = String.format("2026-%02d-%02d", 1 + d / 30, 1 + d % 28);
            List<QuestionSpec> specs = DailyPuzzle.specs(date);
            assertEquals(5, specs.size());
            Set<QuestionKind> kinds = new HashSet<>();
            for (QuestionSpec s : specs) {
                assertTrue(!s.kind().usesCardBank());
                kinds.add(s.kind());
            }
            assertEquals(5, kinds.size());
        }
    }

    @Test
    public void badDatesAreRefused() {
        for (String bad : new String[] {null, "", "2026-1-5", "2026-13-01", "2026-02-30", "15-10-2026", "2026/10/15", " 2026-10-15"}) {
            try {
                DailyPuzzle.seed(bad);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void scoreRewardsRightAndFastAnswers() {
        assertEquals(0, DailyScore.answer(false, 0));
        assertEquals(120, DailyScore.answer(true, 0));
        assertEquals(110, DailyScore.answer(true, 10));
        assertEquals(100, DailyScore.answer(true, 20));
        assertEquals(100, DailyScore.answer(true, 999));
        assertEquals(120, DailyScore.answer(true, -5));
        assertEquals(600, DailyScore.MAX_SCORE);
        assertNotEquals(DailyScore.answer(true, 3), DailyScore.answer(true, 4));
    }

    @Test
    public void openActivitiesLimitThePuzzle() {
        List<QuestionKind> open = List.of(QuestionKind.values()[0], QuestionKind.values()[1], QuestionKind.values()[2]);
        List<QuestionKind> usable = new java.util.ArrayList<>();
        for (QuestionKind k : open) {
            if (!k.usesCardBank()) {
                usable.add(k);
            }
        }
        List<QuestionSpec> specs = DailyPuzzle.specs("2026-10-15", open);
        assertEquals(Math.min(5, usable.size()), specs.size());
        for (QuestionSpec s : specs) {
            assertTrue(usable.contains(s.kind()));
        }
        assertEquals(specs, DailyPuzzle.specs("2026-10-15", open));
    }

    @Test
    public void theWholePoolGivesTheOriginalPuzzle() {
        assertEquals(DailyPuzzle.specs("2026-10-15"), DailyPuzzle.specs("2026-10-15", DailyPuzzle.pool()));
    }

    @Test
    public void noOpenActivityIsRefused() {
        try {
            DailyPuzzle.specs("2026-10-15", List.of());
            fail("accepted an empty pool");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}

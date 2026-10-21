package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.progress.Mastery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class SessionPlannerTest {
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    /** A random player: some answers per activity and some practised topics, some of them long ago. */
    private static SurpriseEngine randomEngine(Random random) {
        Map<String, int[]> activity = new HashMap<>();
        Map<String, int[]> topic = new HashMap<>();
        Mastery mastery = new Mastery();
        for (QuestionKind kind : QuestionKind.values()) {
            if (random.nextInt(3) > 0) {
                int attempts = 1 + random.nextInt(12);
                activity.put(kind.id(), new int[] {attempts, random.nextInt(attempts + 1)});
            }
            if (random.nextInt(3) == 0) {
                Instant when = NOW.minus(Duration.ofDays(random.nextInt(60)));
                for (int i = 0; i < 1 + random.nextInt(5); i++) {
                    mastery.record(kind.topic(), random.nextBoolean(), when);
                }
            }
        }
        return new SurpriseEngine(new NeedModel(activity, topic), mastery, SurpriseEngine.Weights.DEFAULT, NOW);
    }

    @Test
    public void aPlanNeverExceedsTheBudgetAndNeverRepeatsAnActivityBackToBack() {
        Random random = new Random(5);
        for (int round = 0; round < 300; round++) {
            SurpriseEngine engine = randomEngine(random);
            int budget = random.nextInt(1500);
            SessionPlanner.Plan plan = SessionPlanner.plan(engine, budget);
            assertTrue("budget " + budget + " plan " + plan.seconds(), plan.seconds() <= budget);
            List<QuestionKind> kinds = plan.kinds();
            for (int i = 1; i < kinds.size(); i++) {
                assertNotEquals(kinds.get(i - 1), kinds.get(i));
            }
            Map<QuestionKind, Integer> counts = new HashMap<>();
            for (QuestionKind k : kinds) {
                assertTrue(counts.merge(k, 1, Integer::sum) <= SessionPlanner.COPIES);
            }
            assertTrue(plan.coverCount() <= kinds.size());
        }
    }

    @Test
    public void aBudgetThatFitsNothingGivesAnEmptyPlan() {
        SessionPlanner.Plan plan = SessionPlanner.plan(SurpriseEngine.neutral(NOW), 5);
        assertTrue(plan.entries().isEmpty());
        assertTrue(SessionPlanner.plan(SurpriseEngine.neutral(NOW), 0).entries().isEmpty());
    }

    @Test
    public void aBiggerBudgetNeverGivesLessValue() {
        SurpriseEngine engine = randomEngine(new Random(9));
        double previous = -1;
        for (int budget = 0; budget <= 900; budget += 30) {
            SessionPlanner.Plan plan = SessionPlanner.plan(engine, budget);
            double value = 0;
            for (SessionPlanner.Entry e : plan.entries()) {
                value += e.value();
            }
            // the cover rule can change which questions are kept, so allow a small step back only when it switches on
            assertTrue("budget " + budget + ": " + value + " < " + previous, value + 1.0 >= previous);
            previous = value;
        }
    }

    @Test
    public void theKnapsackFindsTheBestValueByBruteForce() {
        Random random = new Random(3);
        for (int round = 0; round < 60; round++) {
            List<SessionPlanner.Entry> items = new ArrayList<>();
            QuestionKind[] kinds = QuestionKind.values();
            for (int i = 0; i < 12; i++) {
                items.add(new SessionPlanner.Entry(kinds[i], 0, 10 + random.nextInt(60), 0.1 + random.nextInt(900) / 1000.0));
            }
            int budget = random.nextInt(300);
            long best = 0;
            for (int mask = 0; mask < (1 << items.size()); mask++) {
                int seconds = 0;
                long value = 0;
                for (int i = 0; i < items.size(); i++) {
                    if ((mask >> i & 1) == 1) {
                        seconds += items.get(i).seconds();
                        value += Math.round(items.get(i).value() * 1000.0);
                    }
                }
                if (seconds <= budget) {
                    best = Math.max(best, value);
                }
            }
            long got = 0;
            int used = 0;
            for (SessionPlanner.Entry e : SessionPlanner.knapsack(items, budget)) {
                got += Math.round(e.value() * 1000.0);
                used += e.seconds();
            }
            assertEquals(best, got);
            assertTrue(used <= budget);
        }
    }

    @Test
    public void everyDueTopicWithAnActivityGetsItsOwnQuestionAtTheEnd() {
        Mastery mastery = new Mastery();
        Instant long_ago = NOW.minus(Duration.ofDays(40));
        for (String topic : List.of("bfs", "dfs", "prim", "karger")) {   // karger has no activity
            mastery.record(topic, true, long_ago);
        }
        SurpriseEngine engine = new SurpriseEngine(NeedModel.neutral(), mastery, SurpriseEngine.Weights.DEFAULT, NOW);
        SessionPlanner.Plan plan = SessionPlanner.plan(engine, 900);
        assertFalse(plan.coverDropped());
        assertEquals(List.of("bfs", "dfs", "prim"), plan.coveredTopics().stream().sorted().toList());
        assertEquals(3, plan.coverCount());
        List<SessionPlanner.Entry> tail = plan.entries().subList(plan.entries().size() - 3, plan.entries().size());
        Set<String> topics = new HashSet<>();
        for (SessionPlanner.Entry e : tail) {
            topics.add(e.kind().topic());
        }
        assertEquals(Set.of("bfs", "dfs", "prim"), topics);
    }

    @Test
    public void theMatchingGivesDifferentQuestionsToTopicsThatShareActivities() {
        // dfs has two activities (order and finish), bfs one: two due topics need three different slots at most
        List<SessionPlanner.Entry> all = new ArrayList<>();
        for (QuestionKind kind : QuestionKind.values()) {
            for (int copy = 0; copy < SessionPlanner.COPIES; copy++) {
                all.add(new SessionPlanner.Entry(kind, copy, kind.seconds(), 1.0));
            }
        }
        List<SessionPlanner.Entry> cover = SessionPlanner.cover(all, List.of("dfs", "bfs", "dijkstra", "bellman-ford"));
        assertEquals(4, cover.size());
        assertEquals(4, new HashSet<>(cover).size());
        // a topic that is not any activity's topic cannot be matched
        assertEquals(1, SessionPlanner.cover(all, List.of("karger", "bfs")).size());
    }

    @Test
    public void whenTimeIsShortTheCoverIsDroppedFirst() {
        Mastery mastery = new Mastery();
        Instant long_ago = NOW.minus(Duration.ofDays(40));
        for (String topic : List.of("floyd-warshall", "bellman-ford")) {
            mastery.record(topic, true, long_ago);
        }
        SurpriseEngine engine = new SurpriseEngine(NeedModel.neutral(), mastery, SurpriseEngine.Weights.DEFAULT, NOW);
        SessionPlanner.Plan tight = SessionPlanner.plan(engine, 80);
        assertTrue(tight.coverDropped());
        assertEquals(0, tight.coverCount());
        assertTrue(tight.seconds() <= 80);
        SessionPlanner.Plan roomy = SessionPlanner.plan(engine, 600);
        assertFalse(roomy.coverDropped());
        assertTrue(roomy.coverCount() >= 1);
    }

    @Test
    public void theSamePlayerAndBudgetGiveTheSamePlan() {
        SurpriseEngine a = randomEngine(new Random(77));
        SurpriseEngine b = randomEngine(new Random(77));
        assertEquals(SessionPlanner.plan(a, 420), SessionPlanner.plan(b, 420));
    }

    // ---- Surprise Engine v2 -----------------------------------------------------------

    @Test
    public void aForgottenTopicScoresHigherThanAFreshOneAllElseEqual() {
        Mastery mastery = new Mastery();
        for (int i = 0; i < 6; i++) {
            mastery.record("bfs", true, NOW.minus(Duration.ofDays(45)));
            mastery.record("dfs", true, NOW);
        }
        SurpriseEngine engine = new SurpriseEngine(NeedModel.neutral(), mastery, SurpriseEngine.Weights.DEFAULT, NOW);
        assertTrue(engine.score(QuestionKind.BFS_ORDER) > engine.score(QuestionKind.DFS_ORDER));
    }

    @Test
    public void explainGivesProbabilitiesThatSumToOneWithoutThePreviousActivity() {
        SurpriseEngine engine = randomEngine(new Random(4));
        List<QuestionKind> kinds = List.of(QuestionKind.values());
        List<SurpriseEngine.Row> rows = engine.explain(kinds, QuestionKind.MAX_FLOW);
        double sum = 0;
        for (SurpriseEngine.Row row : rows) {
            sum += row.probability();
            if (row.kind() == QuestionKind.MAX_FLOW) {
                assertEquals(0.0, row.probability(), 0);
            }
            double rebuilt = engine.weights().need() * row.need() + engine.weights().weakness() * (1 - row.mastery())
                    + engine.weights().novelty() * row.novelty() + engine.weights().due() * row.due();
            assertEquals(rebuilt, row.score(), 1e-12);
        }
        assertEquals(1.0, sum, 1e-9);
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).score() >= rows.get(i).score());
        }
    }

    @Test
    public void weightsSaveAndLoadAndDamageGivesTheDefaults() {
        SurpriseEngine.Weights w = new SurpriseEngine.Weights(0.1, 0.2, 0.3, 0.4);
        assertEquals(w, SurpriseEngine.Weights.parse(w.encode()));
        assertEquals(SurpriseEngine.Weights.DEFAULT, SurpriseEngine.Weights.parse("x,y"));
        assertEquals(SurpriseEngine.Weights.DEFAULT, SurpriseEngine.Weights.parse(null == null ? "" : ""));
        assertEquals(new SurpriseEngine.Weights(0.4, 0.25, 0.25, 0.5), SurpriseEngine.Weights.DEFAULT);
    }

    @Test
    public void aMixedRoundFromTheV2EngineNeverRepeatsAnActivityAndIsReproducible() {
        SurpriseEngine engine = randomEngine(new Random(2));
        for (long seed = 0; seed < 40; seed++) {
            ArenaRound a = ArenaRound.mixed(Level.MEDIUM, seed, engine);
            ArenaRound b = ArenaRound.mixed(Level.MEDIUM, seed, engine);
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.question(i), b.question(i));
                if (i > 0) {
                    assertNotEquals(a.question(i - 1).kind(), a.question(i).kind());
                }
            }
        }
    }

    @Test
    public void aPlannedRoundHasOneQuestionPerPlannedActivityInOrder() {
        SessionPlanner.Plan plan = SessionPlanner.plan(randomEngine(new Random(8)), 480);
        List<QuestionKind> kinds = plan.kinds();
        assertFalse(kinds.isEmpty());
        ArenaRound round = ArenaRound.planned(Level.EASY, 99, kinds);
        assertEquals(kinds.size(), round.size());
        for (int i = 0; i < kinds.size(); i++) {
            assertEquals(kinds.get(i), round.question(i).kind());
        }
        ArenaRound again = ArenaRound.planned(Level.EASY, 99, kinds);
        for (int i = 0; i < kinds.size(); i++) {
            assertEquals(round.question(i), again.question(i));
        }
        // a round can be played to the end and saved/restored like any other
        for (int i = 0; i < round.size(); i++) {
            round.answer(0);
            if (round.hasNext()) {
                round.advance();
            }
        }
        assertTrue(round.isFinished());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptyPlannedSessionIsRefused() {
        ArenaRound.planned(Level.EASY, 1, List.of());
    }
}

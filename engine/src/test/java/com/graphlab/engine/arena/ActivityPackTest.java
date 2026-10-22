package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.input.TextInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Activity pack A (MS11): every generated question is checked against the engine again, in a different way. */
public class ActivityPackTest {
    private static final int SEEDS = 60;

    private static Question make(QuestionKind kind, Level level, long seed) {
        return QuestionFactory.create(kind, level, seed);
    }

    // ---- Order the steps -------------------------------------------------------------

    @Test
    public void theOrderIsExactlyAWindowOfTheRealTrace() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = make(QuestionKind.ORDER_STEPS, level, seed);
                Task t = q.task();
                Graph g = ((GraphInput) q.input()).graph();
                Algorithm a = Algorithms.byId(q.topic());
                Trace trace = a.run(g, a.needsSource() ? Params.from(q.source()) : Params.none());
                List<String> inOrder = new ArrayList<>();
                for (int place = 0; place < t.items().size(); place++) {
                    inOrder.add(t.items().get(t.solution().get(place)));
                }
                boolean found = false;
                for (int from = 0; from + inOrder.size() <= trace.size(); from++) {
                    boolean all = true;
                    for (int k = 0; k < inOrder.size(); k++) {
                        all &= trace.step(from + k).message().equals(inOrder.get(k));
                    }
                    found |= all;
                }
                assertTrue(level + " seed " + seed + " " + inOrder, found);
                assertFalse("the steps must be shown mixed", t.mistakes(range(t.items().size())) == 0);
                assertEquals(level == Level.EASY ? 4 : level == Level.MEDIUM ? 5 : 6, t.items().size());
            }
        }
    }

    private static int[] range(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i;
        }
        return out;
    }

    // ---- Odd one out -----------------------------------------------------------------

    @Test
    public void generatedOddOneOutUsesRealFamilies() {
        int generated = 0;
        for (long seed = 0; seed < 200; seed++) {
            Question q = make(QuestionKind.ODD_ONE_OUT, Level.MEDIUM, seed);
            assertEquals(4, q.options().size());
            assertTrue(q.input() instanceof TextInput);
            if (q.cardId() != null) {
                continue;
            }
            generated++;
            AlgorithmCategory oddFamily = categoryOf(q.correctAnswer());
            Set<AlgorithmCategory> others = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                if (i != q.correctIndex()) {
                    others.add(categoryOf(q.options().get(i)));
                }
            }
            assertEquals("the other three share one family", 1, others.size());
            assertFalse("the odd one is from another family", others.contains(oddFamily));
        }
        assertTrue("some questions come from the families, not the cards: " + generated, generated > 20);
    }

    private static AlgorithmCategory categoryOf(String name) {
        for (Algorithm a : Algorithms.all()) {
            if (a.name().equals(name)) {
                return a.category();
            }
        }
        throw new AssertionError("not an algorithm: " + name);
    }

    @Test
    public void cardOddOneOutHasACardId() {
        boolean card = false;
        for (long seed = 0; seed < 50; seed++) {
            Question q = make(QuestionKind.ODD_ONE_OUT, Level.EASY, seed);
            card |= q.cardId() != null && q.cardId().startsWith("oo-");
        }
        assertTrue(card);
    }

    // ---- Match pairs -----------------------------------------------------------------

    @Test
    public void matchQuestionsComeFromCardsAndAllowSomeMisses() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 30; seed++) {
                Question q = make(QuestionKind.MATCH_PAIRS, level, seed);
                assertTrue(q.cardId().startsWith("mp-"));
                Task t = q.task();
                int pairs = t.pairs();
                assertEquals(level == Level.EASY ? 2 * pairs : level == Level.MEDIUM ? pairs + 1 : pairs, t.tolerance());
                MatchBoard board = new MatchBoard(t, seed);
                assertEquals(pairs, board.pairs());
            }
        }
    }

    // ---- Guess the number ------------------------------------------------------------

    @Test
    public void theNumberIsWhatTheEngineComputes() {
        Set<String> metrics = new HashSet<>();
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = make(QuestionKind.GUESS_NUMBER, level, seed);
                Graph g = ((GraphInput) q.input()).graph();
                int exact = q.task().solution().get(0);
                int tolerance = q.task().tolerance();
                double mst = Algorithms.byId("kruskal").run(g, Params.none()).result().totalWeight();
                Result dij = Algorithms.byId("dijkstra").run(g, Params.from(q.source())).result();
                double far = dij.distances().values().stream().mapToDouble(Double::doubleValue).max().orElse(-1);
                boolean match = exact == (int) mst
                        || (q.target() >= 0 && exact == dij.distances().get(q.target()).intValue())
                        || exact == (int) far;
                assertTrue(level + " seed " + seed + " exact " + exact, match);
                if (exact == (int) mst && q.topic().equals("kruskal")) {
                    metrics.add("mst");
                } else if (q.target() >= 0) {
                    metrics.add("route");
                } else {
                    metrics.add("far");
                }
                assertTrue(tolerance >= 1);
                assertTrue(q.isCorrect(q.task().mistakes(exact + tolerance)));
                assertTrue(q.isCorrect(q.task().mistakes(exact - tolerance)));
                assertFalse(q.isCorrect(q.task().mistakes(exact + tolerance + 1)));
                assertFalse(q.isCorrect(q.task().mistakes(Task.NO_VALUE)));
            }
        }
        assertEquals("all three metrics are used", 3, metrics.size());
    }

    @Test
    public void harderLevelsAreStricter() {
        double easy = 0;
        double hard = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Question e = make(QuestionKind.GUESS_NUMBER, Level.EASY, seed);
            Question h = make(QuestionKind.GUESS_NUMBER, Level.HARD, seed);
            easy += (double) e.task().tolerance() / e.task().solution().get(0);
            hard += (double) h.task().tolerance() / h.task().solution().get(0);
        }
        assertTrue(easy > hard);
    }

    // ---- Fix the pseudocode ----------------------------------------------------------

    @Test
    public void theBrokenLineIsTheOnlyOneThatDiffers() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = make(QuestionKind.FIX_PSEUDOCODE, level, seed);
                Algorithm a = Algorithms.byId(q.topic());
                List<String> original = a.pseudocode();
                String[] shown = ((TextInput) q.input()).body().split("\n", -1);
                assertEquals(original.size(), shown.length);
                List<Integer> differing = new ArrayList<>();
                for (int i = 0; i < shown.length; i++) {
                    String expected = (i + 1) + "  " + original.get(i);
                    if (!shown[i].equals(expected)) {
                        differing.add(i);
                    }
                }
                assertEquals(level + " seed " + seed, 1, differing.size());
                String right = q.correctAnswer();
                assertTrue(right, right.startsWith("Line " + (differing.get(0) + 1) + ": "));
                assertEquals(4, q.options().size());
                assertTrue(q.explanation().contains(original.get(differing.get(0)).trim()));
            }
        }
    }

    // ---- Fill the table --------------------------------------------------------------

    @Test
    public void theTableMatchesTheKnapsackAlgorithm() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = make(QuestionKind.FILL_TABLE, level, seed);
                if (!(q.input() instanceof KnapsackInput)) {
                    continue;   // the MS12 DP tables are checked in DpTablesTest
                }
                KnapsackInput in = (KnapsackInput) q.input();
                int n = in.items().size();
                int cap = in.capacity();
                Task t = q.task();
                assertEquals(cap + 1, t.columns());
                assertEquals((n + 1) * (cap + 1), t.items().size());
                int[][] dp = new int[n + 1][cap + 1];
                for (int i = 1; i <= n; i++) {
                    for (int w = 0; w <= cap; w++) {
                        dp[i][w] = dp[i - 1][w];
                        int weight = in.items().get(i - 1).weight();
                        if (weight <= w) {
                            dp[i][w] = Math.max(dp[i][w], dp[i - 1][w - weight] + in.items().get(i - 1).value());
                        }
                    }
                }
                double engine = Algorithms.byId("knapsack-01").run(in, Params.none()).result().value();
                assertEquals(engine, dp[n][cap], 1e-9);
                int blank = 0;
                for (int i = 0; i <= n; i++) {
                    for (int w = 0; w <= cap; w++) {
                        String cell = t.items().get(i * (cap + 1) + w);
                        if (cell.isEmpty()) {
                            assertTrue("first row and column are shown", i >= 1 && w >= 1);
                            assertEquals(dp[i][w], (int) t.solution().get(blank++));
                            // computable: the cells it rests on are shown
                            int weight = in.items().get(i - 1).weight();
                            assertFalse(t.items().get((i - 1) * (cap + 1) + w).isEmpty());
                            if (weight <= w) {
                                assertFalse(t.items().get((i - 1) * (cap + 1) + w - weight).isEmpty());
                            }
                        } else {
                            assertEquals(dp[i][w], Integer.parseInt(cell));
                        }
                    }
                }
                assertEquals(2 + level.ordinal(), blank);
            }
        }
    }

    // ---- Path pick -------------------------------------------------------------------

    @Test
    public void thePathIsTheOneShortestRoute() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Question q = make(QuestionKind.PATH_PICK, level, seed);
                Graph g = ((GraphInput) q.input()).graph();
                Result res = Algorithms.byId("dijkstra").run(g, Params.from(q.source())).result();
                double best = res.distances().get(q.target());
                double sum = 0;
                for (int id : q.task().solution()) {
                    sum += g.edge(id).weight();
                }
                assertEquals(best, sum, 1e-9);
                assertTrue(q.task().solution().size() >= 2);
                // draw it with the board
                PathBoard board = new PathBoard(g, q.source(), q.target());
                int at = q.source();
                List<Integer> remaining = new ArrayList<>(q.task().solution());
                while (!remaining.isEmpty()) {
                    int next = -1;
                    for (int id : remaining) {
                        Edge e = g.edge(id);
                        if (e.from() == at) {
                            next = e.to();
                        } else if (e.to() == at) {
                            next = e.from();
                        }
                        if (next >= 0) {
                            remaining.remove(Integer.valueOf(id));
                            break;
                        }
                    }
                    assertTrue(next >= 0);
                    assertEquals(PathBoard.Result.ADDED, board.tap(next));
                    at = next;
                }
                assertTrue(board.isComplete());
                assertEquals(0, board.mistakes(q.task()));
                assertTrue(q.isCorrect(board.mistakes(q.task())));
            }
        }
    }

    // ---- the kinds -------------------------------------------------------------------

    @Test
    public void everyKindHasADifficultyTagAndTheTagsAreSpread() {
        Set<Level> tags = new HashSet<>();
        for (QuestionKind kind : QuestionKind.values()) {
            assertNotNull(kind.toString(), kind.difficulty());
            tags.add(kind.difficulty());
        }
        assertEquals(3, tags.size());
    }

    @Test
    public void thePlanListsTenActivitiesAndTheyAreAllThere() {
        for (String id : List.of("next-step", "true-false", "best-tool", "order-steps", "odd-one-out", "match-pairs",
                "guess-number", "fix-pseudocode", "fill-table", "path-pick")) {
            assertNotNull(id, QuestionKind.findById(id));
        }
    }

    @Test
    public void interactionsMatchTheTasks() {
        int tasks = 0;
        for (QuestionKind kind : QuestionKind.values()) {
            Question q = make(kind, Level.EASY, 3);
            assertEquals(kind.interaction(), q.interaction());
            if (kind.interaction() == Interaction.CHOICE) {
                assertNull(q.task());
            } else {
                tasks++;
            }
        }
        assertEquals(5, tasks);
    }

    @Test
    public void aRoundOfTaskQuestionsCanBePlayedAndRestored() {
        for (QuestionKind kind : new QuestionKind[] {QuestionKind.ORDER_STEPS, QuestionKind.MATCH_PAIRS, QuestionKind.GUESS_NUMBER,
                QuestionKind.FILL_TABLE, QuestionKind.PATH_PICK}) {
            ArenaRound round = ArenaRound.of(kind, Level.MEDIUM, 11L);
            for (int i = 0; i < round.size(); i++) {
                round.answer(i % 2 == 0 ? 0 : 7);   // zero mistakes, or seven
                if (round.hasNext()) {
                    round.advance();
                }
            }
            assertTrue(round.isFinished());
            assertEquals(1, round.correctCount());   // answers 0 then 7 mistakes: only the first is right
            ArenaRound again = ArenaRound.of(kind, Level.MEDIUM, 11L);
            again.restore(round.choices(), round.index());
            assertEquals(round.correctCount(), again.correctCount());
        }
    }
}

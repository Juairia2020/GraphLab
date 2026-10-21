package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.Test;

/**
 * Every generated question is checked against {@link Reference}, which solves the same problem a different
 * way. If the engine and the reference ever disagree, the Arena would be teaching a wrong answer.
 */
public class QuestionFactoryTest {
    private static final int SEEDS = 80;

    // ---- every kind and level ------------------------------------------------------------

    @Test
    public void everyKindAndLevelMakesValidQuestions() {
        for (QuestionKind kind : QuestionKind.values()) {
            for (Level level : Level.values()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    Question q = QuestionFactory.create(kind, level, seed);
                    String where = kind + " " + level + " seed " + seed;
                    assertEquals(where, kind, q.kind());
                    assertEquals(where, level, q.level());
                    if (kind != QuestionKind.FILL_TABLE) {
                        assertEquals(where, kind.inputKind(), q.input().kind());   // MS12: Fill the table also shows text
                    }
                    int options = q.options().size();
                    if (kind.interaction() != Interaction.CHOICE) {
                        assertNotNull(where, q.task());
                        assertEquals(where, kind.interaction(), q.interaction());
                        assertEquals(where, 0, options);
                        assertEquals(where, 0, q.task().mistakes(perfect(q)));
                        assertTrue(where, q.isCorrect(q.task().mistakes(perfect(q))));
                        assertTrue(where, q.correctAnswer().length() > 3);
                    } else {
                        switch (kind) {
                            case NEGATIVE_CYCLE, TRUE_FALSE, WILL_CHANGE -> assertEquals(where, 2, options);
                            case REVERSE_ENGINEER, RACE_BET -> assertEquals(where, 3, options);
                            case BEST_TOOL -> assertTrue(where, options >= 3 && options <= 4);
                            case NEXT_STEP -> assertTrue(where, options >= 2 && options <= 4);
                            default -> assertEquals(where, 4, options);
                        }
                        assertEquals(where, q.options().size(), new HashSet<>(q.options()).size());
                        assertTrue(where, q.isCorrect(q.correctIndex()));
                    }
                    assertTrue(where, q.explanation().length() > 20);
                }
            }
        }
    }

    /** The answer a perfect player gives, in the form {@link Task#mistakes} reads. */
    static int[] perfect(Question q) {
        Task t = q.task();
        switch (t.interaction()) {
            case ORDER, TABLE, PATH -> {
                return t.solution().stream().mapToInt(Integer::intValue).toArray();
            }
            case NUMBER -> {
                return new int[] {t.solution().get(0)};
            }
            default -> {
                return new int[] {0};
            }
        }
    }

    @Test
    public void sameSeedGivesTheSameQuestion() {
        for (QuestionKind kind : QuestionKind.values()) {
            for (Level level : Level.values()) {
                for (long seed = 0; seed < 10; seed++) {
                    assertEquals(QuestionFactory.create(kind, level, seed), QuestionFactory.create(kind, level, seed));
                }
            }
        }
    }

    @Test
    public void differentSeedsGiveDifferentQuestions() {
        for (QuestionKind kind : QuestionKind.values()) {
            Set<Question> seen = new HashSet<>();   // records compare the whole question, graph included
            for (long seed = 0; seed < 40; seed++) {
                seen.add(QuestionFactory.create(kind, Level.MEDIUM, seed));
            }
            assertTrue(kind + " made only " + seen.size() + " different questions", seen.size() >= (kind == QuestionKind.MATCH_PAIRS ? 6 : kind == QuestionKind.ODD_ONE_OUT ? 8 : kind.fromCards() ? 15 : 30));
        }
    }

    @Test
    public void rightAnswerIsNotAlwaysInTheSamePlace() {
        for (QuestionKind kind : QuestionKind.values()) {
            if (kind == QuestionKind.NEGATIVE_CYCLE || kind.interaction() != Interaction.CHOICE) {
                continue;
            }
            Set<Integer> places = new HashSet<>();
            for (long seed = 0; seed < 60; seed++) {
                places.add(QuestionFactory.create(kind, Level.EASY, seed).correctIndex());
            }
            int expected = switch (kind) {
                case TRUE_FALSE, WILL_CHANGE -> 2;
                case NEXT_STEP, BEST_TOOL, REVERSE_ENGINEER, RACE_BET -> 3;
                default -> 4;
            };
            assertTrue(kind + " uses " + places.size() + " places, wanted at least " + expected, places.size() >= expected);
        }
    }

    @Test
    public void graphSizesFollowTheLevel() {
        for (QuestionKind kind : QuestionKind.values()) {
            if (kind.inputKind() != com.graphlab.engine.input.InputKind.GRAPH) {
                continue;
            }
            for (Level level : Level.values()) {
                for (long seed = 0; seed < 40; seed++) {
                    Graph g = ((GraphInput) QuestionFactory.create(kind, level, seed).input()).graph();
                    assertTrue(kind + " " + level, g.nodeCount() >= level.minNodes() && g.nodeCount() <= level.maxNodes());
                }
            }
        }
    }

    @Test
    public void easyQuestionsStartAtA() {
        for (QuestionKind kind : new QuestionKind[] {QuestionKind.BFS_ORDER, QuestionKind.DFS_ORDER, QuestionKind.DFS_FINISH,
                QuestionKind.DIJKSTRA_DISTANCE, QuestionKind.PRIM_THIRD_EDGE}) {
            for (long seed = 0; seed < 40; seed++) {
                assertEquals(0, QuestionFactory.create(kind, Level.EASY, seed).source());
            }
        }
    }

    @Test
    public void listsAndItemsStayWithinTheInputLimits() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 100; seed++) {
                ArrayInput list = (ArrayInput) QuestionFactory.create(QuestionKind.MERGE_COMPARISONS, level, seed).input();
                assertEquals(level.listSize(), list.values().size());
                assertEquals("distinct numbers", list.values().size(), new HashSet<>(list.values()).size());
                KnapsackInput bag = (KnapsackInput) QuestionFactory.create(QuestionKind.KNAPSACK_VALUE, level, seed).input();
                assertTrue(bag.items().size() >= level.minItems() && bag.items().size() <= level.maxItems());
                assertTrue(bag.capacity() >= level.minCapacity() && bag.capacity() <= level.maxCapacity());
            }
        }
    }

    // ---- the right answer, against the reference solver ------------------------------------

    @Test
    public void bfsOrderMatchesReference() {
        forEachGraph(QuestionKind.BFS_ORDER, (q, g) -> {
            assertEquals(names(g, Reference.bfs(g, q.source())), q.correctAnswer());
            assertWrongOptionsArePermutations(q, g);
        });
    }

    @Test
    public void dfsOrderMatchesReference() {
        forEachGraph(QuestionKind.DFS_ORDER, (q, g) -> {
            List<Integer> discovery = new ArrayList<>();
            Reference.dfs(g, q.source(), discovery, new ArrayList<>());
            assertEquals(names(g, discovery), q.correctAnswer());
            assertWrongOptionsArePermutations(q, g);
        });
    }

    @Test
    public void dfsFinishMatchesReference() {
        forEachGraph(QuestionKind.DFS_FINISH, (q, g) -> {
            List<Integer> finish = new ArrayList<>();
            Reference.dfs(g, q.source(), new ArrayList<>(), finish);
            assertEquals(names(g, finish), q.correctAnswer());
            assertWrongOptionsArePermutations(q, g);
        });
    }

    @Test
    public void dijkstraDistanceMatchesReference() {
        forEachGraph(QuestionKind.DIJKSTRA_DISTANCE, (q, g) -> {
            double d = Reference.allPairs(g)[q.source()][q.target()];
            assertEquals(Choices.number(d), q.correctAnswer());
        });
    }

    @Test
    public void bellmanDistanceMatchesReference() {
        forEachGraph(QuestionKind.BELLMAN_DISTANCE, (q, g) -> {
            double d = Reference.allPairs(g)[q.source()][q.target()];
            assertEquals(Choices.number(d), q.correctAnswer());
            boolean negative = false;
            for (Edge e : g.edges()) {
                negative |= e.weight() < 0;
            }
            assertTrue("the graph has a negative edge", negative);
        });
    }

    @Test
    public void negativeCycleMatchesReferenceAndBothAnswersHappen() {
        int yes = 0;
        int no = 0;
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 300; seed++) {
                Question q = QuestionFactory.create(QuestionKind.NEGATIVE_CYCLE, level, seed);
                Graph g = ((GraphInput) q.input()).graph();
                boolean expected = Reference.negativeCycleFrom(g, 0);
                assertEquals(expected ? 0 : 1, q.correctIndex());
                if (expected) {
                    yes++;
                } else {
                    no++;
                }
            }
        }
        assertTrue("yes " + yes + " no " + no, yes > 300 && no > 300);
    }

    @Test
    public void floydEntryMatchesReference() {
        int noPath = 0;
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 300; seed++) {
                Question q = QuestionFactory.create(QuestionKind.FLOYD_ENTRY, level, seed);
                Graph g = ((GraphInput) q.input()).graph();
                double d = Reference.allPairs(g)[q.source()][q.target()];
                if (Double.isInfinite(d)) {
                    assertEquals("No path", q.correctAnswer());
                    noPath++;
                } else {
                    assertEquals(Choices.number(d), q.correctAnswer());
                }
                assertEquals("only Hard graphs are directed", level == Level.HARD, g.isDirected());
            }
        }
        assertTrue("the \"No path\" answer is exercised: " + noPath, noPath > 0);
    }

    @Test
    public void kruskalWeightMatchesReference() {
        forEachGraph(QuestionKind.KRUSKAL_WEIGHT, (q, g) -> {
            assertEquals(Choices.number(Reference.mstWeight(g)), q.correctAnswer());
            assertDistinctWeights(g);
        });
    }

    @Test
    public void kruskalLastEdgeMatchesReference() {
        forEachGraph(QuestionKind.KRUSKAL_LAST_EDGE, (q, g) -> {
            List<Edge> tree = Reference.kruskalEdges(g);
            assertEquals(edgeName(g, tree.get(tree.size() - 1)), q.correctAnswer());
            assertDistinctWeights(g);
        });
    }

    @Test
    public void primThirdEdgeMatchesReference() {
        forEachGraph(QuestionKind.PRIM_THIRD_EDGE, (q, g) -> {
            assertEquals(edgeName(g, Reference.primEdges(g, q.source()).get(2)), q.correctAnswer());
            assertDistinctWeights(g);
        });
    }

    @Test
    public void maxFlowMatchesReference() {
        forEachGraph(QuestionKind.MAX_FLOW, (q, g) -> {
            assertEquals(0, q.source());
            assertEquals(g.nodeCount() - 1, q.target());
            assertEquals(Choices.number(Reference.maxFlow(g, q.source(), q.target())), q.correctAnswer());
        });
    }

    @Test
    public void coloursMatchReference() {
        forEachGraph(QuestionKind.COLOURS_NEEDED, (q, g) ->
                assertEquals(Integer.toString(Reference.chromaticNumber(g)), q.correctAnswer()));
    }

    @Test
    public void comparisonCountsMatchReference() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 150; seed++) {
                Question merge = QuestionFactory.create(QuestionKind.MERGE_COMPARISONS, level, seed);
                List<Integer> mergeValues = ((ArrayInput) merge.input()).values();
                assertEquals(Integer.toString(Reference.mergeComparisons(mergeValues)), merge.correctAnswer());
                Question quick = QuestionFactory.create(QuestionKind.QUICK_COMPARISONS, level, seed);
                List<Integer> quickValues = ((ArrayInput) quick.input()).values();
                assertEquals(Integer.toString(Reference.quickComparisons(quickValues)), quick.correctAnswer());
            }
        }
    }

    @Test
    public void knapsackValuesMatchReference() {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 150; seed++) {
                Question zeroOne = QuestionFactory.create(QuestionKind.KNAPSACK_VALUE, level, seed);
                assertEquals(Integer.toString(Reference.knapsack01((KnapsackInput) zeroOne.input())), zeroOne.correctAnswer());
                Question fractional = QuestionFactory.create(QuestionKind.FRACTIONAL_VALUE, level, seed);
                assertEquals(Choices.number(Reference.knapsackFractional((KnapsackInput) fractional.input())),
                        fractional.correctAnswer());
            }
        }
    }

    @Test
    public void wrongAnswersIncludeTheCommonMistake() {
        // Dijkstra: the route with the fewest edges is offered as a wrong answer whenever it costs more
        int offered = 0;
        int available = 0;
        for (long seed = 0; seed < 200; seed++) {
            Question q = QuestionFactory.create(QuestionKind.DIJKSTRA_DISTANCE, Level.MEDIUM, seed);
            if (q.explanation().contains("fewest edges")) {
                available++;
                String cost = q.explanation().replaceAll("(?s).*fewest edges, .*? costs (\\d+),.*", "$1");
                if (q.options().contains(cost)) {
                    offered++;
                }
            }
        }
        assertTrue("some questions have the trap: " + available, available > 20);
        assertEquals("the trap is always one of the options", available, offered);
    }

    @Test
    public void explanationsStateTheRightAnswer() {
        for (QuestionKind kind : new QuestionKind[] {QuestionKind.DIJKSTRA_DISTANCE, QuestionKind.KRUSKAL_WEIGHT,
                QuestionKind.MAX_FLOW, QuestionKind.COLOURS_NEEDED, QuestionKind.KNAPSACK_VALUE, QuestionKind.FRACTIONAL_VALUE,
                QuestionKind.BFS_ORDER, QuestionKind.KRUSKAL_LAST_EDGE, QuestionKind.PRIM_THIRD_EDGE}) {
            for (long seed = 0; seed < 40; seed++) {
                Question q = QuestionFactory.create(kind, Level.MEDIUM, seed);
                assertTrue(kind + ": " + q.explanation(), q.explanation().contains(q.correctAnswer())
                        || kind == QuestionKind.BFS_ORDER);
                assertNotEquals("", q.explanation().trim());
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static void forEachGraph(QuestionKind kind, BiConsumer<Question, Graph> check) {
        for (Level level : Level.values()) {
            for (long seed = 0; seed < SEEDS * 2; seed++) {
                Question q = QuestionFactory.create(kind, level, seed);
                check.accept(q, ((GraphInput) q.input()).graph());
            }
        }
    }

    private static String names(Graph g, List<Integer> ids) {
        List<String> out = new ArrayList<>();
        for (int id : ids) {
            out.add(g.node(id).label());
        }
        return String.join(", ", out);
    }

    private static String edgeName(Graph g, Edge e) {
        return g.node(e.from()).label() + "-" + g.node(e.to()).label();
    }

    private static void assertWrongOptionsArePermutations(Question q, Graph g) {
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < g.nodeCount(); i++) {
            labels.add(g.node(i).label());
        }
        for (String option : q.options()) {
            List<String> parts = List.of(option.split(", "));
            assertEquals(g.nodeCount(), parts.size());
            assertEquals(labels, new HashSet<>(parts));
        }
    }

    private static void assertDistinctWeights(Graph g) {
        Set<Double> weights = new HashSet<>();
        for (Edge e : g.edges()) {
            assertTrue("weights are all different", weights.add(e.weight()));
        }
    }
}

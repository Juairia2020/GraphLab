package com.graphlab.engine.arena;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.model.Arc;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Makes Arena questions. The same kind, level and seed always give the same question.
 *
 * <p>The rule behind every question: the problem is generated at random, then the real engine
 * algorithm is run on it, and the right answer is read from the algorithm's {@link Result}. The wrong
 * answers are mistakes a learner really makes (using breadth-first search for a weighted graph,
 * adding the cheapest edges without checking for cycles, ...). Plain Java, no Android.
 *
 * <p>Graph questions say "visit neighbours in alphabetical order", and the generator builds graphs
 * in canonical edge order (see {@link RandomGraphs}), so a player following that rule gets the
 * engine's answer.
 */
public final class QuestionFactory {
    private static final int WANTED_OPTIONS = 4;
    private static final int MAX_ATTEMPTS = 300;
    private static final String ARROW = " \u2192 ";

    private QuestionFactory() {
    }

    /**
     * @throws IllegalStateException if no valid question came out of {@value #MAX_ATTEMPTS} random
     *                               tries (a bug: every kind and level is tested with many seeds)
     */
    public static Question create(QuestionKind kind, Level level, long seed) {
        return create(kind, level, seed, Set.of());
    }

    /**
     * Like {@link #create(QuestionKind, Level, long)}, but a card whose id is in {@code usedCards} is skipped while other
     * cards remain, so a round does not repeat a card.
     */
    public static Question create(QuestionKind kind, Level level, long seed, Set<String> usedCards) {
        Random random = new Random(seed);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Question question;
            try {
                question = tryCreate(kind, level, random, usedCards);
            } catch (IllegalArgumentException tooHard) {
                question = null;   // an algorithm refused this input (for example a colouring that needs too many steps)
            }
            if (question != null) {
                return question;
            }
        }
        throw new IllegalStateException("Could not make a " + kind + " question at level " + level + " from seed " + seed);
    }

    private static Question tryCreate(QuestionKind kind, Level level, Random r, Set<String> usedCards) {
        return switch (kind) {
            case BFS_ORDER -> order(kind, level, r, true, false);
            case DFS_ORDER -> order(kind, level, r, false, false);
            case DFS_FINISH -> order(kind, level, r, false, true);
            case DIJKSTRA_DISTANCE -> dijkstra(level, r);
            case BELLMAN_DISTANCE -> bellmanDistance(level, r);
            case NEGATIVE_CYCLE -> negativeCycle(level, r);
            case FLOYD_ENTRY -> floyd(level, r);
            case KRUSKAL_WEIGHT -> kruskalWeight(level, r);
            case KRUSKAL_LAST_EDGE -> kruskalLastEdge(level, r);
            case PRIM_THIRD_EDGE -> primThirdEdge(level, r);
            case MAX_FLOW -> maxFlow(level, r);
            case FRACTIONAL_VALUE -> knapsack(kind, level, r);
            case COLOURS_NEEDED -> colours(level, r);
            case MERGE_COMPARISONS -> comparisons(kind, level, r);
            case QUICK_COMPARISONS -> comparisons(kind, level, r);
            case KNAPSACK_VALUE -> knapsack(kind, level, r);
            case NEXT_STEP -> nextStep(level, r);
            case TRUE_FALSE, BEST_TOOL -> card(kind, level, r, usedCards);
            case ORDER_STEPS -> orderSteps(level, r);
            case ODD_ONE_OUT -> oddOneOut(level, r, usedCards);
            case MATCH_PAIRS -> matchPairs(level, r, usedCards);
            case GUESS_NUMBER -> guessNumber(level, r);
            case FIX_PSEUDOCODE -> fixPseudocode(level, r);
            case FILL_TABLE -> fillTable(level, r);
            case PATH_PICK -> pathPick(level, r);
            case SPOT_BUG, COMPLEXITY_GUESS, WILL_CHANGE, REVERSE_ENGINEER, RACE_BET, CUT_NETWORK -> PackB.create(kind, level, r);
            case BREAK_IT, FOG_OF_WAR -> PackC.create(kind, level, r);
        };
    }

    // =============================================================================
    // Next step: which node does Dijkstra settle next? (MS6 plan activity "Next step")
    // =============================================================================

    private static Question nextStep(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        int start = 0;
        Result res = run(QuestionKind.NEXT_STEP, g, Params.from(start));
        List<Integer> order = res.visitOrder();   // the order Dijkstra settles the nodes
        if (order.size() != g.nodeCount() || order.size() < 3) {
            return null;
        }
        int settledCount = 1 + r.nextInt(order.size() - 2);   // 1 .. n - 2 settled, so a next node exists
        Set<Integer> settled = new java.util.LinkedHashSet<>(order.subList(0, settledCount));
        java.util.Map<Integer, Double> waiting = new java.util.TreeMap<>();
        for (int u : settled) {
            double du = res.distances().get(u);
            for (Arc arc : g.arcsFrom(u)) {
                if (!settled.contains(arc.to())) {
                    waiting.merge(arc.to(), du + arc.weight(), Math::min);
                }
            }
        }
        if (waiting.size() < 2) {
            return null;
        }
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        boolean tie = false;
        for (java.util.Map.Entry<Integer, Double> e : waiting.entrySet()) {
            if (e.getValue() < bestDistance) {
                best = e.getKey();
                bestDistance = e.getValue();
                tie = false;
            } else if (e.getValue() == bestDistance) {
                tie = true;
            }
        }
        if (tie || best != order.get(settledCount)) {
            return null;   // an ambiguous question is never asked; the second test is a safety net
        }
        List<Integer> shown = new ArrayList<>(waiting.keySet());
        if (shown.size() > WANTED_OPTIONS) {
            List<Integer> others = new ArrayList<>(shown);
            others.remove(Integer.valueOf(best));
            Collections.shuffle(others, r);
            shown = new ArrayList<>(others.subList(0, WANTED_OPTIONS - 1));
            shown.add(best);
        }
        Collections.sort(shown);
        List<String> options = new ArrayList<>();
        for (int id : shown) {
            options.add(g.nodeName(id));
        }
        int correctIndex = shown.indexOf(best);

        com.graphlab.engine.trace.GraphFrame.Builder paint = com.graphlab.engine.trace.GraphFrame.builder(g);
        for (int u : settled) {
            paint.node(u, com.graphlab.engine.trace.State.VISITED).label(u, Choices.number(res.distances().get(u)));
        }
        for (java.util.Map.Entry<Integer, Double> e : waiting.entrySet()) {
            paint.node(e.getKey(), com.graphlab.engine.trace.State.FRONTIER).label(e.getKey(), Choices.number(e.getValue()));
        }
        StringBuilder done = new StringBuilder();
        for (int u : settled) {
            done.append(done.length() == 0 ? "" : ", ").append(g.nodeName(u));
        }
        String prompt = "Dijkstra started at " + g.nodeName(start) + ". It has settled " + done + ". Settled nodes show their "
                + "final distance. Waiting nodes show the best distance found so far. Which node does Dijkstra settle next?";
        StringBuilder why = new StringBuilder("Dijkstra always settles the waiting node with the smallest distance. ")
                .append(g.nodeName(best)).append(" has ").append(Choices.number(bestDistance));
        for (int id : shown) {
            if (id != best) {
                why.append(", ").append(g.nodeName(id)).append(" has ").append(Choices.number(waiting.get(id)));
            }
        }
        why.append(", so ").append(g.nodeName(best)).append(" is next.");
        return new Question(QuestionKind.NEXT_STEP, level, prompt, new GraphInput(g), start, -1, options, correctIndex,
                why.toString(), null, "dijkstra", paint.build());
    }

    // =============================================================================
    // Cards: true or false, best tool (from content/questions.json)
    // =============================================================================

    private static Question card(QuestionKind kind, Level level, Random r, Set<String> used) {
        com.graphlab.engine.content.Card.Activity activity = kind == QuestionKind.TRUE_FALSE
                ? com.graphlab.engine.content.Card.Activity.TRUE_FALSE : com.graphlab.engine.content.Card.Activity.BEST_TOOL;
        com.graphlab.engine.content.Card card = com.graphlab.engine.content.CardBank.current()
                .pick(activity, level.ordinal(), r, used);
        String prompt = activity == com.graphlab.engine.content.Card.Activity.TRUE_FALSE
                ? "Is this statement true or false?" : "Which algorithm is the best tool for this job?";
        return new Question(kind, level, prompt, new com.graphlab.engine.input.TextInput(card.text()), -1, -1,
                card.options(), card.correct(), card.explanation(), card.id(), card.topic(), null);
    }

    // =============================================================================
    // Activity pack A (MS11): the seven activities that are not a plain button choice or card
    // =============================================================================

    private static Question taskQuestion(QuestionKind kind, Level level, String prompt, Input input, int source, int target,
            String explanation, String cardId, String topic, Task task) {
        return new Question(kind, level, prompt, input, source, target, List.of(), 0, explanation, cardId, topic, null, task);
    }

    /** The bank to use for an activity: the installed one, or the bundled one when a fetched older file lacks it. */
    private static com.graphlab.engine.content.CardBank bankFor(com.graphlab.engine.content.Card.Activity activity) {
        com.graphlab.engine.content.CardBank bank = com.graphlab.engine.content.CardBank.current();
        return bank.has(activity) ? bank : com.graphlab.engine.content.CardBank.bundled();
    }

    // ---- Order the steps -------------------------------------------------------------

    private static final String[] ORDER_ALGORITHMS = {"bfs", "dijkstra", "kruskal", "prim"};

    private static Question orderSteps(Level level, Random r) {
        String id = ORDER_ALGORITHMS[r.nextInt(ORDER_ALGORITHMS.length)];
        com.graphlab.engine.trace.Algorithm algorithm = Algorithms.byId(id);
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        int start = start(level, r, g);
        com.graphlab.engine.trace.Trace trace = algorithm.run(g, algorithm.needsSource() ? Params.from(start) : Params.none());
        int n = switch (level) {
            case EASY -> 4;
            case MEDIUM -> 5;
            case HARD -> 6;
        };
        if (trace.size() < n + 2) {
            return null;
        }
        List<String> window = null;
        for (int tries = 0; tries < 40 && window == null; tries++) {
            int from = r.nextInt(trace.size() - n + 1);
            List<String> messages = new ArrayList<>();
            for (int i = from; i < from + n; i++) {
                messages.add(trace.step(i).message());
            }
            if (new HashSet<>(messages).size() == n) {
                window = messages;
            }
        }
        if (window == null) {
            return null;
        }
        List<Integer> shown = new ArrayList<>();   // shown.get(k) = which true place the k-th shown item has
        for (int i = 0; i < n; i++) {
            shown.add(i);
        }
        boolean mixed = false;
        for (int tries = 0; tries < 50 && !mixed; tries++) {
            Collections.shuffle(shown, r);
            for (int i = 0; i < n; i++) {
                mixed |= shown.get(i) != i;
            }
        }
        if (!mixed) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            items.add(window.get(shown.get(k)));
        }
        List<Integer> solution = new ArrayList<>();   // for each true place, the index of the item that holds it
        for (int place = 0; place < n; place++) {
            solution.add(shown.indexOf(place));
        }
        StringBuilder answer = new StringBuilder();
        for (int place = 0; place < n; place++) {
            answer.append(place == 0 ? "" : "; ").append(place + 1).append(". ").append(window.get(place));
        }
        String how = algorithm.needsSource() ? ", starting at node " + g.nodeName(start) : "";
        String prompt = "These " + n + " steps come one after another from a real run of " + algorithm.name() + how
                + ", but they are mixed up. Drag them into the order they happen.";
        Task task = new Task(Interaction.ORDER, items, solution, 0, 0, answer.toString());
        return taskQuestion(QuestionKind.ORDER_STEPS, level, prompt, new GraphInput(g), algorithm.needsSource() ? start : -1,
                -1, "This is the order the engine ran them in: " + answer + ".", null, id, task);
    }

    // ---- Odd one out -----------------------------------------------------------------

    private static Question oddOneOut(Level level, Random r, Set<String> used) {
        if (r.nextInt(3) == 0) {
            Question generated = oddFromFamilies(level, r);
            if (generated != null) {
                return generated;
            }
        }
        com.graphlab.engine.content.Card card = bankFor(com.graphlab.engine.content.Card.Activity.ODD_ONE_OUT)
                .pick(com.graphlab.engine.content.Card.Activity.ODD_ONE_OUT, level.ordinal(), r, used);
        List<String> wrong = new ArrayList<>(card.options());
        wrong.remove(card.correct());
        Choices c = Choices.of(r, card.correctAnswer(), wrong, 4);
        return new Question(QuestionKind.ODD_ONE_OUT, level, "Three of these four belong together. Which one does not?",
                new com.graphlab.engine.input.TextInput(card.text()), -1, -1, c.options, c.correctIndex, card.explanation(),
                card.id(), card.topic(), null);
    }

    /** Three algorithms of one family and one from another family; the families are the engine's own categories. */
    private static Question oddFromFamilies(Level level, Random r) {
        java.util.Map<com.graphlab.engine.trace.AlgorithmCategory, List<String>> byFamily = new java.util.EnumMap<>(
                com.graphlab.engine.trace.AlgorithmCategory.class);
        for (com.graphlab.engine.trace.Algorithm a : Algorithms.all()) {
            byFamily.computeIfAbsent(a.category(), k -> new ArrayList<>()).add(a.name());
        }
        List<com.graphlab.engine.trace.AlgorithmCategory> big = new ArrayList<>();
        for (java.util.Map.Entry<com.graphlab.engine.trace.AlgorithmCategory, List<String>> e : byFamily.entrySet()) {
            if (e.getValue().size() >= 3) {
                big.add(e.getKey());
            }
        }
        if (big.isEmpty()) {
            return null;
        }
        com.graphlab.engine.trace.AlgorithmCategory family = big.get(r.nextInt(big.size()));
        List<String> same = new ArrayList<>(byFamily.get(family));
        Collections.shuffle(same, r);
        same = new ArrayList<>(same.subList(0, 3));
        List<String> others = new ArrayList<>();
        for (java.util.Map.Entry<com.graphlab.engine.trace.AlgorithmCategory, List<String>> e : byFamily.entrySet()) {
            if (e.getKey() != family) {
                others.addAll(e.getValue());
            }
        }
        String odd = others.get(r.nextInt(others.size()));
        com.graphlab.engine.trace.AlgorithmCategory oddFamily = null;
        for (java.util.Map.Entry<com.graphlab.engine.trace.AlgorithmCategory, List<String>> e : byFamily.entrySet()) {
            if (e.getValue().contains(odd)) {
                oddFamily = e.getKey();
            }
        }
        Choices c = Choices.of(r, odd, same, 4);
        String why = same.get(0) + ", " + same.get(1) + " and " + same.get(2) + " are all in the family \"" + family.title()
                + "\". " + odd + " is in another family: \"" + oddFamily.title() + "\".";
        return new Question(QuestionKind.ODD_ONE_OUT, level, "Three of these four belong together. Which one does not?",
                new com.graphlab.engine.input.TextInput("Which one does not belong with the other three?"), -1, -1, c.options,
                c.correctIndex, why, null, "general", null);
    }

    // ---- Match pairs -----------------------------------------------------------------

    private static Question matchPairs(Level level, Random r, Set<String> used) {
        com.graphlab.engine.content.Card card = bankFor(com.graphlab.engine.content.Card.Activity.MATCH_PAIRS)
                .pick(com.graphlab.engine.content.Card.Activity.MATCH_PAIRS, level.ordinal(), r, used);
        int pairs = card.options().size() / 2;
        int allowed = switch (level) {
            case EASY -> pairs * 2;
            case MEDIUM -> pairs + 1;
            case HARD -> pairs;
        };
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < pairs; i++) {
            answer.append(i == 0 ? "" : "; ").append(card.options().get(2 * i)).append(" = ").append(card.options().get(2 * i + 1));
        }
        Task task = new Task(Interaction.MATCH, card.options(), List.of(), allowed, 0, answer.toString());
        String prompt = "Flip two tiles at a time. A term and its meaning make a pair. You may miss " + allowed + " times or fewer.";
        return taskQuestion(QuestionKind.MATCH_PAIRS, level, prompt, new com.graphlab.engine.input.TextInput(card.text()), -1, -1,
                card.explanation(), card.id(), card.topic(), task);
    }

    // ---- Guess the number ------------------------------------------------------------

    private static Question guessNumber(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        int start = start(level, r, g);
        int metric = r.nextInt(3);
        double exact;
        String prompt;
        String topic;
        String why;
        int target = -1;
        if (metric == 0) {
            exact = Algorithms.byId("kruskal").run(g, Params.none()).result().totalWeight();
            prompt = "Estimate the total weight of the cheapest set of edges that connects every node (a minimum spanning tree).";
            topic = "kruskal";
            why = "Kruskal adds the lightest edges that do not close a cycle. The tree it builds weighs " + Choices.number(exact) + ".";
        } else {
            Result res = Algorithms.byId("dijkstra").run(g, Params.from(start)).result();
            if (metric == 1) {
                target = other(r, g, start);
                exact = res.distances().get(target);
                prompt = "Estimate the length of the shortest route from " + g.nodeName(start) + " to " + g.nodeName(target) + ".";
                why = "Dijkstra's distance from " + g.nodeName(start) + " to " + g.nodeName(target) + " is " + Choices.number(exact) + ".";
            } else {
                exact = 0;
                for (double d : res.distances().values()) {
                    exact = Math.max(exact, d);
                }
                prompt = "Estimate how far the farthest node is from " + g.nodeName(start) + " (the largest shortest distance).";
                why = "Dijkstra's largest distance from " + g.nodeName(start) + " is " + Choices.number(exact) + ".";
            }
            topic = "dijkstra";
        }
        if (Math.abs(exact - Math.rint(exact)) > 1e-9 || exact < 1 || exact > 9999) {
            return null;
        }
        int value = (int) Math.rint(exact);
        double share = switch (level) {
            case EASY -> 0.20;
            case MEDIUM -> 0.10;
            case HARD -> 0.05;
        };
        int tolerance = Math.max(1, (int) Math.round(value * share));
        String answer = value + " (any answer from " + (value - tolerance) + " to " + (value + tolerance) + " counts)";
        Task task = new Task(Interaction.NUMBER, List.of(), List.of(value), tolerance, 0, answer);
        return taskQuestion(QuestionKind.GUESS_NUMBER, level, prompt, new GraphInput(g), start, target, why, null, topic, task);
    }

    // ---- Fix the pseudocode ----------------------------------------------------------

    private static Question fixPseudocode(Level level, Random r) {
        List<com.graphlab.engine.trace.Algorithm> pool = new ArrayList<>();
        for (com.graphlab.engine.trace.Algorithm a : Algorithms.all()) {
            if (!PseudocodeFaults.breakable(a.pseudocode()).isEmpty()) {
                pool.add(a);
            }
        }
        com.graphlab.engine.trace.Algorithm algorithm = pool.get(r.nextInt(pool.size()));
        PseudocodeFaults.Fault fault = PseudocodeFaults.inject(algorithm.pseudocode(), r);
        if (fault == null || fault.lines().size() < 4) {
            return null;
        }
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < fault.lines().size(); i++) {
            if (i != fault.lineIndex()) {
                others.add(i);
            }
        }
        Collections.shuffle(others, r);
        List<Integer> shown = new ArrayList<>(others.subList(0, 3));
        shown.add(fault.lineIndex());
        Collections.shuffle(shown, r);
        List<String> options = new ArrayList<>();
        for (int index : shown) {
            options.add("Line " + (index + 1) + ": " + fault.lines().get(index).trim());
        }
        StringBuilder listing = new StringBuilder();
        for (int i = 0; i < fault.lines().size(); i++) {
            listing.append(i == 0 ? "" : "\n").append(i + 1).append("  ").append(fault.lines().get(i));
        }
        String prompt = "One line of the " + algorithm.name() + " pseudocode was changed so that it is wrong. Which line is it?";
        String why = "Line " + (fault.lineIndex() + 1) + " should read: " + fault.original().trim() + ". As written, "
                + fault.why() + ".";
        return new Question(QuestionKind.FIX_PSEUDOCODE, level, prompt, new com.graphlab.engine.input.TextInput(listing.toString()),
                -1, -1, options, shown.indexOf(fault.lineIndex()), why, null, algorithm.id(), null);
    }

    // ---- Fill the table --------------------------------------------------------------

    private static Question fillTable(Level level, Random r) {
        // MS12: three in four tables are one of the new dynamic-programming algorithms; the rest stay the knapsack table
        int which = r.nextInt(4);
        return which == 3 ? fillKnapsackTable(level, r) : DpTables.create(which, level, r);
    }

    private static Question fillKnapsackTable(Level level, Random r) {
        int n = switch (level) {
            case EASY -> 3;
            case MEDIUM -> 4;
            case HARD -> 5;
        };
        int capacity = 5 + level.ordinal() * 2;
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new Item(1 + r.nextInt(Math.max(2, capacity / 2)), 2 + r.nextInt(12)));
        }
        int[][] dp = new int[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            Item item = items.get(i - 1);
            for (int w = 0; w <= capacity; w++) {
                dp[i][w] = dp[i - 1][w];
                if (item.weight() <= w) {
                    dp[i][w] = Math.max(dp[i][w], dp[i - 1][w - item.weight()] + item.value());
                }
            }
        }
        KnapsackInput input = new KnapsackInput(items, capacity);
        double engine = Algorithms.byId("knapsack-01").run(input, Params.none()).result().value();
        if (Math.rint(engine) != dp[n][capacity]) {
            return null;   // the table and the engine must agree
        }
        int blanksWanted = 2 + level.ordinal();
        boolean[][] blank = new boolean[n + 1][capacity + 1];
        List<int[]> blanks = new ArrayList<>();
        List<int[]> cells = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            for (int w = 1; w <= capacity; w++) {
                cells.add(new int[] {i, w});
            }
        }
        Collections.shuffle(cells, r);
        for (int[] cell : cells) {
            if (blanks.size() == blanksWanted) {
                break;
            }
            int i = cell[0];
            int w = cell[1];
            if (!canBlank(blank, items, i, w, n, capacity)) {
                continue;
            }
            blank[i][w] = true;
            blanks.add(cell);
        }
        if (blanks.size() < blanksWanted) {
            return null;
        }
        List<String> shown = new ArrayList<>();
        List<Integer> solution = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            for (int w = 0; w <= capacity; w++) {
                if (blank[i][w]) {
                    shown.add("");
                    solution.add(dp[i][w]);
                } else {
                    shown.add(Integer.toString(dp[i][w]));
                }
            }
        }
        String answer = solution.toString().replace("[", "").replace("]", "") + " (in the order of the blank cells, row by row)";
        String prompt = "This is the 0/1 knapsack table: a cell is the best value using the first i items with room w. "
                + "Each cell is the larger of two numbers: the cell above it (skip the item), or the item's value plus the cell "
                + "above and to the left by the item's weight (take it, if it fits). Type the " + blanksWanted + " blank cells.";
        String why = "Every blank follows the rule: the larger of \"skip\" (the cell above) and \"take\" (value plus the cell up and left). "
                + "The right values are " + answer + ". The bottom right cell, " + dp[n][capacity] + ", is the best value, as the engine finds.";
        Task task = new Task(Interaction.TABLE, shown, solution, 0, capacity + 1, answer);
        return taskQuestion(QuestionKind.FILL_TABLE, level, prompt, input, -1, -1, why, null, "knapsack-01", task);
    }

    /** A blank must be computable from cells that are shown, and no shown cell may rest on it. */
    private static boolean canBlank(boolean[][] blank, List<Item> items, int i, int w, int n, int capacity) {
        Item item = items.get(i - 1);
        if (blank[i - 1][w] || (item.weight() <= w && blank[i - 1][w - item.weight()])) {
            return false;
        }
        if (i < n) {
            Item below = items.get(i);
            if (blank[i + 1][w] || (w + below.weight() <= capacity && blank[i + 1][w + below.weight()])) {
                return false;
            }
        }
        return true;
    }

    // ---- Path pick -------------------------------------------------------------------

    private static Question pathPick(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        int start = start(level, r, g);
        int end = other(r, g, start);
        Result res = Algorithms.byId("dijkstra").run(g, Params.from(start)).result();
        if (!res.distances().containsKey(end)) {
            return null;
        }
        double best = res.distances().get(end);
        List<Integer> edges = new ArrayList<>();
        List<Integer> nodes = new ArrayList<>();
        int at = end;
        nodes.add(at);
        while (at != start) {
            Integer edgeId = res.parentEdge().get(at);
            if (edgeId == null) {
                return null;
            }
            edges.add(edgeId);
            Edge e = g.edge(edgeId);
            at = e.from() == at ? e.to() : e.from();
            nodes.add(at);
        }
        Collections.reverse(nodes);
        if (edges.size() < 2 || countShortest(g, start, end, best) != 1) {
            return null;   // one clear answer, and not just the direct edge
        }
        String route = route(g, nodes);
        String prompt = "Draw the shortest route from " + g.nodeName(start) + " to " + g.nodeName(end)
                + ". Tap the nodes one after the other, starting at " + g.nodeName(start) + ". Tap the last node again to take it back.";
        Task task = new Task(Interaction.PATH, List.of(), edges, 0, 0, route + " (total " + Choices.number(best) + ")");
        String why = "Dijkstra finds one shortest route: " + route + ", total " + Choices.number(best)
                + ". Every other route is longer.";
        return taskQuestion(QuestionKind.PATH_PICK, level, prompt, new GraphInput(g), start, end, why, null, "dijkstra", task);
    }

    /** How many simple routes from start to end have exactly this total weight. */
    private static int countShortest(Graph g, int start, int end, double total) {
        int[] count = new int[1];
        walk(g, start, end, total, 0, new boolean[g.nodeCount()], count);
        return count[0];
    }

    private static void walk(Graph g, int at, int end, double total, double sum, boolean[] seen, int[] count) {
        if (sum > total + 1e-9) {
            return;
        }
        if (at == end) {
            if (Math.abs(sum - total) < 1e-9) {
                count[0]++;
            }
            return;
        }
        seen[at] = true;
        for (Arc arc : g.arcsFrom(at)) {
            if (!seen[arc.to()]) {
                walk(g, arc.to(), end, total, sum + arc.weight(), seen, count);
            }
        }
        seen[at] = false;
    }

    // =============================================================================
    // Search order: BFS visit order, DFS discovery order, DFS finishing order
    // =============================================================================

    private static Question order(QuestionKind kind, Level level, Random r, boolean bfs, boolean finish) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), false);
        int start = start(level, r, g);
        Result res = run(kind, g, Params.from(start));
        List<Integer> correct = finish ? res.finishOrder() : res.visitOrder();
        if (correct.size() != g.nodeCount()) {
            return null;
        }
        List<List<Integer>> clever = new ArrayList<>();
        if (bfs) {
            clever.add(Algorithms.byId("dfs").run(g, Params.from(start)).result().visitOrder());
            clever.add(bfsLastNeighbourFirst(g, start));
        } else if (finish) {
            clever.add(res.visitOrder());
            List<Integer> reversed = new ArrayList<>(correct);
            Collections.reverse(reversed);
            clever.add(reversed);
        } else {
            clever.add(Algorithms.byId("bfs").run(g, Params.from(start)).result().visitOrder());
        }
        List<String> wrong = new ArrayList<>();
        for (List<Integer> o : Choices.wrongOrders(r, correct, clever)) {
            wrong.add(names(g, o));
        }
        String right = names(g, correct);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String s = g.nodeName(start);
        String prompt;
        String why;
        if (bfs) {
            prompt = "Run breadth-first search (BFS) from " + s + ". When there is a choice, take the neighbour that comes "
                    + "first in the alphabet. In which order are the nodes visited?";
            why = "BFS visits every node one edge away before any node two edges away. " + layers(g, res)
                    + " Inside a layer the nodes appear in the order they were found.";
        } else if (finish) {
            prompt = "Run depth-first search (DFS) from " + s + ". Always go to the neighbour that comes first in the "
                    + "alphabet and has not been seen. In which order does DFS finish with the nodes? A node is "
                    + "finished when everything reachable from it has been discovered.";
            why = "DFS finishes a node only after all the nodes it opened are finished, so the nodes that were "
                    + "discovered last finish first, and " + s + " finishes last. Discovery order was "
                    + names(g, res.visitOrder()) + ".";
        } else {
            prompt = "Run depth-first search (DFS) from " + s + ". Always go to the neighbour that comes first in the "
                    + "alphabet and has not been seen. In which order are the nodes discovered?";
            why = "DFS goes as deep as it can along one path, and backs up only when every neighbour has been seen. "
                    + "Taking the alphabetical neighbour each time gives " + right + ".";
        }
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(kind, level, prompt, new GraphInput(g), start, -1, c, why);
    }

    /** "At 0 edges: A. At 1 edge: B, C. ..." from the hop counts BFS stored. */
    private static String layers(Graph g, Result res) {
        StringBuilder out = new StringBuilder();
        int layer = 0;
        while (true) {
            List<Integer> in = new ArrayList<>();
            for (int node : res.visitOrder()) {
                if (Math.round(res.distances().get(node)) == layer) {
                    in.add(node);
                }
            }
            if (in.isEmpty()) {
                break;
            }
            out.append(layer == 0 ? "Start: " : "After " + layer + (layer == 1 ? " edge: " : " edges: "))
                    .append(names(g, in)).append(". ");
            layer++;
        }
        return out.toString().trim();
    }

    private static List<Integer> bfsLastNeighbourFirst(Graph g, int start) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            order.add(u);
            List<Arc> arcs = new ArrayList<>(g.arcsFrom(u));
            Collections.reverse(arcs);
            for (Arc arc : arcs) {
                if (seen.add(arc.to())) {
                    queue.add(arc.to());
                }
            }
        }
        return order;
    }

    // =============================================================================
    // Shortest paths
    // =============================================================================

    private static Question dijkstra(Level level, Random r) {
        boolean directed = level == Level.HARD;
        Graph g = graph(level, r, directed, false, 1, level.maxWeight(), false);
        int start = directed ? 0 : start(level, r, g);
        int end = other(r, g, start);
        Result res = run(QuestionKind.DIJKSTRA_DISTANCE, g, Params.from(start));
        Double distance = res.distances().get(end);
        if (distance == null || Double.isInfinite(distance)) {
            return null;
        }
        Result fewest = Algorithms.byId("bfs").run(g, Params.from(start)).result();
        double fewestEdgesLength = treeLength(g, fewest, end);
        List<String> wrong = Choices.wrongNumbers(r, distance, List.of(fewestEdgesLength), 1, 6);
        String right = Choices.number(distance);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String s = g.nodeName(start);
        String t = g.nodeName(end);
        String why = "The shortest route from " + s + " to " + t + " is " + route(g, res.pathTo(g, end)) + ", with a total "
                + "weight of " + right + ".";
        List<Integer> fewestPath = fewest.pathTo(g, end);
        if (fewestEdgesLength != distance) {
            why += " The route with the fewest edges, " + route(g, fewestPath) + ", costs "
                    + Choices.number(fewestEdgesLength) + ", which is more. BFS counts edges, not weights.";
        }
        String prompt = "What is the length of the shortest path from " + s + " to " + t + "? The numbers on the edges are weights"
                + (directed ? ", and edges can only be used in the direction of the arrow." : ".");
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.DIJKSTRA_DISTANCE, level, prompt, new GraphInput(g), start, end, c, why);
    }

    private static Question bellmanDistance(Level level, Random r) {
        int lowest = -(2 + level.ordinal());
        Graph g = graph(level, r, true, true, lowest, Math.min(level.maxWeight(), 9), false);
        boolean anyNegative = false;
        for (Edge e : g.edges()) {
            anyNegative |= e.weight() < 0;
        }
        if (!anyNegative) {
            return null;
        }
        int end = other(r, g, 0);
        Result res = run(QuestionKind.BELLMAN_DISTANCE, g, Params.from(0));
        if (res.negativeCycle() || !res.distances().containsKey(end) || Double.isInfinite(res.distances().get(end))) {
            return null;
        }
        double distance = res.distances().get(end);
        double asIfPositive = Algorithms.byId("dijkstra").run(RandomGraphs.absolute(g), Params.from(0))
                .result().distances().get(end);
        List<String> wrong = Choices.wrongNumbers(r, distance, List.of(asIfPositive), -12, 6);
        String right = Choices.number(distance);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String s = g.nodeName(0);
        String t = g.nodeName(end);
        String why = "Bellman-Ford handles negative edges. The best route from " + s + " to " + t + " is "
                + route(g, res.pathTo(g, end)) + ": " + routeSum(g, res, end) + " = " + right
                + ". A negative edge can make a longer route cheaper, so it is wrong to ignore the minus signs"
                + (asIfPositive != distance ? " (that would give " + Choices.number(asIfPositive) + ")." : ".");
        String prompt = "Some edges have negative weights, and edges can only be used in the direction of the arrow. "
                + "What is the shortest distance from " + s + " to " + t + "?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.BELLMAN_DISTANCE, level, prompt, new GraphInput(g), 0, end, c, why);
    }

    private static Question negativeCycle(Level level, Random r) {
        int n = nodeCount(level, r);
        Graph forward = RandomGraphs.connected(r, n, extra(level, r), true, true, 1, Math.max(3, level.maxWeight() / 3), false);
        // one forward edge may get a small negative weight, so "there is a negative edge" does not give the answer away
        if (r.nextBoolean()) {
            List<Edge> edges = new ArrayList<>(forward.edges());
            int i = r.nextInt(edges.size());
            Edge e = edges.get(i);
            edges.set(i, new Edge(e.id(), e.from(), e.to(), -(1 + r.nextInt(2))));
            forward = new Graph(true, forward.nodes(), edges);
        }
        // add one edge that goes backwards along an existing route a -> ... -> c, so exactly the cycles through it exist
        List<int[]> candidates = new ArrayList<>();
        for (int a = 0; a < n; a++) {
            Set<Integer> reach = reachable(forward, a);
            for (int c : reach) {
                if (c > a) {
                    candidates.add(new int[] {a, c});
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int[] pair = candidates.get(r.nextInt(candidates.size()));
        int a = pair[0];
        int c = pair[1];
        Result forwardRun = run(QuestionKind.NEGATIVE_CYCLE, forward, Params.from(a));
        double d = forwardRun.distances().get(c);
        boolean wantYes = r.nextBoolean();
        double back = wantYes ? -d - 1 : -d + 1 + r.nextInt(3);
        Graph g = RandomGraphs.withEdge(forward, c, a, back);
        Result res = run(QuestionKind.NEGATIVE_CYCLE, g, Params.from(0));
        if (res.negativeCycle() != wantYes) {
            return null;
        }
        List<Integer> loop = new ArrayList<>(forwardRun.pathTo(forward, c));
        loop.add(a);
        double total = d + back;
        String lap = route(g, loop) + " has total weight " + routeSum(forward, forwardRun, c) + " + (" + Choices.number(back)
                + ") = " + Choices.number(total);
        String why = wantYes
                ? "Yes. The loop " + lap + ", which is below zero. Going round it again and again makes every distance "
                        + "smaller each time, so Bellman-Ford finds the cycle in its last pass."
                : "No. Every loop uses the edge " + g.nodeName(c) + ARROW + g.nodeName(a) + ". The cheapest one, "
                        + lap + ", is not below zero, so going round never helps and the distances settle.";
        String prompt = "Bellman-Ford starts at " + g.nodeName(0) + ". Is there a cycle whose weights add up to less than zero "
                + "that can be reached from " + g.nodeName(0) + "? (Edges can only be used in the direction of the arrow.)";
        Choices ch = Choices.fixed(List.of("Yes, there is a negative cycle", "No, there is no negative cycle"), wantYes ? 0 : 1);
        return make(QuestionKind.NEGATIVE_CYCLE, level, prompt, new GraphInput(g), 0, -1, ch, why);
    }

    private static Question floyd(Level level, Random r) {
        boolean directed = level == Level.HARD;
        Graph g = graph(level, r, directed, false, 1, level.maxWeight(), false);
        int from = r.nextInt(g.nodeCount());
        int to = other(r, g, from);
        Result res = run(QuestionKind.FLOYD_ENTRY, g, Params.none());
        double entry = res.matrix().get(from).get(to);
        boolean none = Double.isInfinite(entry);
        double reverse = res.matrix().get(to).get(from);
        double direct = Double.NaN;
        for (Edge e : g.edges()) {
            boolean joins = e.from() == from && e.to() == to || !directed && e.from() == to && e.to() == from;
            if (joins && (Double.isNaN(direct) || e.weight() < direct)) {
                direct = e.weight();
            }
        }
        List<Double> clever = new ArrayList<>();
        clever.add(reverse);
        clever.add(direct);
        double base = entry;
        if (none) {
            List<Double> finite = new ArrayList<>();
            for (List<Double> row : res.matrix()) {
                for (double v : row) {
                    if (Double.isFinite(v) && v > 0) {
                        finite.add(v);
                    }
                }
            }
            if (finite.isEmpty()) {
                return null;
            }
            Collections.sort(finite);
            base = finite.get(finite.size() / 2);
        }
        List<String> wrong = new ArrayList<>(Choices.wrongNumbers(r, base, clever, 1, 6));
        String right = none ? "No path" : Choices.number(entry);
        if (directed && !none && wrong.size() > 3) {
            wrong.add(0, "No path");
        }
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String f = g.nodeName(from);
        String t = g.nodeName(to);
        String why;
        if (none) {
            why = "There is no route from " + f + " to " + t + ": following the arrows never leads there, so the table "
                    + "entry stays at infinity. " + (Double.isFinite(reverse) ? "(The other way round, " + t + " to " + f + ", the distance is "
                    + Choices.number(reverse) + ".)" : "");
        } else {
            Result from0 = Algorithms.byId("dijkstra").run(g, Params.from(from)).result();
            why = "Floyd-Warshall tries every node as a stop-over and keeps the shortest result. The shortest route from " + f
                    + " to " + t + " is " + route(g, from0.pathTo(g, to)) + ", length " + right + "."
                    + (directed ? " Row " + f + " and column " + t + " read from the first node to the second." : "");
        }
        String prompt = "Floyd-Warshall fills a table with the shortest distance from every node to every other node. What is the "
                + "entry in row " + f + ", column " + t + " (the shortest distance from " + f + " to " + t + ")?"
                + (directed ? " Edges can only be used in the direction of the arrow." : "");
        Choices c = Choices.of(r, right, wrong.stream().map(String::trim).toList(), WANTED_OPTIONS);
        return make(QuestionKind.FLOYD_ENTRY, level, prompt, new GraphInput(g), from, to, c, why.trim());
    }

    // =============================================================================
    // Spanning trees
    // =============================================================================

    private static Question kruskalWeight(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        Result res = run(QuestionKind.KRUSKAL_WEIGHT, g, Params.none());
        List<Double> sorted = sortedWeights(g);
        double cheapest = 0;
        for (int i = 0; i < g.nodeCount() - 1; i++) {
            cheapest += sorted.get(i);
        }
        List<String> wrong = Choices.wrongNumbers(r, res.totalWeight(), List.of(cheapest), 1, 8);
        String right = Choices.number(res.totalWeight());
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        StringBuilder tree = new StringBuilder();
        for (int id : res.treeEdgeIds()) {
            Edge e = g.edge(id);
            tree.append(tree.length() == 0 ? "" : ", ").append(edgeName(g, e)).append(" (").append(Choices.number(e.weight())).append(')');
        }
        String why = "Kruskal looks at the edges from lightest to heaviest and skips any edge that would close a cycle. It keeps "
                + tree + ", total " + right + "."
                + (cheapest != res.totalWeight() ? " Just adding the " + (g.nodeCount() - 1) + " lightest edges would give "
                        + Choices.number(cheapest) + ", but one of them closes a cycle." : "");
        String prompt = "All edge weights are different. What is the total weight of a minimum spanning tree of this graph?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.KRUSKAL_WEIGHT, level, prompt, new GraphInput(g), -1, -1, c, why);
    }

    private static Question kruskalLastEdge(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        Result res = run(QuestionKind.KRUSKAL_LAST_EDGE, g, Params.none());
        if (res.treeEdgeIds().size() != g.nodeCount() - 1) {
            return null;
        }
        Edge last = g.edge(res.treeEdgeIds().get(res.treeEdgeIds().size() - 1));
        Edge heaviest = null;
        for (Edge e : g.edges()) {
            if (heaviest == null || e.weight() > heaviest.weight()) {
                heaviest = e;
            }
        }
        List<Edge> others = new ArrayList<>(g.edges());
        Collections.shuffle(others, r);
        List<String> wrong = new ArrayList<>();
        wrong.add(edgeName(g, heaviest));
        for (Edge e : others) {
            wrong.add(edgeName(g, e));
        }
        String right = edgeName(g, last);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String why = "Kruskal considers the edges from lightest to heaviest and adds an edge unless it closes a cycle. The last "
                + "edge it adds is " + right + " (weight " + Choices.number(last.weight()) + ")."
                + (heaviest.id() != last.id() && !res.treeEdgeIds().contains(heaviest.id())
                        ? " The heaviest edge, " + edgeName(g, heaviest) + " (" + Choices.number(heaviest.weight())
                                + "), is never added: it would close a cycle." : "");
        String prompt = "All edge weights are different. Kruskal's algorithm adds edges to the tree from the lightest to the heaviest, "
                + "skipping any edge that would close a cycle. Which edge does it add last?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.KRUSKAL_LAST_EDGE, level, prompt, new GraphInput(g), -1, -1, c, why);
    }

    private static Question primThirdEdge(Level level, Random r) {
        Graph g = graph(level, r, false, false, 1, level.maxWeight(), true);
        int start = start(level, r, g);
        Result res = run(QuestionKind.PRIM_THIRD_EDGE, g, Params.from(start));
        if (res.treeEdgeIds().size() < 3) {
            return null;
        }
        Edge third = g.edge(res.treeEdgeIds().get(2));
        Result kruskal = Algorithms.byId("kruskal").run(g, Params.none()).result();
        List<Edge> byWeight = new ArrayList<>(g.edges());
        byWeight.sort(Comparator.comparingDouble(Edge::weight));
        List<String> wrong = new ArrayList<>();
        wrong.add(edgeName(g, g.edge(kruskal.treeEdgeIds().get(2))));
        wrong.add(edgeName(g, byWeight.get(2)));
        List<Edge> others = new ArrayList<>(g.edges());
        Collections.shuffle(others, r);
        for (Edge e : others) {
            wrong.add(edgeName(g, e));
        }
        String right = edgeName(g, third);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            Edge e = g.edge(res.treeEdgeIds().get(i));
            steps.append(i == 0 ? "" : ", ").append(edgeName(g, e)).append(" (").append(Choices.number(e.weight())).append(')');
        }
        String s = g.nodeName(start);
        String why = "Prim grows one tree from " + s + ". Each time it adds the lightest edge that leaves the tree and reaches a new "
                + "node. The first three edges are " + steps + ", so the third is " + right + ". "
                + "Kruskal would work differently: it looks at the whole graph, not only at edges next to the tree.";
        String prompt = "All edge weights are different. Prim's algorithm starts at " + s + " and always adds the lightest edge "
                + "that joins the tree to a node outside it. Which edge does it add third?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.PRIM_THIRD_EDGE, level, prompt, new GraphInput(g), start, -1, c, why);
    }

    // =============================================================================
    // Flow and colouring
    // =============================================================================

    private static Question maxFlow(Level level, Random r) {
        int cap = level.maxWeight() / 2 + 1;
        Graph g = RandomGraphs.connected(r, nodeCount(level, r), extra(level, r) + 1, true, false, 1, cap, false);
        int sink = g.nodeCount() - 1;
        Result res = run(QuestionKind.MAX_FLOW, g, new Params(0, 0L, sink));
        double flow = res.value();
        double outOfSource = 0;
        double intoSink = 0;
        for (Edge e : g.edges()) {
            if (e.from() == 0) {
                outOfSource += e.weight();
            }
            if (e.to() == sink) {
                intoSink += e.weight();
            }
        }
        List<String> wrong = Choices.wrongNumbers(r, flow, List.of(outOfSource, intoSink), 1, 5);
        String right = Choices.number(flow);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        StringBuilder cut = new StringBuilder();
        double cutTotal = 0;
        for (int id : res.cutEdgeIds()) {
            Edge e = g.edge(id);
            cut.append(cut.length() == 0 ? "" : ", ").append(g.nodeName(e.from())).append(ARROW)
                    .append(g.nodeName(e.to())).append(" (").append(Choices.number(e.weight())).append(')');
            cutTotal += e.weight();
        }
        String why = "The largest flow is " + right + "."
                + (!res.cutEdgeIds().isEmpty() && cutTotal == flow
                        ? " No more can pass because the edges " + cut + " carry at most " + Choices.number(cutTotal)
                                + " in total, and all flow has to cross them (a minimum cut)."
                        : "")
                + " Adding up the edges next to the source (" + Choices.number(outOfSource) + ") or the sink ("
                + Choices.number(intoSink) + ") only gives an upper limit.";
        String prompt = "The numbers on the edges are capacities, and flow can only go in the direction of the arrow. What is the "
                + "maximum flow from the source " + g.nodeName(0) + " to the sink " + g.nodeName(sink) + "?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.MAX_FLOW, level, prompt, new GraphInput(g), 0, sink, c, why);
    }

    private static Question colours(Level level, Random r) {
        int n = nodeCount(level, r);
        Graph g = RandomGraphs.connected(r, n, level.minExtraEdges() + n / 2 + r.nextInt(3), false, false, 1, 1, false);
        Result res = run(QuestionKind.COLOURS_NEEDED, g, Params.none());
        int k = (int) Math.round(res.value());
        int maxDegree = 0;
        for (int i = 0; i < n; i++) {
            maxDegree = Math.max(maxDegree, g.arcsFrom(i).size());
        }
        List<String> wrong = Choices.wrongNumbers(r, k, List.of((double) maxDegree + 1, (double) maxDegree), 1, 3);
        String right = Integer.toString(k);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        StringBuilder sample = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sample.append(i == 0 ? "" : ", ").append(g.nodeName(i)).append('=').append(res.colours().get(i));
        }
        String why = k + (k == 1 ? " colour is" : " colours are") + " the fewest that work. One valid colouring is " + sample
                + ". With one colour fewer, every attempt ends in a clash between two neighbours.";
        String prompt = "Colour the nodes so that two nodes joined by an edge never have the same colour. What is the smallest "
                + "number of colours that is enough?";
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(QuestionKind.COLOURS_NEEDED, level, prompt, new GraphInput(g), -1, -1, c, why);
    }

    // =============================================================================
    // Lists and knapsack
    // =============================================================================

    private static Question comparisons(QuestionKind kind, Level level, Random r) {
        List<Integer> pool = new ArrayList<>();
        for (int v = 1; v <= 99; v++) {
            pool.add(v);
        }
        Collections.shuffle(pool, r);
        List<Integer> values = new ArrayList<>(pool.subList(0, level.listSize()));
        ArrayInput input = new ArrayInput(values);
        Result res = run(kind, input, Params.none());
        double count = res.value();
        int n = values.size();
        boolean merge = kind == QuestionKind.MERGE_COMPARISONS;
        double upper = merge ? n * Math.ceil(Math.log(n) / Math.log(2)) : n * (n - 1) / 2.0;
        List<String> wrong = Choices.wrongNumbers(r, count, List.of(upper), 1, 5);
        String right = Choices.number(count);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        String list = values.toString().replace("[", "").replace("]", "");
        String prompt;
        String why;
        if (merge) {
            prompt = "Merge sort sorts the list " + list + ". It splits a list in the middle (the left half is one longer when the "
                    + "length is odd), sorts both halves the same way, then merges them by comparing the front numbers of the two "
                    + "halves. How many comparisons does it make in total?";
            why = "Every merge compares the front numbers of two sorted halves and stops comparing as soon as one half is used up, "
                    + "so merging halves of sizes p and q takes at most p + q - 1 comparisons. Adding up all the merges gives "
                    + right + " (not the upper limit " + Choices.number(upper) + ").";
        } else {
            prompt = "Quick sort sorts the list " + list + ". It always takes the last number of a part as the pivot and compares every "
                    + "other number of that part with it once. Parts of one number need no comparison. How many comparisons does it "
                    + "make in total?";
            why = "A part of m numbers needs m - 1 comparisons, one for each number against the pivot. Adding up the parts as quick "
                    + "sort splits them gives " + right + ". The worst case for " + n + " numbers would be "
                    + Choices.number(upper) + ", when the pivot is always the smallest or the largest.";
        }
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(kind, level, prompt, input, -1, -1, c, why);
    }

    private static Question knapsack(QuestionKind kind, Level level, Random r) {
        int capacity = level.minCapacity() + r.nextInt(level.maxCapacity() - level.minCapacity() + 1);
        int count = level.minItems() + r.nextInt(level.maxItems() - level.minItems() + 1);
        List<Item> items = new ArrayList<>();
        int totalWeight = 0;
        for (int i = 0; i < count; i++) {
            int weight = 2 + r.nextInt(Math.max(2, capacity / 2 - 1));
            int value = 4 + r.nextInt(21 + 10 * level.multiplier());
            items.add(new Item(weight, value));
            totalWeight += weight;
        }
        if (totalWeight <= capacity) {
            return null;   // everything fits: no choice to make
        }
        KnapsackInput input = new KnapsackInput(items, capacity);
        boolean fractional = kind == QuestionKind.FRACTIONAL_VALUE;
        Result res = run(kind, input, Params.none());
        Result other = Algorithms.byId(fractional ? "knapsack-01" : "fractional-knapsack").run(input, Params.none()).result();
        double wholeByRatio = greedyWhole(items, capacity, true);
        double wholeByValue = greedyWhole(items, capacity, false);
        double value = res.value();
        List<Double> clever = new ArrayList<>();
        String right = Choices.number(value);
        String prompt;
        String why;
        if (fractional) {
            boolean split = false;
            for (double a : res.amounts()) {
                split |= a > 1e-9 && a < 1 - 1e-9;
            }
            if (!split) {
                return null;   // no item is cut: the question would be the same as plain greedy
            }
            clever.add(other.value());
            clever.add(wholeByRatio);
            clever.add(Math.floor(value));
            prompt = "The bag holds " + capacity + ". Each item has a weight and a value, and an item may be cut: you can take any "
                    + "fraction of it and get the same fraction of its value. What is the most value the bag can hold? (Two decimals "
                    + "at most.)";
            why = "Take items in order of value per weight, best first, and cut the last one to fill the bag exactly. "
                    + fractionalSteps(items, res) + " Total value " + right + ".";
        } else {
            clever.add(Math.floor(other.value()));
            clever.add(wholeByRatio);
            clever.add(wholeByValue);
            prompt = "The bag holds " + capacity + ". Each item has a weight and a value, and goes in whole or stays out. What is the "
                    + "greatest total value that fits?";
            StringBuilder taken = new StringBuilder();
            int weight = 0;
            for (int i : res.chosen()) {
                taken.append(taken.length() == 0 ? "" : ", ").append(i + 1);
                weight += items.get(i).weight();
            }
            why = "The best choice is item" + (res.chosen().size() == 1 ? " " : "s ") + taken + ": total weight " + weight + " of "
                    + capacity + ", total value " + right + ". "
                    + (wholeByRatio < value
                            ? "Taking the items with the best value per weight first gives only " + Choices.number(wholeByRatio)
                                    + ", so that greedy rule fails when items cannot be cut."
                            : "Taking the items with the best value per weight first happens to give the same here, but that greedy "
                                    + "rule is not always best when items cannot be cut.");
        }
        List<String> wrong = Choices.wrongNumbers(r, value, clever, 1, 8);
        if (!Choices.enough(right, wrong, WANTED_OPTIONS)) {
            return null;
        }
        Choices c = Choices.of(r, right, wrong, WANTED_OPTIONS);
        return make(kind, level, prompt, input, -1, -1, c, why);
    }

    /** Takes whole items in order of value per weight (or of value), skipping any that do not fit. */
    private static double greedyWhole(List<Item> items, int capacity, boolean byRatio) {
        List<Item> order = new ArrayList<>(items);
        order.sort(byRatio
                ? Comparator.comparingDouble((Item i) -> -(double) i.value() / i.weight())
                : Comparator.comparingInt((Item i) -> -i.value()));
        int room = capacity;
        double total = 0;
        for (Item item : order) {
            if (item.weight() <= room) {
                room -= item.weight();
                total += item.value();
            }
        }
        return total;
    }

    private static String fractionalSteps(List<Item> items, Result res) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> -(double) items.get(i).value() / items.get(i).weight()));
        StringBuilder out = new StringBuilder("Order: ");
        StringBuilder taken = new StringBuilder();
        for (int i : order) {
            out.append(out.length() == 7 ? "" : ", ").append("item ").append(i + 1).append(" (")
                    .append(Choices.number(Math.round((double) items.get(i).value() / items.get(i).weight() * 100) / 100.0)).append(')');
            double a = res.amounts().get(i);
            if (a > 1e-9) {
                taken.append(taken.length() == 0 ? "" : ", ")
                        .append(a >= 1 - 1e-9 ? "all of item " + (i + 1) : Choices.number(Math.round(a * 100) / 100.0) + " of item " + (i + 1));
            }
        }
        return out + ". Taken: " + taken + ".";
    }

    // =============================================================================
    // Helpers
    // =============================================================================

    private static Question make(QuestionKind kind, Level level, String prompt, Input input, int source, int target,
            Choices choices, String explanation) {
        return new Question(kind, level, prompt, input, source, target, choices.options, choices.correctIndex, explanation);
    }

    private static Result run(QuestionKind kind, Graph g, Params params) {
        return kind.algorithm().run(g, params).result();
    }

    private static Result run(QuestionKind kind, Input input, Params params) {
        return kind.algorithm().run(input, params).result();
    }

    private static int nodeCount(Level level, Random r) {
        return level.minNodes() + r.nextInt(level.maxNodes() - level.minNodes() + 1);
    }

    private static int extra(Level level, Random r) {
        return level.minExtraEdges() + r.nextInt(level.maxExtraEdges() - level.minExtraEdges() + 1);
    }

    private static Graph graph(Level level, Random r, boolean directed, boolean forwardOnly, int minWeight, int maxWeight,
            boolean distinct) {
        return RandomGraphs.connected(r, nodeCount(level, r), extra(level, r), directed, forwardOnly, minWeight, maxWeight, distinct);
    }

    /** Easy questions always start at A; the others at a random node. */
    private static int start(Level level, Random r, Graph g) {
        return level == Level.EASY ? 0 : r.nextInt(g.nodeCount());
    }

    private static int other(Random r, Graph g, int not) {
        int pick = r.nextInt(g.nodeCount() - 1);
        return pick >= not ? pick + 1 : pick;
    }

    private static String names(Graph g, List<Integer> ids) {
        StringBuilder out = new StringBuilder();
        for (int id : ids) {
            out.append(out.length() == 0 ? "" : ", ").append(g.nodeName(id));
        }
        return out.toString();
    }

    private static String route(Graph g, List<Integer> ids) {
        StringBuilder out = new StringBuilder();
        for (int id : ids) {
            out.append(out.length() == 0 ? "" : ARROW).append(g.nodeName(id));
        }
        return out.toString();
    }

    private static String edgeName(Graph g, Edge e) {
        return g.nodeName(e.from()) + "-" + g.nodeName(e.to());
    }

    private static List<Double> sortedWeights(Graph g) {
        List<Double> out = new ArrayList<>();
        for (Edge e : g.edges()) {
            out.add(e.weight());
        }
        Collections.sort(out);
        return out;
    }

    /** Length of the route a search tree gives from its root to {@code target}. */
    private static double treeLength(Graph g, Result res, int target) {
        double total = 0;
        int node = target;
        int guard = 0;
        while (res.parentEdge().containsKey(node) && guard++ <= g.nodeCount()) {
            Edge e = g.edge(res.parentEdge().get(node));
            total += e.weight();
            node = e.to() == node ? e.from() : e.to();
        }
        return total;
    }

    /** "5 + (-3) + 2": the weights along the tree route to {@code target}, first edge first. */
    private static String routeSum(Graph g, Result res, int target) {
        List<Double> weights = new ArrayList<>();
        int node = target;
        int guard = 0;
        while (res.parentEdge().containsKey(node) && guard++ <= g.nodeCount()) {
            Edge e = g.edge(res.parentEdge().get(node));
            weights.add(e.weight());
            node = e.to() == node ? e.from() : e.to();
        }
        Collections.reverse(weights);
        StringBuilder out = new StringBuilder();
        for (double w : weights) {
            String text = w < 0 ? "(" + Choices.number(w) + ")" : Choices.number(w);
            out.append(out.length() == 0 ? "" : " + ").append(text);
        }
        return out.length() == 0 ? "0" : out.toString();
    }

    private static Set<Integer> reachable(Graph g, int from) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(from);
        seen.add(from);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (Arc arc : g.arcsFrom(u)) {
                if (seen.add(arc.to())) {
                    stack.push(arc.to());
                }
            }
        }
        seen.remove(from);
        return seen;
    }
}

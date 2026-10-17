package com.graphlab.engine.arena;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.AlgorithmCategory;
import java.util.ArrayList;
import java.util.List;

/**
 * The activities of the Arena. Each one asks a different question about one algorithm, and the
 * right answer always comes from running that algorithm in the engine. The order here is the
 * order the selector shows them: it follows the order of the algorithm picker.
 *
 * <p>Karger and randomized quick sort have no activity: their result depends on a random seed,
 * so a player cannot work the answer out by reasoning. They stay in the Lab.
 */
public enum QuestionKind {
    BFS_ORDER("bfs-order", "BFS visiting order", "bfs", InputKind.GRAPH, false,
            "In which order does breadth-first search visit the nodes?"),
    DFS_ORDER("dfs-order", "DFS visiting order", "dfs", InputKind.GRAPH, false,
            "In which order does depth-first search discover the nodes?"),
    DFS_FINISH("dfs-finish", "DFS finishing order", "dfs", InputKind.GRAPH, false,
            "In which order does depth-first search finish with the nodes?"),
    DIJKSTRA_DISTANCE("dijkstra-distance", "Shortest distance", "dijkstra", InputKind.GRAPH, true,
            "How far is it from the start to another node?"),
    BELLMAN_DISTANCE("bellman-distance", "Distance with negative edges", "bellman-ford", InputKind.GRAPH, true,
            "Find a shortest distance when some edges have negative weights."),
    NEGATIVE_CYCLE("negative-cycle", "Negative cycle", "bellman-ford", InputKind.GRAPH, true,
            "Does the graph have a negative cycle that can be reached from the start?"),
    FLOYD_ENTRY("floyd-entry", "All-pairs table", "floyd-warshall", InputKind.GRAPH, true,
            "Read one entry of the Floyd-Warshall table."),
    KRUSKAL_WEIGHT("kruskal-weight", "Minimum spanning tree weight", "kruskal", InputKind.GRAPH, true,
            "What is the total weight of the minimum spanning tree?"),
    KRUSKAL_LAST_EDGE("kruskal-last-edge", "Last edge of Kruskal", "kruskal", InputKind.GRAPH, true,
            "Which edge does Kruskal add last?"),
    PRIM_THIRD_EDGE("prim-third-edge", "Third edge of Prim", "prim", InputKind.GRAPH, true,
            "Which edge does Prim add third?"),
    MAX_FLOW("max-flow", "Maximum flow", "edmonds-karp", InputKind.GRAPH, true,
            "How much flow can the network carry from the source to the sink?"),
    FRACTIONAL_VALUE("fractional-value", "Fractional knapsack", "fractional-knapsack", InputKind.KNAPSACK, false,
            "How much value fits in the bag when items may be cut?"),
    COLOURS_NEEDED("colours-needed", "Graph colouring", "graph-colouring", InputKind.GRAPH, false,
            "How many colours are needed so no two neighbours match?"),
    MERGE_COMPARISONS("merge-comparisons", "Merge sort comparisons", "merge-sort", InputKind.ARRAY, false,
            "How many comparisons does merge sort make?"),
    QUICK_COMPARISONS("quick-comparisons", "Quick sort comparisons", "quick-sort", InputKind.ARRAY, false,
            "How many comparisons does quick sort make?"),
    KNAPSACK_VALUE("knapsack-value", "0/1 knapsack", "knapsack-01", InputKind.KNAPSACK, false,
            "What is the best value when every item goes in whole or stays out?"),
    /** Dijkstra has settled some nodes; which does it settle next? The answer is read from the engine's run. */
    NEXT_STEP("next-step", "Next step", "dijkstra", InputKind.GRAPH, true,
            "Dijkstra has settled some nodes. Which node does it settle next?"),
    /** A statement to judge, from the card bank (swipe right for true, left for false). */
    TRUE_FALSE("true-false", "True or false", null, InputKind.TEXT, false,
            "Swipe right for true, left for false."),
    /** A situation to choose the best algorithm for, from the card bank. */
    BEST_TOOL("best-tool", "Best tool", null, InputKind.TEXT, false,
            "Which algorithm fits the job?"),
    /** Put the next few steps of a real run in order; the steps are the engine's own trace messages. */
    ORDER_STEPS("order-steps", "Order the steps", null, InputKind.GRAPH, true,
            "Drag the steps of a real run into the order they happen."),
    /** Three belong together, one does not. */
    ODD_ONE_OUT("odd-one-out", "Odd one out", null, InputKind.TEXT, false,
            "Three of four belong together. Which one does not?"),
    /** A memory game: flip tiles to match each term with its meaning. */
    MATCH_PAIRS("match-pairs", "Match pairs", null, InputKind.TEXT, false,
            "Flip two tiles at a time to pair each term with its meaning."),
    /** Type a number worked out by the engine; close enough counts. */
    GUESS_NUMBER("guess-number", "Guess the number", null, InputKind.GRAPH, true,
            "Estimate a number from the graph. A close guess is right."),
    /** One line of real pseudocode has been broken; find it. */
    FIX_PSEUDOCODE("fix-pseudocode", "Fix the pseudocode", null, InputKind.TEXT, false,
            "One line of the pseudocode has a mistake. Which one?"),
    /** Fill the blank cells of a dynamic-programming table. */
    FILL_TABLE("fill-table", "Fill the table", null, InputKind.KNAPSACK, false,
            "Type the missing cells of a dynamic-programming table."),
    /** Draw the shortest route by tapping nodes; the line snaps to the graph's edges. */
    PATH_PICK("path-pick", "Path pick", null, InputKind.GRAPH, true,
            "Tap the nodes to draw the shortest route from start to end."),
    /** A log of a real Dijkstra run with one fault injected (MS17). */
    SPOT_BUG("spot-bug", "Spot the bug", null, InputKind.GRAPH, true,
            "A log of a real run has one wrong line. Find it."),
    /** Operation counts at growing sizes; which growth fits (MS17). */
    COMPLEXITY_GUESS("complexity-guess", "Complexity guess", null, InputKind.TEXT, false,
            "Read the counts at growing sizes and name the growth."),
    /** One edge changes; does the answer change (MS17). */
    WILL_CHANGE("will-change", "Will it change?", null, InputKind.GRAPH, true,
            "One edge changes. Does the answer change?"),
    /** An output is shown; which algorithm made it (MS17). */
    REVERSE_ENGINEER("reverse-engineer", "Reverse-engineer", null, InputKind.GRAPH, true,
            "Here is the output. Which algorithm made it?"),
    /** Bet on which of two algorithms gets there first (MS17). */
    RACE_BET("race-bet", "Race bet", null, InputKind.GRAPH, false,
            "Two searches race to one node. Place your bet."),
    /** Cut one edge of a flow network; which cut hurts the flow most (MS17). */
    CUT_NETWORK("cut-network", "Cut the network", null, InputKind.GRAPH, true,
            "Cut one edge so the maximum flow falls the most."),
    /** Change one edge so that Dijkstra gives a wrong distance; Bellman-Ford is the reference (Priority 4, O1). */
    BREAK_IT("break-it", "Break the algorithm", null, InputKind.GRAPH, true,
            "Make one edge negative so that Dijkstra gives a wrong distance."),
    /** A walker sees only the roads where it stands and always takes the cheapest; what does the trip cost (Priority 4, O2). */
    FOG_OF_WAR("fog-of-war", "Fog of war", null, InputKind.GRAPH, true,
            "You see only the roads where you stand. What does always taking the cheapest cost?");

    private final String id;
    private final String title;
    private final String algorithmId;
    private final InputKind inputKind;
    private final boolean showsWeights;
    private final String summary;

    QuestionKind(String id, String title, String algorithmId, InputKind inputKind, boolean showsWeights,
            String summary) {
        this.id = id;
        this.title = title;
        this.algorithmId = algorithmId;
        this.inputKind = inputKind;
        this.showsWeights = showsWeights;
        this.summary = summary;
    }

    /** Stable identifier, used in saved progress. Never change it once released. */
    public String id() {
        return id;
    }

    /** Name shown in the selector. */
    public String title() {
        return title;
    }

    /** One sentence for the selector card. */
    public String summary() {
        return summary;
    }

    /** The id of the engine algorithm that decides the right answer. */
    public String algorithmId() {
        return algorithmId;
    }

    /** The algorithm this activity is about, or null for the card activities, which cover many. */
    public Algorithm algorithm() {
        return algorithmId == null ? null : Algorithms.byId(algorithmId);
    }

    /** The algorithm family, or null for the card activities. */
    public AlgorithmCategory category() {
        return algorithmId == null ? null : algorithm().category();
    }

    /**
     * True for the activities that are not about one algorithm: the card activities and the MS11 activities that choose their
     * algorithm for each question. They are listed under "all topics", and the statistics use each question's own topic.
     */
    public boolean fromCards() {
        return algorithmId == null;
    }

    /** True for the activities whose content comes (wholly or partly) from {@code questions.json}. */
    public boolean usesCardBank() {
        return this == TRUE_FALSE || this == BEST_TOOL || this == ODD_ONE_OUT || this == MATCH_PAIRS;
    }

    /** How the player answers: a button, or one of the small tasks. */
    public Interaction interaction() {
        return switch (this) {
            case ORDER_STEPS -> Interaction.ORDER;
            case MATCH_PAIRS -> Interaction.MATCH;
            case GUESS_NUMBER -> Interaction.NUMBER;
            case FILL_TABLE -> Interaction.TABLE;
            case PATH_PICK -> Interaction.PATH;
            default -> Interaction.CHOICE;
        };
    }

    /**
     * The difficulty tag of the activity itself (not of the round's level): how much a learner has to know and do. Shown on the
     * selector card. Tags: EASY recognise, MEDIUM apply, HARD work it out.
     */
    public Level difficulty() {
        return switch (this) {
            case TRUE_FALSE, ODD_ONE_OUT, MATCH_PAIRS, BEST_TOOL, BFS_ORDER, DFS_ORDER, COLOURS_NEEDED,
                    FRACTIONAL_VALUE, ORDER_STEPS -> Level.EASY;
            case DFS_FINISH, DIJKSTRA_DISTANCE, KRUSKAL_WEIGHT, KRUSKAL_LAST_EDGE, PRIM_THIRD_EDGE, MAX_FLOW,
                    MERGE_COMPARISONS, QUICK_COMPARISONS, NEXT_STEP, GUESS_NUMBER, FIX_PSEUDOCODE, PATH_PICK, SPOT_BUG,
                    RACE_BET, REVERSE_ENGINEER, WILL_CHANGE, FOG_OF_WAR -> Level.MEDIUM;
            case BELLMAN_DISTANCE, NEGATIVE_CYCLE, FLOYD_ENTRY, KNAPSACK_VALUE, FILL_TABLE, COMPLEXITY_GUESS,
                    CUT_NETWORK, BREAK_IT -> Level.HARD;
        };
    }

    /**
     * About how long one question takes, in seconds: by how the player answers, plus a little for harder activities. The
     * session planner (MS12) spends a time budget with these numbers; they are starting guesses.
     */
    public int seconds() {
        int base = switch (interaction()) {
            case CHOICE -> 20;
            case NUMBER -> 30;
            case PATH -> 40;
            case ORDER -> 45;
            case MATCH -> 60;
            case TABLE -> 80;
        };
        return base + switch (difficulty()) {
            case EASY -> 0;
            case MEDIUM -> 5;
            case HARD -> 10;
        };
    }

    /** The topic used for statistics: the algorithm id, or "general" for cards (a card has its own topic). */
    public String topic() {
        return algorithmId == null ? "general" : algorithmId;
    }

    /** What the question shows: a graph, a list of numbers or a knapsack problem. */
    public InputKind inputKind() {
        return inputKind;
    }

    /** Whether the numbers on the edges matter, so the canvas draws them. */
    public boolean showsWeights() {
        return showsWeights;
    }

    /** @throws IllegalArgumentException if no activity has this id */
    public static QuestionKind byId(String id) {
        for (QuestionKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown Arena activity id: " + id);
    }

    /** The activity with this id, or null (a saved id from a different version of the app). */
    public static QuestionKind findById(String id) {
        for (QuestionKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** All activities that use the given algorithm. */
    public static List<QuestionKind> forAlgorithm(String algorithmId) {
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind kind : values()) {
            if (algorithmId != null && algorithmId.equals(kind.algorithmId)) {
                out.add(kind);
            }
        }
        return out;
    }
}

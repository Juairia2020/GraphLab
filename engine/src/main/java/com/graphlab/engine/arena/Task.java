package com.graphlab.engine.arena;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a question needs besides buttons (MS11): the items to show and the answer the engine worked out. The player's
 * answer is always reduced to one number, the count of <em>mistakes</em> ({@link #mistakes}); the answer is right when
 * that number is within {@link #tolerance()}. One whole number per question is what the round saves and replays, so a
 * rotation or a restart works the same for every activity.
 *
 * <ul>
 *   <li>ORDER: {@code items} are the steps as shown (mixed); {@code solution} lists, for each place 1, 2, 3 ..., the index
 *       of the item that belongs there. Mistakes = places that hold the wrong item.</li>
 *   <li>MATCH: {@code items} are term, meaning, term, meaning ...; the number of pairs is {@code items.size() / 2}. The
 *       game ({@link MatchBoard}) counts the wrong flips; tolerance is how many are allowed.</li>
 *   <li>NUMBER: {@code solution} is the exact value; mistakes = the distance from it; tolerance is the distance allowed.</li>
 *   <li>TABLE: {@code items} are the cells, row by row ({@code columns} per row); an empty text is a blank. {@code solution}
 *       holds the value of each blank in order. Mistakes = blanks that are wrong.</li>
 *   <li>PATH: {@code solution} holds the edge ids of the one shortest route. Mistakes = edges chosen that do not belong plus
 *       edges of the route that are missing.</li>
 * </ul>
 *
 * @param answer the right answer as a sentence, for the feedback
 * @param rowHeaders TABLE only (MS12): a label for each row, or empty to number the rows from 0
 * @param colHeaders TABLE only (MS12): a label for each column, or empty to number the columns from 0
 */
public record Task(Interaction interaction, List<String> items, List<Integer> solution, int tolerance, int columns,
                   String answer, List<String> rowHeaders, List<String> colHeaders) {

    /** A task without table headers (every task except the DP tables). */
    public Task(Interaction interaction, List<String> items, List<Integer> solution, int tolerance, int columns,
                String answer) {
        this(interaction, items, solution, tolerance, columns, answer, List.of(), List.of());
    }

    public Task {
        Objects.requireNonNull(interaction, "interaction");
        items = List.copyOf(items);
        solution = List.copyOf(solution);
        Objects.requireNonNull(answer, "answer");
        rowHeaders = List.copyOf(rowHeaders);
        colHeaders = List.copyOf(colHeaders);
        if (interaction == Interaction.CHOICE) {
            throw new IllegalArgumentException("A choice question has no task");
        }
        if (answer.isBlank() || tolerance < 0) {
            throw new IllegalArgumentException("A task needs an answer text and a tolerance of 0 or more");
        }
        switch (interaction) {
            case ORDER -> {
                if (items.size() < 3 || items.size() > 6 || solution.size() != items.size()) {
                    throw new IllegalArgumentException("An order task has 3 to 6 items and one place per item");
                }
                Set<Integer> seen = new HashSet<>(solution);
                for (int s : solution) {
                    if (s < 0 || s >= items.size()) {
                        throw new IllegalArgumentException("Solution index " + s + " is not an item");
                    }
                }
                if (seen.size() != items.size() || new HashSet<>(items).size() != items.size()) {
                    throw new IllegalArgumentException("Order items and places must all be different");
                }
            }
            case MATCH -> {
                if (items.size() < 6 || items.size() > 8 || items.size() % 2 != 0) {
                    throw new IllegalArgumentException("A match task has 3 or 4 pairs");
                }
                if (new HashSet<>(items).size() != items.size()) {
                    throw new IllegalArgumentException("Match tiles must all be different");
                }
            }
            case NUMBER -> {
                if (solution.size() != 1) {
                    throw new IllegalArgumentException("A number task has exactly one solution value");
                }
            }
            case TABLE -> {
                if (columns < 2 || items.size() % columns != 0 || items.size() / columns < 2) {
                    throw new IllegalArgumentException("A table needs at least 2 rows and 2 columns");
                }
                long blanks = items.stream().filter(String::isEmpty).count();
                if (blanks < 1 || blanks != solution.size()) {
                    throw new IllegalArgumentException("A table needs one solution value per blank cell");
                }
                if ((!colHeaders.isEmpty() && colHeaders.size() != columns)
                        || (!rowHeaders.isEmpty() && rowHeaders.size() != items.size() / columns)) {
                    throw new IllegalArgumentException("Table headers must match the rows and columns");
                }
            }
            case PATH -> {
                if (solution.isEmpty()) {
                    throw new IllegalArgumentException("A path task needs at least one edge");
                }
            }
            default -> throw new IllegalStateException();
        }
    }

    /** Number of pairs in a match task. */
    public int pairs() {
        return items.size() / 2;
    }

    /** Cells of a table task that are blank, as positions in {@link #items()}, in order. */
    public List<Integer> blanks() {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Counts the mistakes in the player's answer.
     *
     * @param given ORDER: the item index the player put in place 1, 2, 3 ...; NUMBER: the one number typed; TABLE: the value
     *              typed for each blank (use {@link #NO_VALUE} for an empty cell); PATH: the edge ids chosen; MATCH: the
     *              number of wrong flips
     */
    public int mistakes(int... given) {
        switch (interaction) {
            case ORDER -> {
                int wrong = 0;
                for (int place = 0; place < solution.size(); place++) {
                    if (place >= given.length || given[place] != solution.get(place)) {
                        wrong++;
                    }
                }
                return wrong;
            }
            case MATCH -> {
                return given.length == 0 ? 0 : Math.max(0, given[0]);
            }
            case NUMBER -> {
                if (given.length != 1 || given[0] == NO_VALUE) {
                    return Integer.MAX_VALUE / 2;
                }
                return (int) Math.min(Integer.MAX_VALUE / 2, Math.abs((long) given[0] - solution.get(0)));
            }
            case TABLE -> {
                int wrong = 0;
                for (int i = 0; i < solution.size(); i++) {
                    if (i >= given.length || given[i] != solution.get(i)) {
                        wrong++;
                    }
                }
                return wrong;
            }
            case PATH -> {
                Set<Integer> chosen = new HashSet<>();
                for (int e : given) {
                    chosen.add(e);
                }
                int wrong = 0;
                for (int e : chosen) {
                    if (!solution.contains(e)) {
                        wrong++;
                    }
                }
                for (int e : solution) {
                    if (!chosen.contains(e)) {
                        wrong++;
                    }
                }
                return wrong;
            }
            default -> throw new IllegalStateException();
        }
    }

    /** The value that stands for an empty cell or an empty number box. */
    public static final int NO_VALUE = Integer.MIN_VALUE;

    /** Whether this many mistakes still counts as a right answer. */
    public boolean isCorrect(int mistakes) {
        return mistakes >= 0 && mistakes <= tolerance;
    }
}

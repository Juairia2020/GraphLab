package com.graphlab.engine.content;

import java.util.List;
import java.util.Objects;

/**
 * One hand-written question of the Arena: a statement to judge (true or false) or a situation to choose a tool for (best
 * tool). Cards live in {@code content/questions.json}, not in code.
 *
 * @param id          unique, for example "tf-001"
 * @param activity    which activity shows it
 * @param topic       an algorithm id ("dijkstra") or "general"
 * @param difficulty  easy, medium or hard
 * @param text        the statement or the situation
 * @param options     the answers in the order shown: True and False, or two to four algorithm names
 * @param correct     the index of the right option
 * @param explanation why it is right
 */
public record Card(String id, Activity activity, String topic, Difficulty difficulty, String text, List<String> options,
                   int correct, String explanation) {

    public enum Activity {
        TRUE_FALSE("true-false", 1),
        BEST_TOOL("best-tool", 1),
        /** Four options, one does not belong. {@code correct} is the odd one. */
        ODD_ONE_OUT("odd-one-out", 2),
        /** Three or four term and meaning pairs; {@code options} lists term, meaning, term, meaning ... */
        MATCH_PAIRS("match-pairs", 2);

        private final String id;
        private final int since;

        Activity(String id, int since) {
            this.id = id;
            this.since = since;
        }

        /** The first {@code schemaVersion} of the question file that may hold this activity. */
        public int since() {
            return since;
        }

        public String id() {
            return id;
        }

        public static Activity byId(String id) {
            for (Activity a : values()) {
                if (a.id.equals(id)) {
                    return a;
                }
            }
            return null;
        }
    }

    public enum Difficulty {
        EASY, MEDIUM, HARD;

        public static Difficulty byName(String name) {
            for (Difficulty d : values()) {
                if (d.name().equalsIgnoreCase(name)) {
                    return d;
                }
            }
            return null;
        }
    }

    public Card {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(explanation, "explanation");
        options = List.copyOf(options);
        if (correct < 0 || correct >= options.size()) {
            throw new IllegalArgumentException("correct is outside the options");
        }
    }

    public String correctAnswer() {
        return options.get(correct);
    }
}

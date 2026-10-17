package com.graphlab.engine.arena;

import com.graphlab.engine.input.Input;
import com.graphlab.engine.trace.GraphFrame;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One multiple-choice question: what to show, what to ask, the choices and which one is right.
 * Immutable. The right answer was worked out by the engine, never typed in by hand.
 *
 * @param kind         the activity the question belongs to
 * @param level        how hard it is
 * @param prompt       the question, in plain English
 * @param input        what to draw: a graph, a list of numbers or a knapsack problem
 * @param source       node id to highlight as the start, or -1
 * @param target       node id to highlight as the end, or -1
 * @param options      two to four different answers, in the order they are shown
 * @param correctIndex position of the right answer in {@code options}
 * @param explanation  why that answer is right, shown after the player has answered
 * @param cardId       the id of the card it was made from (card activities), or null
 * @param topic        what it is about, an algorithm id or "general"; the statistics are kept per topic
 * @param frame        what to paint on the graph (settled and waiting nodes), or null for a plain graph
 * @param task         for the activities that are not a button choice (MS11): the items and the engine's answer, else null.
 *                     With a task, {@code options} is empty and {@code correctIndex} is 0; the answer is read as mistakes.
 */
public record Question(QuestionKind kind, Level level, String prompt, Input input, int source, int target,
        List<String> options, int correctIndex, String explanation, String cardId, String topic, GraphFrame frame,
        Task task) {

    /** A question with no task: the answer is one of the options. */
    public Question(QuestionKind kind, Level level, String prompt, Input input, int source, int target,
            List<String> options, int correctIndex, String explanation, String cardId, String topic, GraphFrame frame) {
        this(kind, level, prompt, input, source, target, options, correctIndex, explanation, cardId, topic, frame, null);
    }

    /** A question with no card id and no drawn frame; its topic is the kind's algorithm, or "general" for card kinds. */
    public Question(QuestionKind kind, Level level, String prompt, Input input, int source, int target,
            List<String> options, int correctIndex, String explanation) {
        this(kind, level, prompt, input, source, target, options, correctIndex, explanation, null,
                kind.algorithmId() == null ? "general" : kind.algorithmId(), null);
    }

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;

    public Question {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(topic, "topic");
        options = List.copyOf(options);
        if (prompt.isBlank() || explanation.isBlank()) {
            throw new IllegalArgumentException("A question needs a prompt and an explanation");
        }
        if (task != null) {
            if (kind.interaction() == Interaction.CHOICE || kind.interaction() != task.interaction()) {
                throw new IllegalArgumentException(kind + " is answered by " + kind.interaction() + " but has a "
                        + task.interaction() + " task");
            }
            if (!options.isEmpty() || correctIndex != 0) {
                throw new IllegalArgumentException("A task question has no options and a correctIndex of 0");
            }
        } else {
            if (kind.interaction() != Interaction.CHOICE) {
                throw new IllegalArgumentException(kind + " needs a task");
            }
            if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("A question has 2 to 4 options, got " + options.size());
            }
            Set<String> seen = new HashSet<>();
            for (String option : options) {
                if (option == null || option.isBlank() || !seen.add(option)) {
                    throw new IllegalArgumentException("Options must be different and not empty: " + options);
                }
            }
            if (correctIndex < 0 || correctIndex >= options.size()) {
                throw new IllegalArgumentException("correctIndex " + correctIndex + " is outside the options");
            }
        }
        boolean dpTable = kind == QuestionKind.FILL_TABLE && input.kind() == com.graphlab.engine.input.InputKind.TEXT;   // MS12: DP tables are described in text
        if (input.kind() != kind.inputKind() && !dpTable) {
            throw new IllegalArgumentException(kind + " shows " + kind.inputKind() + " but got " + input.kind());
        }
    }

    /** The text of the right answer. */
    public String correctAnswer() {
        return task != null ? task.answer() : options.get(correctIndex);
    }

    /**
     * @param choice the position of the option picked; for a task question the number of mistakes
     */
    public boolean isCorrect(int choice) {
        return task != null ? task.isCorrect(choice) : choice == correctIndex;
    }

    /** How the player answers. */
    public Interaction interaction() {
        return task == null ? Interaction.CHOICE : task.interaction();
    }
}

package com.graphlab.engine.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One round of the Arena: {@value #QUESTIONS} questions (a mixed round) or {@value #SINGLE_QUESTIONS} (one activity: a basic
 * question at the chosen level, then a hard variation), answered one after the other. The whole round
 * is decided by its kind, level and seed, so a screen that is rotated or recreated can build the
 * same round again and replay the answers. Plain Java, no Android.
 *
 * <p>The order of calls: {@link #current()}, {@link #answer(int)}, then {@link #advance()} to reach the next
 * question. After the last answer {@link #isFinished()} is true and there is nothing to advance to.
 */
public final class ArenaRound {
    public static final int QUESTIONS = 5;
    /** A round of one activity: one basic question and one hard variation. */
    public static final int SINGLE_QUESTIONS = 2;
    /** The most questions a planned session may have. */
    public static final int MAX_PLANNED = 30;

    /** What happened when an answer was given. */
    public record Verdict(boolean correct, int correctIndex, String explanation, int points) {
    }

    private final QuestionKind kind;   // null for a mixed round
    private final Level level;
    private final long seed;
    private final List<Question> questions;
    /** True when the questions have different difficulties, so stars follow the points won, not the number right. */
    private final boolean weighted;
    private final List<Integer> choices = new ArrayList<>();
    private int index;

    private ArenaRound(QuestionKind kind, Level level, long seed, List<Question> questions) {
        this(kind, level, seed, questions, false);
    }

    private ArenaRound(QuestionKind kind, Level level, long seed, List<Question> questions, boolean weighted) {
        this.kind = kind;
        this.level = level;
        this.seed = seed;
        this.questions = List.copyOf(questions);
        this.weighted = weighted;
    }

    /** The three steps of one activity: one Easy, one Medium and one Hard question, in that order. */
    public static final int LADDER_QUESTIONS = 3;

    /**
     * What happens when the player taps an activity: three questions of that activity, one {@link Level#EASY}, one
     * {@link Level#MEDIUM} and one {@link Level#HARD}, shown one after the other. Each is worth points at its own level, so
     * the stars follow the points won. Nothing is remembered between rounds: a new seed makes three new questions, and the
     * questions are only made when the player starts the activity. The same seed rebuilds the same round (a rotated screen).
     */
    public static ArenaRound ladder(QuestionKind kind, long seed) {
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();   // only so the three steps of this one round differ
        for (int i = 0; i < LADDER_QUESTIONS; i++) {
            Question q = QuestionFactory.create(kind, ArenaScoring.climb(i, LADDER_QUESTIONS), random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
        }
        return new ArenaRound(kind, Level.EASY, seed, list, true);
    }

    /**
     * Two questions of one activity: a basic one at {@code level}, then a hard variation of the same activity (generated at
     * {@link Level#HARD}, so a bigger problem). Each question is worth points at its own level.
     */
    public static ArenaRound of(QuestionKind kind, Level level, long seed) {
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        for (int i = 0; i < SINGLE_QUESTIONS; i++) {
            Level questionLevel = i == 0 ? level : Level.HARD;
            Question q = QuestionFactory.create(kind, questionLevel, random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
        }
        return new ArenaRound(kind, level, seed, list);
    }

    /** Five questions from five different activities, chosen by the seed. */
    public static ArenaRound mixed(Level level, long seed) {
        return mixed(level, seed, NeedModel.neutral());
    }

    /**
     * A mixed round chosen by the selector (Surprise Engine, basic): activities the player is weaker at come up more often,
     * with some noise, and the same activity never comes twice in a row. The same level, seed and needs give the same round.
     */
    public static ArenaRound mixed(Level level, long seed, NeedModel needs) {
        return mixed(level, seed, needs, List.of(QuestionKind.values()));
    }

    /** Like {@link #mixed(Level, long, NeedModel)}, but only from the activities in {@code all} (the unlocked ones). */
    public static ArenaRound mixed(Level level, long seed, NeedModel needs, List<QuestionKind> all) {
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No activity to choose from");
        }
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        QuestionKind previous = null;
        for (int i = 0; i < QUESTIONS; i++) {
            QuestionKind kind = ActivitySelector.next(all, needs, previous, random, ActivitySelector.TEMPERATURE);
            Question q = QuestionFactory.create(kind, level, random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
            previous = kind;
        }
        return new ArenaRound(null, level, seed, list);
    }

    /** A mixed round chosen by the Surprise Engine v2 (need, mastery, novelty and due topics). */
    public static ArenaRound mixed(Level level, long seed, SurpriseEngine engine) {
        return mixed(level, seed, engine, List.of(QuestionKind.values()));
    }

    /** Like {@link #mixed(Level, long, SurpriseEngine)}, but only from the activities in {@code all} (the unlocked ones). */
    public static ArenaRound mixed(Level level, long seed, SurpriseEngine engine, List<QuestionKind> all) {
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No activity to choose from");
        }
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        QuestionKind previous = null;
        for (int i = 0; i < QUESTIONS; i++) {
            QuestionKind kind = ActivitySelector.next(all, engine::score, previous, random, ActivitySelector.TEMPERATURE);
            Question q = QuestionFactory.create(kind, level, random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
            previous = kind;
        }
        return new ArenaRound(null, level, seed, list);
    }

    /**
     * A mixed round that climbs from Easy to Hard (questions 1 and 2 Easy, 3 and 4 Medium, 5 Hard), with activities chosen
     * by the Surprise Engine v2 from {@code all} (the unlocked ones). Stars follow the points won.
     */
    public static ArenaRound mixedClimbing(long seed, SurpriseEngine engine, List<QuestionKind> all) {
        return mixedClimbing(seed, engine::score, all);
    }

    /** Like {@link #mixedClimbing(long, SurpriseEngine, List)} with the basic selector (needs only). */
    public static ArenaRound mixedClimbing(long seed, NeedModel needs, List<QuestionKind> all) {
        return mixedClimbing(seed, needs::need, all);
    }

    private static ArenaRound mixedClimbing(long seed, java.util.function.ToDoubleFunction<QuestionKind> score,
            List<QuestionKind> all) {
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No activity to choose from");
        }
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        QuestionKind previous = null;
        for (int i = 0; i < QUESTIONS; i++) {
            QuestionKind kind = ActivitySelector.next(all, score, previous, random, ActivitySelector.TEMPERATURE);
            Question q = QuestionFactory.create(kind, ArenaScoring.climb(i, QUESTIONS), random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
            previous = kind;
        }
        return new ArenaRound(null, Level.EASY, seed, list, true);
    }

    /** A planned session that climbs from Easy to Hard over its questions. Stars follow the points won. */
    public static ArenaRound plannedClimbing(long seed, List<QuestionKind> kinds) {
        if (kinds.isEmpty() || kinds.size() > MAX_PLANNED) {
            throw new IllegalArgumentException("A planned session has 1 to " + MAX_PLANNED + " questions, got " + kinds.size());
        }
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        for (int i = 0; i < kinds.size(); i++) {
            Question q = QuestionFactory.create(kinds.get(i), ArenaScoring.climb(i, kinds.size()), random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
        }
        return new ArenaRound(null, Level.EASY, seed, list, true);
    }

    /**
     * A planned session (MS12): one question for each activity in {@code kinds}, in that order. The round is decided by the
     * kinds, the level and the seed, so a rotated screen can build it again. At least one and at most {@value #MAX_PLANNED}.
     */
    public static ArenaRound planned(Level level, long seed, List<QuestionKind> kinds) {
        if (kinds.isEmpty() || kinds.size() > MAX_PLANNED) {
            throw new IllegalArgumentException("A planned session has 1 to " + MAX_PLANNED + " questions, got " + kinds.size());
        }
        Random random = new Random(seed);
        List<Question> list = new ArrayList<>();
        java.util.Set<String> usedCards = new java.util.HashSet<>();
        for (QuestionKind kind : kinds) {
            Question q = QuestionFactory.create(kind, level, random.nextLong(), usedCards);
            if (q.cardId() != null) {
                usedCards.add(q.cardId());
            }
            list.add(q);
        }
        return new ArenaRound(null, level, seed, list);
    }

    /**
     * A round made of exact specs (MS17: the daily puzzle and the Ghost round). Each question is made from its own kind, level and
     * seed, so the same specs give the same round on every device. At least one and at most {@value #MAX_PLANNED}.
     *
     * @param level the level used for points and stars
     */
    public static ArenaRound fromSpecs(Level level, long seed, List<QuestionSpec> specs) {
        if (specs.isEmpty() || specs.size() > MAX_PLANNED) {
            throw new IllegalArgumentException("A round has 1 to " + MAX_PLANNED + " questions, got " + specs.size());
        }
        List<Question> list = new ArrayList<>();
        for (QuestionSpec spec : specs) {
            list.add(spec.create());
        }
        return new ArenaRound(null, level, seed, list);
    }

    /** The activity, or null for a mixed round. */
    public QuestionKind kind() {
        return kind;
    }

    public boolean isMixed() {
        return kind == null;
    }

    public Level level() {
        return level;
    }

    public long seed() {
        return seed;
    }

    public int size() {
        return questions.size();
    }

    /** Position of the question being shown, counting from 0. */
    public int index() {
        return index;
    }

    public Question current() {
        return questions.get(index);
    }

    public Question question(int position) {
        return questions.get(position);
    }

    /** Whether the question being shown has been answered. */
    public boolean isAnswered() {
        return choices.size() > index;
    }

    /** All questions answered. */
    public boolean isFinished() {
        return choices.size() == questions.size();
    }

    /** The position the player picked for question {@code position}; only for answered questions. */
    public int choice(int position) {
        return choices.get(position);
    }

    /**
     * Records the answer to the current question.
     *
     * @param choice position in {@link Question#options()}
     * @throws IllegalStateException    if the current question was already answered
     * @throws IllegalArgumentException if {@code choice} is not a position of an option
     */
    public Verdict answer(int choice) {
        if (isAnswered()) {
            throw new IllegalStateException("Question " + (index + 1) + " was already answered");
        }
        Question q = current();
        if (choice < 0 || (q.task() == null && choice >= q.options().size())) {
            throw new IllegalArgumentException(q.task() == null ? "No option " + choice : "Mistakes cannot be negative: " + choice);
        }
        choices.add(choice);
        boolean right = q.isCorrect(choice);
        return new Verdict(right, q.correctIndex(), q.explanation(), right ? ArenaScoring.points(1, q.level()) : 0);
    }

    /** Moves to the next question. @throws IllegalStateException if this one is not answered or it was the last */
    public void advance() {
        if (!isAnswered()) {
            throw new IllegalStateException("Answer question " + (index + 1) + " first");
        }
        if (index + 1 >= questions.size()) {
            throw new IllegalStateException("That was the last question");
        }
        index++;
    }

    public boolean hasNext() {
        return index + 1 < questions.size();
    }

    public int correctCount() {
        int n = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (questions.get(i).isCorrect(choices.get(i))) {
                n++;
            }
        }
        return n;
    }

    public int answeredCount() {
        return choices.size();
    }

    public int points() {
        int total = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (questions.get(i).isCorrect(choices.get(i))) {
                total += ArenaScoring.points(1, questions.get(i).level());
            }
        }
        return total;
    }

    /** Stars so far; only meaningful once {@link #isFinished()}. */
    public int stars() {
        return weighted ? ArenaScoring.starsByPoints(points(), maxPoints()) : ArenaScoring.stars(correctCount(), questions.size());
    }

    /** The points a perfect round would win. */
    public int maxPoints() {
        int total = 0;
        for (Question q : questions) {
            total += ArenaScoring.points(1, q.level());
        }
        return total;
    }

    /** The picked positions so far, for saving across a rotation. */
    public int[] choices() {
        int[] out = new int[choices.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = choices.get(i);
        }
        return out;
    }

    /**
     * Plays back saved choices on a fresh round and moves to the saved position.
     *
     * @param saved     the positions picked, in order
     * @param position  the question that was showing ({@link #index()} when saved)
     * @throws IllegalArgumentException if the saved values do not fit this round
     */
    public void restore(int[] saved, int position) {
        if (saved.length > questions.size() || position < 0 || position >= questions.size()
                || position > saved.length || position < saved.length - 1) {
            throw new IllegalArgumentException("Saved answers do not fit this round");
        }
        for (int i = 0; i < saved.length; i++) {
            answer(saved[i]);
            if (i < position) {
                advance();
            }
        }
    }
}

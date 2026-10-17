package com.graphlab.engine.content;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import com.graphlab.engine.trace.Algorithm;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The hand-written question cards (MS6, moved to JSON in MS11). {@link #parse} checks every card, so a bad file is refused
 * with the id of the card and what is wrong; nothing half-valid gets in. The file shipped with the app is a resource of the
 * engine, so the unit tests check the very file the app uses. A newer file fetched from the network replaces it with
 * {@link #install}; if the fetch fails the bundled one stays. Plain Java, no Android.
 *
 * <p>File format ({@code schemaVersion} 2; version 1 files, without the two newer activities, are still read):
 * <pre>
 * { "schemaVersion": 1, "cards": [
 *   {"id": "tf-001", "activity": "true-false", "topic": "dijkstra", "difficulty": "easy",
 *    "text": "...", "answer": false, "explanation": "..."},
 *   {"id": "bt-001", "activity": "best-tool", "topic": "kruskal", "difficulty": "easy",
 *    "text": "...", "options": ["Kruskal", "Dijkstra", "Prim"], "correct": 0, "explanation": "..."} ] }
 * </pre>
 */
public final class CardBank {
    public static final int SCHEMA_VERSION = 2;
    public static final String RESOURCE = "/content/questions.json";
    public static final String GENERAL = "general";
    public static final List<String> TRUE_FALSE_OPTIONS = List.of("True", "False");

    private static volatile CardBank current;

    private final List<Card> cards;

    private CardBank(List<Card> cards) {
        this.cards = List.copyOf(cards);
    }

    public List<Card> cards() {
        return cards;
    }

    public List<Card> of(Card.Activity activity) {
        List<Card> out = new ArrayList<>();
        for (Card card : cards) {
            if (card.activity() == activity) {
                out.add(card);
            }
        }
        return out;
    }

    // ---- choosing -------------------------------------------------------------------

    /**
     * A card for this activity at this level: easy cards for Easy, easy and medium for Medium, medium and hard for Hard.
     * Cards in {@code used} are skipped while others remain, so one round does not repeat a card.
     */
    public Card pick(Card.Activity activity, int levelOrdinal, Random random, Set<String> used) {
        List<Card> fit = new ArrayList<>();
        List<Card> fresh = new ArrayList<>();
        for (Card card : cards) {
            if (card.activity() != activity || !fits(card.difficulty(), levelOrdinal)) {
                continue;
            }
            fit.add(card);
            if (!used.contains(card.id())) {
                fresh.add(card);
            }
        }
        List<Card> from = !fresh.isEmpty() ? fresh : fit;
        if (from.isEmpty()) {
            from = of(activity);
        }
        if (from.isEmpty()) {
            throw new IllegalStateException("There are no " + activity.id() + " cards.");
        }
        return from.get(random.nextInt(from.size()));
    }

    private static boolean fits(Card.Difficulty difficulty, int levelOrdinal) {
        int d = difficulty.ordinal();
        return switch (levelOrdinal) {
            case 0 -> d == 0;
            case 1 -> d <= 1;
            default -> d >= 1;
        };
    }

    // ---- the bank in use ------------------------------------------------------------

    /** The bank the Arena uses: an installed one, otherwise the one bundled with the app. */
    public static CardBank current() {
        CardBank bank = current;
        if (bank == null) {
            synchronized (CardBank.class) {
                if (current == null) {
                    current = bundled();
                }
                bank = current;
            }
        }
        return bank;
    }

    public static void install(CardBank bank) {
        current = java.util.Objects.requireNonNull(bank, "bank");
    }

    /** Forgets an installed bank, so {@link #current()} is the bundled one again. */
    public static void reset() {
        current = null;
    }

    public static CardBank bundled() {
        try (InputStream in = CardBank.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled question file is missing: " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | ContentException e) {
            throw new IllegalStateException("The bundled question file is damaged: " + e.getMessage(), e);
        }
    }

    // ---- reading --------------------------------------------------------------------

    public static CardBank parse(String text) throws ContentException {
        Object root;
        try {
            root = MiniJson.parse(text);
        } catch (GraphFormatException e) {
            throw new ContentException("The question file is not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map)) {
            throw new ContentException("The question file must be a JSON object.");
        }
        Map<?, ?> top = (Map<?, ?>) root;
        Object version = top.get("schemaVersion");
        if (!(version instanceof Number) || ((Number) version).doubleValue() != Math.rint(((Number) version).doubleValue())) {
            throw new ContentException("\"schemaVersion\" is missing or is not a whole number.");
        }
        int v = ((Number) version).intValue();
        if (v < 1 || v > SCHEMA_VERSION) {
            throw new ContentException("The question file has schemaVersion " + v + "; this app reads up to " + SCHEMA_VERSION + ".");
        }
        Object list = top.get("cards");
        if (!(list instanceof List)) {
            throw new ContentException("\"cards\" is missing or is not a list.");
        }
        Set<String> algorithmNames = new HashSet<>();
        Set<String> topics = new HashSet<>();
        topics.add(GENERAL);
        for (Algorithm a : Algorithms.all()) {
            algorithmNames.add(a.name());
            topics.add(a.id());
        }
        List<Card> cards = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int position = 0;
        for (Object raw : (List<?>) list) {
            position++;
            if (!(raw instanceof Map)) {
                throw new ContentException("Card " + position + " is not an object.");
            }
            Map<?, ?> m = (Map<?, ?>) raw;
            String id = string(m, "id", "Card " + position);
            String where = "Card \"" + id + "\"";
            if (!ids.add(id)) {
                throw new ContentException(where + ": the id is used twice.");
            }
            Card.Activity activity = Card.Activity.byId(string(m, "activity", where));
            if (activity == null || activity.since() > v) {
                throw new ContentException(where + ": unknown activity \"" + m.get("activity") + "\" for schemaVersion " + v + ".");
            }
            String topic = string(m, "topic", where);
            if (!topics.contains(topic)) {
                throw new ContentException(where + ": the topic \"" + topic + "\" is not an algorithm id or \"general\".");
            }
            Card.Difficulty difficulty = Card.Difficulty.byName(string(m, "difficulty", where));
            if (difficulty == null) {
                throw new ContentException(where + ": difficulty must be easy, medium or hard.");
            }
            String body = string(m, "text", where);
            String explanation = string(m, "explanation", where);
            List<String> options;
            int correct;
            if (activity == Card.Activity.TRUE_FALSE) {
                Object answer = m.get("answer");
                if (!(answer instanceof Boolean)) {
                    throw new ContentException(where + ": \"answer\" must be true or false.");
                }
                options = TRUE_FALSE_OPTIONS;
                correct = (Boolean) answer ? 0 : 1;
            } else if (activity == Card.Activity.MATCH_PAIRS) {
                options = pairs(m.get("pairs"), where);
                correct = 0;
            } else if (activity == Card.Activity.ODD_ONE_OUT) {
                options = strings(m.get("options"), where);
                if (options.size() != 4 || new HashSet<>(options).size() != 4) {
                    throw new ContentException(where + ": an odd-one-out card has exactly 4 different options.");
                }
                Object index = m.get("correct");
                if (!(index instanceof Number) || ((Number) index).doubleValue() != Math.rint(((Number) index).doubleValue())) {
                    throw new ContentException(where + ": \"correct\" must be a whole number.");
                }
                correct = ((Number) index).intValue();
                if (correct < 0 || correct >= 4) {
                    throw new ContentException(where + ": \"correct\" is outside the options.");
                }
            } else {
                options = strings(m.get("options"), where);
                if (options.size() < 3 || options.size() > 4) {
                    throw new ContentException(where + ": a best-tool card has 3 or 4 options.");
                }
                if (new HashSet<>(options).size() != options.size()) {
                    throw new ContentException(where + ": the options must be different.");
                }
                for (String option : options) {
                    if (!algorithmNames.contains(option)) {
                        throw new ContentException(where + ": option \"" + option + "\" is not the name of an algorithm in GraphLab.");
                    }
                }
                Object index = m.get("correct");
                if (!(index instanceof Number) || ((Number) index).doubleValue() != Math.rint(((Number) index).doubleValue())) {
                    throw new ContentException(where + ": \"correct\" must be a whole number.");
                }
                correct = ((Number) index).intValue();
                if (correct < 0 || correct >= options.size()) {
                    throw new ContentException(where + ": \"correct\" is outside the options.");
                }
            }
            cards.add(new Card(id, activity, topic, difficulty, body, options, correct, explanation));
        }
        for (Card.Activity activity : Card.Activity.values()) {
            if (activity.since() > v) {
                continue;   // an older file has no cards for the newer activities
            }
            for (int level = 0; level < 3; level++) {
                boolean any = false;
                for (Card card : cards) {
                    any |= card.activity() == activity && fits(card.difficulty(), level);
                }
                if (!any) {
                    throw new ContentException("There is no " + activity.id() + " card for level " + (level + 1) + ".");
                }
            }
        }
        return new CardBank(cards);
    }

    /** Reads {@code "pairs": [["term", "meaning"], ...]} into term, meaning, term, meaning ... */
    private static List<String> pairs(Object value, String where) throws ContentException {
        if (!(value instanceof List) || ((List<?>) value).size() < 3 || ((List<?>) value).size() > 4) {
            throw new ContentException(where + ": \"pairs\" must be a list of 3 or 4 pairs.");
        }
        List<String> out = new ArrayList<>();
        for (Object pair : (List<?>) value) {
            if (!(pair instanceof List) || ((List<?>) pair).size() != 2) {
                throw new ContentException(where + ": every pair must be [term, meaning].");
            }
            for (Object side : (List<?>) pair) {
                if (!(side instanceof String) || ((String) side).isBlank()) {
                    throw new ContentException(where + ": every term and meaning must be non-empty text.");
                }
                out.add((String) side);
            }
        }
        if (new HashSet<>(out).size() != out.size()) {
            throw new ContentException(where + ": every term and meaning must be different.");
        }
        return out;
    }

    /** True if the bank has cards for this activity (a version 1 file has none for the newer ones). */
    public boolean has(Card.Activity activity) {
        for (Card card : cards) {
            if (card.activity() == activity) {
                return true;
            }
        }
        return false;
    }

    private static String string(Map<?, ?> m, String key, String where) throws ContentException {
        Object value = m.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new ContentException(where + ": \"" + key + "\" is missing or empty.");
        }
        return (String) value;
    }

    private static List<String> strings(Object value, String where) throws ContentException {
        if (!(value instanceof List)) {
            throw new ContentException(where + ": \"options\" is missing or is not a list.");
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (!(o instanceof String) || ((String) o).isBlank()) {
                throw new ContentException(where + ": every option must be non-empty text.");
            }
            out.add((String) o);
        }
        return out;
    }

    /** How many cards of each topic, for the validation test and the README. */
    public Map<String, Integer> topicCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Card card : cards) {
            out.merge(card.topic(), 1, Integer::sum);
        }
        return out;
    }
}

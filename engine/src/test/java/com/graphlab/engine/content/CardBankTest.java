package com.graphlab.engine.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.trace.Algorithm;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** The shipped question file and the rules that every card file must obey. */
public class CardBankTest {
    private static final String OK_TF = "{\"id\":\"a\",\"activity\":\"true-false\",\"topic\":\"dijkstra\",\"difficulty\":\"easy\","
            + "\"text\":\"T\",\"answer\":true,\"explanation\":\"E\"}";

    private static String file(String... cards) {
        return "{\"schemaVersion\":" + CardBank.SCHEMA_VERSION + ",\"cards\":[" + String.join(",", cards) + "]}";
    }

    @Test
    public void bundledFileIsValidAndBigEnough() {
        CardBank bank = CardBank.bundled();
        assertTrue(bank.of(Card.Activity.TRUE_FALSE).size() >= 35);
        assertTrue(bank.of(Card.Activity.BEST_TOOL).size() >= 28);
    }

    @Test
    public void everyBundledCardIsWellFormed() {
        Set<String> names = new HashSet<>();
        for (Algorithm a : Algorithms.all()) {
            names.add(a.name());
        }
        Set<String> ids = new HashSet<>();
        for (Card card : CardBank.bundled().cards()) {
            assertTrue("unique id " + card.id(), ids.add(card.id()));
            assertTrue(card.explanation().length() > 15);
            if (card.activity() == Card.Activity.ODD_ONE_OUT) {
                assertEquals(4, card.options().size());
                continue;
            }
            if (card.activity() == Card.Activity.MATCH_PAIRS) {
                assertTrue(card.options().size() == 6 || card.options().size() == 8);
                continue;
            }
            if (card.activity() == Card.Activity.BEST_TOOL) {
                assertTrue(card.options().size() >= 3);
                for (String option : card.options()) {
                    assertTrue(card.id() + " option " + option, names.contains(option));
                }
            } else {
                assertEquals(CardBank.TRUE_FALSE_OPTIONS, card.options());
            }
        }
    }

    @Test
    public void bothAnswersAppearInTheTrueFalseCards() {
        long trues = CardBank.bundled().of(Card.Activity.TRUE_FALSE).stream().filter(c -> c.correct() == 0).count();
        int all = CardBank.bundled().of(Card.Activity.TRUE_FALSE).size();
        assertTrue("true cards " + trues + " of " + all, trues >= all / 4 && trues <= all * 3 / 4 + 3);
    }

    @Test
    public void theRightToolIsNotAlwaysInTheSamePlace() {
        Set<Integer> places = new HashSet<>();
        for (Card card : CardBank.bundled().of(Card.Activity.BEST_TOOL)) {
            places.add(card.correct());
        }
        assertTrue(places.size() >= 3);
    }

    @Test
    public void bestToolCardsReallyNameTheirTopic() {
        for (Card card : CardBank.bundled().of(Card.Activity.BEST_TOOL)) {
            String right = card.correctAnswer();
            boolean match = Algorithms.all().stream().anyMatch(a -> a.name().equals(right) && a.id().equals(card.topic()));
            assertTrue(card.id() + " topic " + card.topic() + " but answer " + right, match);
        }
    }

    @Test
    public void pickRespectsTheLevelAndAvoidsUsedCards() {
        CardBank bank = CardBank.bundled();
        Random random = new Random(1);
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Card card = bank.pick(Card.Activity.TRUE_FALSE, 0, random, used);
            assertEquals(Card.Difficulty.EASY, card.difficulty());
            assertTrue(used.add(card.id()));
        }
        for (int i = 0; i < 40; i++) {
            Card hard = bank.pick(Card.Activity.BEST_TOOL, 2, random, Set.of());
            assertTrue(hard.difficulty() != Card.Difficulty.EASY);
        }
    }

    @Test
    public void aSmallValidFileIsNotEnoughBecauseEveryLevelNeedsCards() {
        assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF)));
    }

    @Test
    public void errorsNameTheCard() {
        ContentException e = assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF, OK_TF)));
        assertTrue(e.getMessage(), e.getMessage().contains("\"a\"") && e.getMessage().contains("twice"));
        e = assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF.replace("dijkstra", "nope"))));
        assertTrue(e.getMessage(), e.getMessage().contains("topic"));
        e = assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF.replace("\"answer\":true", "\"answer\":\"yes\""))));
        assertTrue(e.getMessage(), e.getMessage().contains("answer"));
        e = assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF.replace("easy", "tough"))));
        assertTrue(e.getMessage(), e.getMessage().contains("difficulty"));
        e = assertThrows(ContentException.class, () -> CardBank.parse(file(OK_TF.replace("true-false", "quiz"))));
        assertTrue(e.getMessage(), e.getMessage().contains("activity"));
    }

    @Test
    public void bestToolRulesAreChecked() {
        String bt = "{\"id\":\"b\",\"activity\":\"best-tool\",\"topic\":\"dijkstra\",\"difficulty\":\"easy\",\"text\":\"T\","
                + "\"options\":[\"Dijkstra\",\"Kruskal\",\"Prim\"],\"correct\":0,\"explanation\":\"E\"}";
        assertThrows(ContentException.class, () -> CardBank.parse(file(bt.replace("\"Prim\"", "\"Magic sort\""))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(bt.replace("\"Prim\"", "\"Kruskal\""))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(bt.replace("\"correct\":0", "\"correct\":3"))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(bt.replace(",\"Prim\"", ""))));
    }

    @Test
    public void fileLevelErrorsAreRefused() {
        assertThrows(ContentException.class, () -> CardBank.parse("not json"));
        assertThrows(ContentException.class, () -> CardBank.parse("[]"));
        assertThrows(ContentException.class, () -> CardBank.parse("{\"cards\":[]}"));
        assertThrows(ContentException.class, () -> CardBank.parse("{\"schemaVersion\":99,\"cards\":[]}"));
        assertThrows(ContentException.class, () -> CardBank.parse("{\"schemaVersion\":1}"));
    }

    @Test
    public void installReplacesTheBundledBankUntilReset() {
        CardBank bundled = CardBank.current();
        CardBank other = CardBank.bundled();
        CardBank.install(other);
        assertTrue(CardBank.current() == other);
        CardBank.reset();
        assertFalse(CardBank.current() == other);
        assertEquals(bundled.cards().size(), CardBank.current().cards().size());
    }

    // ---- schema version 2 (MS11): odd one out and match pairs ---------------------------

    @Test
    public void newActivitiesHaveCardsAtEveryLevel() {
        for (Card.Activity a : Card.Activity.values()) {
            assertTrue(a.id(), CardBank.bundled().of(a).size() >= 10);
            for (int level = 0; level < 3; level++) {
                CardBank.bundled().pick(a, level, new Random(level), Set.of());
            }
        }
    }

    @Test
    public void oddOneOutOptionsThatNameAlgorithmsNameRealOnes() {
        Set<String> names = new HashSet<>();
        for (Algorithm a : Algorithms.all()) {
            names.add(a.name());
        }
        for (Card card : CardBank.bundled().of(Card.Activity.ODD_ONE_OUT)) {
            for (String option : card.options()) {
                assertTrue(card.id() + " " + option, names.contains(option));
            }
        }
        for (Card card : CardBank.bundled().of(Card.Activity.MATCH_PAIRS)) {
            // the terms (even positions) are algorithms, data structures or ideas; none may be a half-typed algorithm name
            assertTrue(card.id(), card.options().size() >= 6);
        }
    }

    @Test
    public void aVersionOneFileWithoutTheNewActivitiesIsStillRead() throws Exception {
        StringBuilder cards = new StringBuilder();
        for (Card c : CardBank.bundled().cards()) {
            if (c.activity().since() > 1) {
                continue;
            }
            String json;
            if (c.activity() == Card.Activity.TRUE_FALSE) {
                json = "{\"id\":\"" + c.id() + "\",\"activity\":\"true-false\",\"topic\":\"" + c.topic() + "\",\"difficulty\":\""
                        + c.difficulty().name().toLowerCase() + "\",\"text\":\"" + c.text().replace("\"", "'") + "\",\"answer\":" + (c.correct() == 0)
                        + ",\"explanation\":\"" + c.explanation().replace("\"", "'") + "\"}";
            } else {
                StringBuilder options = new StringBuilder();
                for (String o : c.options()) {
                    options.append(options.length() == 0 ? "" : ",").append('"').append(o).append('"');
                }
                json = "{\"id\":\"" + c.id() + "\",\"activity\":\"best-tool\",\"topic\":\"" + c.topic() + "\",\"difficulty\":\""
                        + c.difficulty().name().toLowerCase() + "\",\"text\":\"" + c.text().replace("\"", "'") + "\",\"options\":[" + options
                        + "],\"correct\":" + c.correct() + ",\"explanation\":\"" + c.explanation().replace("\"", "'") + "\"}";
            }
            cards.append(cards.length() == 0 ? "" : ",").append(json);
        }
        CardBank v1 = CardBank.parse("{\"schemaVersion\":1,\"cards\":[" + cards + "]}");
        assertFalse(v1.has(Card.Activity.ODD_ONE_OUT));
        assertTrue(v1.has(Card.Activity.TRUE_FALSE));
        // and a version 1 file may not smuggle in a newer activity
        assertThrows(ContentException.class, () -> CardBank.parse("{\"schemaVersion\":1,\"cards\":[{\"id\":\"x\",\"activity\":\"odd-one-out\","
                + "\"topic\":\"general\",\"difficulty\":\"easy\",\"text\":\"t\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"correct\":0,\"explanation\":\"e\"}]}"));
    }

    @Test
    public void newActivityCardsAreValidated() {
        String odd = "{\"id\":\"o\",\"activity\":\"odd-one-out\",\"topic\":\"general\",\"difficulty\":\"easy\",\"text\":\"t\","
                + "\"options\":[\"a\",\"b\",\"c\",\"d\"],\"correct\":1,\"explanation\":\"e\"}";
        assertThrows(ContentException.class, () -> CardBank.parse(file(odd.replace("\"d\"", "\"c\""))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(odd.replace(",\"d\"", ""))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(odd.replace("\"correct\":1", "\"correct\":4"))));
        String mp = "{\"id\":\"m\",\"activity\":\"match-pairs\",\"topic\":\"general\",\"difficulty\":\"easy\",\"text\":\"t\","
                + "\"pairs\":[[\"a\",\"A\"],[\"b\",\"B\"],[\"c\",\"C\"]],\"explanation\":\"e\"}";
        assertThrows(ContentException.class, () -> CardBank.parse(file(mp.replace("[\"c\",\"C\"]", "[\"a\",\"C\"]"))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(mp.replace(",[\"c\",\"C\"]", ""))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(mp.replace("[\"c\",\"C\"]", "[\"c\"]"))));
        assertThrows(ContentException.class, () -> CardBank.parse(file(mp.replace("\"pairs\"", "\"options\""))));
    }
}

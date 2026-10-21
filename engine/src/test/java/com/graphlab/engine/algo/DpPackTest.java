package com.graphlab.engine.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.CoinInput;
import com.graphlab.engine.input.InputParser;
import com.graphlab.engine.input.StringsInput;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** The MS12 dynamic-programming pack: every answer is re-checked by brute force, and every traceback is replayed. */
public class DpPackTest {
    private static Trace run(String id, com.graphlab.engine.input.Input input) {
        return Algorithms.byId(id).run(input, Params.none());
    }

    // ---- brute force references ------------------------------------------------------

    private static int bruteCoins(List<Integer> coins, int amount) {
        int best = Integer.MAX_VALUE;
        if (amount == 0) {
            return 0;
        }
        for (int c : coins) {
            if (c <= amount) {
                int sub = bruteCoins(coins, amount - c);
                if (sub != Integer.MAX_VALUE) {
                    best = Math.min(best, sub + 1);
                }
            }
        }
        return best;
    }

    private static int bruteLcs(String a, String b) {
        int best = 0;
        for (int mask = 0; mask < (1 << a.length()); mask++) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                if ((mask >> i & 1) == 1) {
                    s.append(a.charAt(i));
                }
            }
            if (s.length() > best && isSubsequence(s.toString(), b)) {
                best = s.length();
            }
        }
        return best;
    }

    private static boolean isSubsequence(String s, String t) {
        int at = 0;
        for (int i = 0; i < t.length() && at < s.length(); i++) {
            if (t.charAt(i) == s.charAt(at)) {
                at++;
            }
        }
        return at == s.length();
    }

    private static int bruteEdit(String a, String b) {
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        if (a.charAt(0) == b.charAt(0)) {
            return bruteEdit(a.substring(1), b.substring(1));
        }
        return 1 + Math.min(bruteEdit(a.substring(1), b.substring(1)),
                Math.min(bruteEdit(a.substring(1), b), bruteEdit(a, b.substring(1))));
    }

    private static int bruteLis(List<Integer> a) {
        int best = 0;
        for (int mask = 0; mask < (1 << a.size()); mask++) {
            int last = -1;
            int count = 0;
            boolean ok = true;
            for (int i = 0; i < a.size() && ok; i++) {
                if ((mask >> i & 1) == 1) {
                    if (a.get(i) <= last) {
                        ok = false;
                    }
                    last = a.get(i);
                    count++;
                }
            }
            if (ok) {
                best = Math.max(best, count);
            }
        }
        return best;
    }

    // ---- coin change -----------------------------------------------------------------

    @Test
    public void coinChangeBeatsGreedyOnTheClassicExample() {
        Result r = run("coin-change", InputParser.defaultCoins()).result();
        assertEquals(2, r.value(), 0);
        assertEquals("3 + 3", r.text());
        assertEquals(List.of(1, 1), r.chosen());
    }

    @Test
    public void coinChangeMatchesBruteForceAndTheTracebackAddsUp() {
        Random random = new Random(12);
        for (int round = 0; round < 150; round++) {
            CoinInput in = InputParser.randomCoins(random.nextLong());
            Result r = run("coin-change", in).result();
            int expected = bruteCoins(in.coins(), in.amount());
            if (expected == Integer.MAX_VALUE) {
                assertEquals(-1, r.value(), 0);
                assertTrue(r.chosen().isEmpty());
            } else {
                assertEquals(in.coins() + " for " + in.amount(), expected, r.value(), 0);
                int sum = 0;
                for (int index : r.chosen()) {
                    sum += in.coins().get(index);
                }
                assertEquals(in.amount(), sum);
                assertEquals(expected, r.chosen().size());
            }
        }
    }

    @Test
    public void coinChangeSaysWhenAnAmountCannotBeMade() {
        Trace t = run("coin-change", new CoinInput(List.of(4, 6), 7));
        assertEquals(-1, t.result().value(), 0);
        assertTrue(t.last().message().contains("cannot be made"));
        assertEquals(Double.POSITIVE_INFINITY, t.result().matrix().get(2).get(7), 0);
    }

    @Test
    public void coinInputRefusesBadProblems() {
        assertThrows(IllegalArgumentException.class, () -> new CoinInput(List.of(), 5));
        assertThrows(IllegalArgumentException.class, () -> new CoinInput(List.of(1, 1), 5));
        assertThrows(IllegalArgumentException.class, () -> new CoinInput(List.of(0), 5));
        assertThrows(IllegalArgumentException.class, () -> new CoinInput(List.of(1, 2, 3, 4, 5, 6, 7), 5));
        assertThrows(IllegalArgumentException.class, () -> new CoinInput(List.of(1), 21));
        assertThrows(IllegalArgumentException.class, () -> InputParser.parseCoins("1 x", "5"));
        assertEquals(new CoinInput(List.of(1, 3, 4), 6), InputParser.parseCoins("1, 3 4", " 6"));
    }

    // ---- LCS -------------------------------------------------------------------------

    @Test
    public void lcsTextbookExample() {
        Result r = run("lcs", InputParser.defaultStrings()).result();
        assertEquals(4, r.value(), 0);
        assertEquals(4, r.text().length());
    }

    @Test
    public void lcsMatchesBruteForceAndTheAnswerIsACommonSubsequence() {
        Random random = new Random(21);
        for (int round = 0; round < 200; round++) {
            StringsInput in = InputParser.randomStrings(random.nextLong());
            Result r = run("lcs", in).result();
            assertEquals(in.first() + "/" + in.second(), bruteLcs(in.first(), in.second()), r.value(), 0);
            assertEquals((int) r.value(), r.text().length());
            assertTrue(isSubsequence(r.text(), in.first()));
            assertTrue(isSubsequence(r.text(), in.second()));
        }
    }

    // ---- edit distance ---------------------------------------------------------------

    @Test
    public void editDistanceKittenSitting() {
        assertEquals(3, run("edit-distance", InputParser.defaultEditStrings()).result().value(), 0);
    }

    @Test
    public void editDistanceMatchesBruteForceAndTheScriptReplaysToTheSecondText() {
        Random random = new Random(33);
        for (int round = 0; round < 200; round++) {
            StringsInput in = InputParser.randomStrings(random.nextLong());
            Result r = run("edit-distance", in).result();
            assertEquals(in.first() + "/" + in.second(), bruteEdit(in.first(), in.second()), r.value(), 0);
            // replay the script: it must consume the first text and produce the second
            StringBuilder produced = new StringBuilder();
            int consumed = 0;
            int edits = 0;
            for (String token : r.text().split(" ")) {
                String kind = token.substring(0, token.indexOf(':'));
                String arg = token.substring(token.indexOf(':') + 1);
                switch (kind) {
                    case "keep" -> {
                        assertEquals(in.first().charAt(consumed), arg.charAt(0));
                        produced.append(arg.charAt(0));
                        consumed++;
                    }
                    case "replace" -> {
                        assertEquals(in.first().charAt(consumed), arg.charAt(0));
                        produced.append(arg.charAt(2));
                        consumed++;
                        edits++;
                    }
                    case "delete" -> {
                        assertEquals(in.first().charAt(consumed), arg.charAt(0));
                        consumed++;
                        edits++;
                    }
                    case "insert" -> {
                        produced.append(arg.charAt(0));
                        edits++;
                    }
                    default -> throw new AssertionError("unknown token " + token);
                }
            }
            assertEquals(in.first().length(), consumed);
            assertEquals(in.second(), produced.toString());
            assertEquals((int) r.value(), edits);
        }
    }

    @Test
    public void equalTextsNeedNoEdits() {
        Result r = run("edit-distance", new StringsInput("ABC", "ABC")).result();
        assertEquals(0, r.value(), 0);
        assertEquals("keep:A keep:B keep:C", r.text());
    }

    @Test
    public void stringsInputRefusesBadText() {
        assertThrows(IllegalArgumentException.class, () -> new StringsInput("", "A"));
        assertThrows(IllegalArgumentException.class, () -> new StringsInput("A B", "A"));
        assertThrows(IllegalArgumentException.class, () -> new StringsInput("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi", "A"));
        assertEquals("AB", new StringsInput(" AB ", "C").first());
    }

    // ---- LIS -------------------------------------------------------------------------

    @Test
    public void lisMatchesBruteForceAndTheChosenPositionsRise() {
        Random random = new Random(44);
        for (int round = 0; round < 200; round++) {
            ArrayInput in = InputParser.randomArray(random.nextLong(), 1 + random.nextInt(11));
            Result r = run("lis", in).result();
            assertEquals(in.values().toString(), bruteLis(in.values()), r.value(), 0);
            assertEquals((int) r.value(), r.chosen().size());
            for (int i = 1; i < r.chosen().size(); i++) {
                assertTrue(r.chosen().get(i) > r.chosen().get(i - 1));
                assertTrue(in.values().get(r.chosen().get(i)) > in.values().get(r.chosen().get(i - 1)));
            }
        }
    }

    @Test
    public void lisDefaultExample() {
        Result r = run("lis", InputParser.defaultSequence()).result();
        assertEquals(4, r.value(), 0);
    }

    // ---- the contract every table trace must keep ------------------------------------

    @Test
    public void everyDpTraceIsATableWithValidLinesAndTheSameCounters() {
        List<Trace> traces = List.of(run("coin-change", InputParser.defaultCoins()), run("lcs", InputParser.defaultStrings()),
                run("edit-distance", InputParser.defaultEditStrings()), run("lis", InputParser.defaultSequence()));
        for (Trace t : traces) {
            Algorithm a = Algorithms.byId(t.algorithmId());
            assertTrue(a.id(), t.size() > 5);
            assertTrue(a.pseudocode().size() >= 5);
            for (Step s : t.steps()) {
                assertTrue(a.id(), s.frame() instanceof GridFrame);
                assertTrue(a.id() + " line " + s.line(), s.line() >= 1 && s.line() <= a.pseudocode().size());
                assertFalse(s.message().isBlank());
                assertEquals(t.step(0).counters().keySet(), s.counters().keySet());
            }
            assertEquals(t, Algorithms.byId(t.algorithmId()).run(inputOf(t.algorithmId()), Params.none()));
        }
    }

    private static com.graphlab.engine.input.Input inputOf(String id) {
        return switch (id) {
            case "coin-change" -> InputParser.defaultCoins();
            case "lcs" -> InputParser.defaultStrings();
            case "edit-distance" -> InputParser.defaultEditStrings();
            default -> InputParser.defaultSequence();
        };
    }

    @Test
    public void theTracebackHighlightsACompletePathFromTheCornerToTheStart() {
        for (String id : List.of("lcs", "edit-distance")) {
            Trace t = run(id, InputParser.defaultStrings());
            GridFrame last = (GridFrame) t.last().frame();
            int pathCells = 0;
            for (int r = 0; r < last.rows(); r++) {
                for (int c = 0; c < last.cols(); c++) {
                    pathCells += last.state(r, c) == State.PATH ? 1 : 0;
                }
            }
            assertTrue(id, pathCells >= Math.max(last.rows(), last.cols()) - 1);
            assertEquals(State.PATH, last.state(last.rows() - 1, last.cols() - 1));
        }
    }

    @Test
    public void wrongInputKindsAreRefusedWithAnExplanation() {
        assertThrows(IllegalArgumentException.class, () -> Algorithms.byId("lcs").run(Graph.builder(false).build(), Params.none()));
        assertThrows(IllegalArgumentException.class, () -> Algorithms.byId("coin-change").run(InputParser.defaultArray(), Params.none()));
        assertThrows(IllegalArgumentException.class, () -> Algorithms.byId("lis").run(InputParser.defaultCoins(), Params.none()));
        new ArrayList<>();
    }
}

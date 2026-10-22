package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MiniJsonTest {

    @Test
    public void parsesEveryKindOfValue() throws Exception {
        Object v = MiniJson.parse(" {\"a\": [1, -2.5, 1e3, true, false, null, \"x\"], \"b\": {}} ");
        Map<?, ?> map = (Map<?, ?>) v;
        List<?> list = (List<?>) map.get("a");
        assertEquals(1.0, (Double) list.get(0), 0);
        assertEquals(-2.5, (Double) list.get(1), 0);
        assertEquals(1000.0, (Double) list.get(2), 0);
        assertEquals(Boolean.TRUE, list.get(3));
        assertEquals(Boolean.FALSE, list.get(4));
        assertNull(list.get(5));
        assertEquals("x", list.get(6));
        assertTrue(((Map<?, ?>) map.get("b")).isEmpty());
    }

    @Test
    public void keepsKeysInFileOrder() throws Exception {
        Map<?, ?> map = (Map<?, ?>) MiniJson.parse("{\"z\":1,\"a\":2,\"m\":3}");
        assertEquals(List.of("z", "a", "m"), List.copyOf(map.keySet()));
    }

    @Test
    public void readsEscapesAndUnicode() throws Exception {
        assertEquals("a\"b\\c/d\n\t\u00e9\u0001", MiniJson.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\u0001\""));
        assertEquals("\uD83D\uDE00", MiniJson.parse("\"\\ud83d\\ude00\""));   // surrogate pair written as two escapes
        assertEquals("\u0985\u09BE", MiniJson.parse("\"\u0985\u09BE\""));         // Bengali, raw
    }

    @Test
    public void quoteThenParseGivesTheSameText() throws Exception {
        String[] samples = {"", "plain", "quote\" and \\ backslash", "line\nbreak\ttab\r", "\u0000\u001f", "\u2028\u2029",
                "\u0985\u09BE\u09B0\u09CD\u09A5", "\uD83D\uDE00 emoji"};
        for (String s : samples) {
            assertEquals(s, MiniJson.parse(MiniJson.quote(s)));
        }
    }

    @Test
    public void numbersAreWrittenShortAndReadBackExactly() throws Exception {
        assertEquals("7", MiniJson.number(7.0));
        assertEquals("0", MiniJson.number(-0.0));
        assertEquals("-3", MiniJson.number(-3));
        assertEquals("0.1", MiniJson.number(0.1));
        double[] values = {0.1, 1.0 / 3, 1e-7, 123456789.123456789, -2.5e-9, 1e20, Math.PI, 12345678901234.5};
        for (double d : values) {
            assertEquals(d, (Double) MiniJson.parse(MiniJson.number(d)), 0.0);
        }
    }

    @Test
    public void refusesNaNAndInfinity() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.number(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.number(Double.POSITIVE_INFINITY));
    }

    @Test
    public void rejectsBadJson() {
        String[] bad = {
                "", "   ", "{", "[1,]", "{\"a\":1,}", "{'a':1}", "{\"a\" 1}", "[1 2]", "01", "1.", ".5", "-", "+1", "1e", "1e+",
                "\"unclosed", "\"bad \\q escape\"", "\"bad \\u12 escape\"", "\"raw\ncontrol\"", "tru", "nul", "NaN", "Infinity",
                "{\"a\":1} extra", "[1] [2]", "{\"a\":1,\"a\":2}", "// comment\n{}"
        };
        for (String text : bad) {
            assertThrows("should reject: " + text, GraphFormatException.class, () -> MiniJson.parse(text));
        }
    }

    @Test
    public void errorMessagesSayWhere() {
        GraphFormatException e = assertThrows(GraphFormatException.class, () -> MiniJson.parse("[1, 2, }"));
        assertTrue(e.getMessage(), e.getMessage().contains("at character 8"));
    }

    @Test
    public void refusesRunawayNesting() {
        String deep = "[".repeat(200) + "]".repeat(200);
        GraphFormatException e = assertThrows(GraphFormatException.class, () -> MiniJson.parse(deep));
        assertTrue(e.getMessage().contains("nested too deeply"));
        // exactly at the limit is fine
        assertTrue(MiniJson.MAX_DEPTH > 10);
    }

    @Test
    public void hugeExponentsParseToInfinityForTheCallerToRefuse() throws Exception {
        assertTrue(Double.isInfinite((Double) MiniJson.parse("1e999")));
    }
}

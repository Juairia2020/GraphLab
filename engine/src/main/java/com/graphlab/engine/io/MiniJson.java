package com.graphlab.engine.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON reader and the two writing helpers the graph format needs. It exists
 * because the engine is plain Java: Android's org.json is not there on a desktop JVM, and the
 * graph files should read the same everywhere.
 *
 * <p>Reading gives {@code Map<String,Object>} (in file order), {@code List<Object>}, {@code String},
 * {@code Double} (every number), {@code Boolean} and {@code null}. It follows the JSON grammar
 * exactly: no comments, no trailing commas, no leading zeros, no NaN. Duplicate keys in one object
 * are an error. Nesting deeper than {@link #MAX_DEPTH} is refused so a hostile file cannot
 * overflow the stack.
 */
public final class MiniJson {
    public static final int MAX_DEPTH = 64;

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parses one JSON value; anything after it (other than whitespace) is an error. */
    public static Object parse(String text) throws GraphFormatException {
        MiniJson parser = new MiniJson(text);
        parser.skipSpace();
        Object value = parser.value(0);
        parser.skipSpace();
        if (parser.pos < text.length()) {
            throw parser.error("Unexpected text after the end of the JSON");
        }
        return value;
    }

    // ---- writing helpers ---------------------------------------------------------------

    /** The text as a JSON string literal, quotes included. */
    public static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    /**
     * A number as JSON text: whole numbers without ".0", others in the shortest form that reads
     * back to the same double. Locale independent.
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON cannot hold " + value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);   // also turns -0.0 into 0
        }
        return Double.toString(value);
    }

    // ---- parser ------------------------------------------------------------------------

    private Object value(int depth) throws GraphFormatException {
        if (depth > MAX_DEPTH) {
            throw error("The JSON is nested too deeply");
        }
        if (pos >= text.length()) {
            throw error("The JSON ends too early");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("Unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object(int depth) throws GraphFormatException {
        pos++;   // {
        Map<String, Object> map = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (!peek('"')) {
                throw error("Expected a quoted key");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            Object value = value(depth + 1);
            if (map.containsKey(key)) {
                throw error("The key \"" + key + "\" appears twice in one object");
            }
            map.put(key, value);
            skipSpace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array(int depth) throws GraphFormatException {
        pos++;   // [
        List<Object> list = new ArrayList<>();
        skipSpace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value(depth + 1));
            skipSpace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() throws GraphFormatException {
        pos++;   // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("A string is not closed");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("A string contains a raw control character");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("A string is not closed");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    out.append(unicodeEscape());
                    break;
                default:
                    throw error("Unknown escape \\" + e);
            }
        }
    }

    private char unicodeEscape() throws GraphFormatException {
        if (pos + 4 > text.length()) {
            throw error("A \\u escape is cut short");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos + i), 16);
            if (digit < 0) {
                throw error("A \\u escape needs four hex digits");
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return (char) value;
    }

    private Double number() throws GraphFormatException {
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        if (peek('0')) {
            pos++;
        } else if (pos < text.length() && text.charAt(pos) >= '1' && text.charAt(pos) <= '9') {
            while (pos < text.length() && Character.isDigit(text.charAt(pos)) && text.charAt(pos) < 128) {
                pos++;
            }
        } else {
            throw error("A number is expected here");
        }
        if (peek('.')) {
            pos++;
            int digits = digits();
            if (digits == 0) {
                throw error("A number needs digits after the decimal point");
            }
        }
        if (peek('e') || peek('E')) {
            pos++;
            if (peek('+') || peek('-')) {
                pos++;
            }
            if (digits() == 0) {
                throw error("A number needs digits in its exponent");
            }
        }
        return Double.valueOf(text.substring(start, pos));
    }

    private int digits() {
        int count = 0;
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
            pos++;
            count++;
        }
        return count;
    }

    private Object literal(String word, Object value) throws GraphFormatException {
        if (!text.startsWith(word, pos)) {
            throw error("Unexpected text; did you mean " + word + "?");
        }
        pos += word.length();
        return value;
    }

    private void skipSpace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) throws GraphFormatException {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private GraphFormatException error(String message) {
        return new GraphFormatException(message + " (at character " + (pos + 1) + ")");
    }
}

package com.graphlab.engine.insight;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The teaching page of one algorithm, read from a small JSON file (assets/insight/{id}.json):
 * the idea in plain words, the running time and memory, and when to use it and when not to. The
 * pseudocode is not stored here; it comes from the algorithm itself, so the Insight tab and the
 * State tab can never disagree.
 */
public record Insight(String id, String idea, String time, String space, List<String> useWhen, List<String> avoidWhen) {

    public Insight {
        useWhen = List.copyOf(useWhen);
        avoidWhen = List.copyOf(avoidWhen);
    }

    /** @throws GraphFormatException if the text is not valid JSON or a field is missing or empty */
    public static Insight parse(String json) throws GraphFormatException {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map<?, ?> map)) {
            throw new GraphFormatException("An insight file must be a JSON object.");
        }
        return new Insight(text(map, "id"), text(map, "idea"), text(map, "time"), text(map, "space"),
                list(map, "useWhen"), list(map, "avoidWhen"));
    }

    private static String text(Map<?, ?> map, String key) throws GraphFormatException {
        Object value = map.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new GraphFormatException("\"" + key + "\" is missing or empty.");
        }
        if (s.length() > 600) {
            throw new GraphFormatException("\"" + key + "\" is too long (" + s.length() + " characters).");
        }
        return s.trim();
    }

    private static List<String> list(Map<?, ?> map, String key) throws GraphFormatException {
        Object value = map.get(key);
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            throw new GraphFormatException("\"" + key + "\" must be a list with at least one entry.");
        }
        List<String> out = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof String s) || s.isBlank() || s.length() > 300) {
                throw new GraphFormatException("Every entry of \"" + key + "\" must be short text.");
            }
            out.add(s.trim());
        }
        return out;
    }
}

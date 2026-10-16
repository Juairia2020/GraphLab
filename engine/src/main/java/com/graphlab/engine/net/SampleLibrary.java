package com.graphlab.engine.net;

import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.MiniJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The sample library: a list of ready-made graphs that the app downloads (or reads from its own
 * assets when it is offline). One JSON file holds them all:
 *
 * <pre>
 * {
 *   "format": "graphlab-library",
 *   "version": 1,
 *   "graphs": [
 *     { "id": "road-network", "title": "Road network", "description": "...",
 *       "graph": { ...the same object a GraphLab graph file holds... } }
 *   ]
 * }
 * </pre>
 *
 * <p>Whatever comes from the network is untrusted, so everything is checked: the shape, sizes, the
 * ids (lower case letters, digits and dashes, unique) and every graph. A problem is reported as a
 * {@link GraphFormatException} whose message names the sample that is wrong.
 */
public final class SampleLibrary {
    public static final String FORMAT = "graphlab-library";
    public static final int VERSION = 1;
    public static final int MAX_ENTRIES = 50;
    public static final int MAX_TEXT_CHARS = 500_000;
    public static final int MAX_TITLE = 60;
    public static final int MAX_DESCRIPTION = 200;

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    /** One sample: what the list shows, and the graph to add. */
    public static final class Entry {
        private final String id;
        private final String title;
        private final String description;
        private final GraphDocument document;

        Entry(String id, String title, String description, GraphDocument document) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.document = document;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public GraphDocument document() {
            return document;
        }

        @Override
        public String toString() {
            return "Entry{" + id + ", " + title + "}";
        }
    }

    private SampleLibrary() {
    }

    /** Reads and checks a library. @throws GraphFormatException with a message fit to show */
    public static List<Entry> parse(String text) throws GraphFormatException {
        if (text == null || text.isBlank()) {
            throw new GraphFormatException("The sample library is empty.");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new GraphFormatException("The sample library is too large (more than " + MAX_TEXT_CHARS + " characters).");
        }
        Object root = MiniJson.parse(text);
        if (!(root instanceof Map)) {
            throw new GraphFormatException("A sample library must be a JSON object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> top = (Map<String, Object>) root;
        if (!FORMAT.equals(top.get("format"))) {
            throw new GraphFormatException("This JSON is not a GraphLab sample library (\"format\" should be \"" + FORMAT + "\").");
        }
        Object version = top.get("version");
        if (!(version instanceof Double) || (Double) version != Math.rint((Double) version)) {
            throw new GraphFormatException("The library's \"version\" must be a whole number.");
        }
        int v = (int) (double) (Double) version;
        if (v < 1) {
            throw new GraphFormatException("The library's \"version\" must be 1 or more.");
        }
        if (v > VERSION) {
            throw new GraphFormatException("This library needs a newer GraphLab (library version " + v
                    + ", this app reads up to " + VERSION + ").");
        }
        Object rawGraphs = top.get("graphs");
        if (!(rawGraphs instanceof List)) {
            throw new GraphFormatException("The library needs a \"graphs\" list.");
        }
        List<?> graphs = (List<?>) rawGraphs;
        if (graphs.isEmpty()) {
            throw new GraphFormatException("The library has no graphs.");
        }
        if (graphs.size() > MAX_ENTRIES) {
            throw new GraphFormatException("The library has " + graphs.size() + " graphs; the most GraphLab reads is " + MAX_ENTRIES + ".");
        }

        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < graphs.size(); i++) {
            String where = "Sample " + (i + 1);
            Object item = graphs.get(i);
            if (!(item instanceof Map)) {
                throw new GraphFormatException(where + " must be a JSON object.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            String id = text(map.get("id"), where, "id", 40, false);
            if (!ID.matcher(id).matches()) {
                throw new GraphFormatException(where + ": the id \"" + id + "\" may only use lower case letters, digits and dashes.");
            }
            if (!ids.add(id)) {
                throw new GraphFormatException(where + ": the id \"" + id + "\" is used twice.");
            }
            where = where + " (" + id + ")";
            String title = text(map.get("title"), where, "title", MAX_TITLE, false);
            String description = text(map.get("description"), where, "description", MAX_DESCRIPTION, true);
            if (!map.containsKey("graph")) {
                throw new GraphFormatException(where + " has no \"graph\".");
            }
            GraphDocument document;
            try {
                document = GraphJson.fromObject(map.get("graph"));
            } catch (GraphFormatException e) {
                throw new GraphFormatException(where + ": " + e.getMessage(), e);
            }
            entries.add(new Entry(id, title, description, document.withName(title)));
        }
        return Collections.unmodifiableList(entries);
    }

    private static String text(Object value, String where, String key, int max, boolean optional) throws GraphFormatException {
        if (value == null && optional) {
            return "";
        }
        if (!(value instanceof String) || (!optional && ((String) value).isBlank())) {
            throw new GraphFormatException(where + " needs a non-empty \"" + key + "\" text.");
        }
        String s = ((String) value).trim();
        if (s.length() > max) {
            throw new GraphFormatException(where + ": \"" + key + "\" is longer than " + max + " characters.");
        }
        return s;
    }
}

package com.graphlab.engine.net;

import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cloud gallery's record of one shared graph, and the rules for what may be published. The
 * cloud stores plain fields (text and numbers), so this class turns a graph into those fields and
 * back. Anything read back is untrusted: the graph text is parsed again and the sizes are
 * recomputed from it instead of trusting the stored numbers.
 */
public final class GalleryPost {
    public static final int MAX_AUTHOR = 24;
    public static final int MAX_JSON_CHARS = 100_000;
    public static final String DEFAULT_AUTHOR = "Student";

    // Field names in the cloud document.
    public static final String TITLE = "title";
    public static final String AUTHOR = "author";
    public static final String OWNER = "ownerUid";
    public static final String NODES = "nodes";
    public static final String EDGES = "edges";
    public static final String CREATED = "createdAt";
    public static final String JSON = "json";

    /** One shared graph as the gallery lists it. */
    public static final class Entry {
        private final String id;
        private final String title;
        private final String author;
        private final String ownerUid;
        private final long createdAt;
        private final GraphDocument document;

        Entry(String id, String title, String author, String ownerUid, long createdAt, GraphDocument document) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.ownerUid = ownerUid;
            this.createdAt = createdAt;
            this.document = document;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String author() {
            return author;
        }

        public String ownerUid() {
            return ownerUid;
        }

        /** Milliseconds since 1970. */
        public long createdAt() {
            return createdAt;
        }

        public GraphDocument document() {
            return document;
        }

        public int nodeCount() {
            return document.graph().nodeCount();
        }

        public int edgeCount() {
            return document.graph().edgeCount();
        }

        public boolean isOwnedBy(String uid) {
            return uid != null && !uid.isEmpty() && uid.equals(ownerUid);
        }
    }

    private GalleryPost() {
    }

    /** A title made only of spaces or control characters becomes this. */
    public static final String DEFAULT_TITLE = "Untitled graph";

    private static String titleOrDefault(String cleaned) {
        return cleaned.isEmpty() ? DEFAULT_TITLE : cleaned;
    }

    /** Trims, collapses spaces, drops control characters, limits the length; empty becomes "Student". */
    public static String cleanAuthor(String raw) {
        if (raw == null) {
            return DEFAULT_AUTHOR;
        }
        StringBuilder sb = new StringBuilder();
        boolean lastSpace = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c)) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                }
                lastSpace = true;
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        String s = sb.toString().trim();
        if (s.length() > MAX_AUTHOR) {
            s = s.substring(0, MAX_AUTHOR).trim();
        }
        return s.isEmpty() ? DEFAULT_AUTHOR : s;
    }

    /**
     * The fields to store for a new post.
     *
     * @throws IllegalArgumentException if the graph is empty or too large, or the owner is unknown
     */
    public static Map<String, Object> toFields(String title, String author, String ownerUid, GraphDocument doc, long now) {
        if (ownerUid == null || ownerUid.isBlank()) {
            throw new IllegalArgumentException("You need to be signed in to publish.");
        }
        if (doc.graph().nodeCount() == 0) {
            throw new IllegalArgumentException("An empty graph cannot be published.");
        }
        String cleanTitle = titleOrDefault(GraphNames.clean(title));
        String json = GraphJson.write(doc.withName(cleanTitle));
        if (json.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("This graph is too large to publish.");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(TITLE, cleanTitle);
        fields.put(AUTHOR, cleanAuthor(author));
        fields.put(OWNER, ownerUid);
        fields.put(NODES, (long) doc.graph().nodeCount());
        fields.put(EDGES, (long) doc.graph().edgeCount());
        fields.put(CREATED, now);
        fields.put(JSON, json);
        return fields;
    }

    /**
     * Reads one stored post. Numbers may arrive as any kind of number; missing optional fields get defaults.
     *
     * @throws GraphFormatException if the post is unusable (no title, or the graph text is not a valid graph)
     */
    public static Entry fromFields(String id, Map<String, Object> fields) throws GraphFormatException {
        if (id == null || id.isEmpty()) {
            throw new GraphFormatException("A post needs an id.");
        }
        Object rawTitle = fields.get(TITLE);
        if (!(rawTitle instanceof String) || ((String) rawTitle).isBlank()) {
            throw new GraphFormatException("Post " + id + " has no title.");
        }
        Object rawJson = fields.get(JSON);
        if (!(rawJson instanceof String) || ((String) rawJson).length() > MAX_JSON_CHARS) {
            throw new GraphFormatException("Post " + id + " has no usable graph.");
        }
        GraphDocument doc;
        try {
            doc = GraphJson.read((String) rawJson);
        } catch (GraphFormatException e) {
            throw new GraphFormatException("Post " + id + ": " + e.getMessage(), e);
        }
        String title = titleOrDefault(GraphNames.clean((String) rawTitle));
        Object owner = fields.get(OWNER);
        Object created = fields.get(CREATED);
        long when = created instanceof Number ? ((Number) created).longValue() : 0L;
        return new Entry(id, title, cleanAuthor(fields.get(AUTHOR) instanceof String ? (String) fields.get(AUTHOR) : null),
                owner instanceof String ? (String) owner : "", when, doc.withName(title));
    }
}

package com.graphlab.engine.io;

import java.util.Locale;

/** Picks the right reader for a file, and names files. The one entry point the app uses for import and export. */
public final class GraphFiles {
    /** Longer text is refused before parsing; a 60-node graph is a few thousand characters. */
    public static final int MAX_CHARS = 1_000_000;

    public enum Format {
        JSON("json", "application/json"),
        GRAPHML("graphml", "text/xml");

        public final String extension;
        public final String mimeType;

        Format(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    private GraphFiles() {
    }

    /** JSON starts with "{", GraphML with "<" (a byte-order mark and spaces are skipped). */
    public static Format detect(String text) throws GraphFormatException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFEFF' || Character.isWhitespace(c)) {
                continue;
            }
            if (c == '{') {
                return Format.JSON;
            }
            if (c == '<') {
                return Format.GRAPHML;
            }
            break;
        }
        throw new GraphFormatException("This file is neither GraphLab JSON nor GraphML.");
    }

    public static GraphDocument read(String text) throws GraphFormatException {
        if (text.length() > MAX_CHARS) {
            throw new GraphFormatException("This file is too large to be a graph (more than "
                    + MAX_CHARS / 1000 + " thousand characters).");
        }
        String clean = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return detect(clean) == Format.JSON ? GraphJson.read(clean) : GraphMl.read(clean);
    }

    public static String write(GraphDocument doc, Format format) {
        return format == Format.JSON ? GraphJson.write(doc) : GraphMl.write(doc);
    }

    /** A file name that is safe on every phone: letters, digits, space, dash and underscore only. */
    public static String fileName(String graphName, Format format) {
        StringBuilder base = new StringBuilder();
        for (int i = 0; i < graphName.length() && base.length() < GraphNames.MAX_LENGTH; i++) {
            char c = graphName.charAt(i);
            base.append(Character.isLetterOrDigit(c) && c < 128 || c == ' ' || c == '-' || c == '_' ? c : '_');
        }
        String name = base.toString().trim();
        if (name.isEmpty() || name.chars().allMatch(ch -> ch == '_')) {
            name = "graph";
        }
        return name + "." + format.extension;
    }

    /** "campus.graphml" becomes "campus". Used to name an imported file that has no name of its own. */
    public static String nameFromFile(String fileName) {
        if (fileName == null) {
            return GraphNames.IMPORTED;
        }
        String s = fileName;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            String ext = s.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.equals("json") || ext.equals("graphml") || ext.equals("xml") || ext.equals("txt")) {
                s = s.substring(0, dot);
            }
        }
        String cleaned = GraphNames.clean(s);
        return cleaned.isEmpty() ? GraphNames.IMPORTED : cleaned;
    }
}

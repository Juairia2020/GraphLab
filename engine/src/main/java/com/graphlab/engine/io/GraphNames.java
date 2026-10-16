package com.graphlab.engine.io;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Rules for graph names in the library: what is allowed, and how copies and new graphs are named. */
public final class GraphNames {
    public static final int MAX_LENGTH = 40;
    public static final String UNTITLED = "Untitled graph";
    public static final String IMPORTED = "Imported graph";

    public enum Check { OK, EMPTY, TOO_LONG }

    private GraphNames() {
    }

    /** Trims, turns runs of whitespace and control characters into one space, and cuts to the limit. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                space = out.length() > 0;
            } else {
                if (space) {
                    out.append(' ');
                    space = false;
                }
                out.append(c);
            }
        }
        String cleaned = out.toString();
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
            if (!cleaned.isEmpty() && Character.isHighSurrogate(cleaned.charAt(cleaned.length() - 1))) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
        }
        return cleaned;
    }

    /** What a person typed into the name field: empty is refused, and so is too long (not silently cut). */
    public static Check check(String typed) {
        String cleaned = clean(typed == null ? "" : typed);
        if (cleaned.isEmpty()) {
            return Check.EMPTY;
        }
        // Compare with the cleaned length before cutting, to tell "too long" from "fits".
        String squeezed = typed.trim().replaceAll("[\\s\\p{Cntrl}]+", " ");
        return squeezed.length() > MAX_LENGTH ? Check.TOO_LONG : Check.OK;
    }

    /** {@code base}, or "base (2)", "base (3)" ... the first one not in {@code existing} (case-insensitive). */
    public static String unique(String base, Collection<String> existing) {
        Set<String> taken = lower(existing);
        String start = base.isEmpty() ? UNTITLED : base;
        if (!taken.contains(start.toLowerCase(Locale.ROOT))) {
            return start;
        }
        for (int n = 2; ; n++) {
            String candidate = withSuffix(start, " (" + n + ")");
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    /** "Untitled graph", then "Untitled graph (2)" ... */
    public static String untitled(Collection<String> existing) {
        return unique(UNTITLED, existing);
    }

    /** "Name (copy)", then "Name (copy 2)" ... */
    public static String copyOf(String name, Collection<String> existing) {
        Set<String> taken = lower(existing);
        String base = name.isEmpty() ? UNTITLED : name;
        String first = withSuffix(base, " (copy)");
        if (!taken.contains(first.toLowerCase(Locale.ROOT))) {
            return first;
        }
        for (int n = 2; ; n++) {
            String candidate = withSuffix(base, " (copy " + n + ")");
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    /** Adds a suffix and, if that goes over the limit, shortens the name (not the suffix). */
    private static String withSuffix(String name, String suffix) {
        int room = MAX_LENGTH - suffix.length();
        String head = name.length() > room ? name.substring(0, Math.max(room, 1)).trim() : name;
        return head + suffix;
    }

    private static Set<String> lower(Collection<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) {
            out.add(n.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}

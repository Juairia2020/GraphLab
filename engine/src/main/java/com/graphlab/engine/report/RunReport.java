package com.graphlab.engine.report;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for the PDF run report: which steps to draw, and how to wrap text into lines and pages. */
public final class RunReport {
    private RunReport() {
    }

    /**
     * Picks at most {@code max} step indexes out of {@code total}. Always includes the first and the
     * last step, is strictly increasing, and is spread as evenly as whole numbers allow.
     */
    public static List<Integer> keyFrames(int total, int max) {
        if (total < 1 || max < 1) {
            throw new IllegalArgumentException("total and max must be at least 1");
        }
        List<Integer> out = new ArrayList<>();
        if (total <= max) {
            for (int i = 0; i < total; i++) {
                out.add(i);
            }
            return out;
        }
        if (max == 1) {
            out.add(total - 1);
            return out;
        }
        for (int k = 0; k < max; k++) {
            out.add((int) Math.round(k * (total - 1) / (double) (max - 1)));
        }
        return out;
    }

    /** Wraps on spaces so no line is longer than {@code width}; a word longer than that is cut. */
    public static List<String> wrap(String text, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be at least 1");
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) {
                    continue;
                }
                while (word.length() > width) {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    lines.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Splits lines into pages of at most {@code perPage} lines. An empty list gives one empty page. */
    public static List<List<String>> paginate(List<String> lines, int perPage) {
        if (perPage < 1) {
            throw new IllegalArgumentException("perPage must be at least 1");
        }
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += perPage) {
            pages.add(new ArrayList<>(lines.subList(i, Math.min(lines.size(), i + perPage))));
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }
}

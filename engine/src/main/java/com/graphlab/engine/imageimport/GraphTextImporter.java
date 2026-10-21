package com.graphlab.engine.imageimport;

import java.util.ArrayList;
import java.util.List;

/** Decides whether recognised text is an adjacency matrix or an edge list, and reads it accordingly. */
public final class GraphTextImporter {
    private GraphTextImporter() {
    }

    public static DetectedGraph parse(String text) {
        List<List<Cell>> rows = new ArrayList<>();
        List<TextGraphParser.Line> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String clean = line.replaceAll("[\\[\\]|(){};]", " ").replace(',', ' ').trim();
            if (clean.isEmpty()) {
                continue;
            }
            List<Cell> row = new ArrayList<>();
            for (String word : clean.split("\\s+")) {
                row.add(new Cell(word, 1.0));
            }
            rows.add(row);
            lines.add(new TextGraphParser.Line(line, 1.0));
        }
        return looksLikeMatrix(rows) ? MatrixParser.parseRows(rows) : TextGraphParser.parse(lines);
    }

    public static DetectedGraph parse(List<OcrToken> tokens) {
        List<List<Cell>> rows = MatrixParser.group(tokens);
        if (looksLikeMatrix(rows)) {
            return MatrixParser.parseRows(rows);
        }
        List<TextGraphParser.Line> lines = new ArrayList<>();
        for (List<Cell> row : rows) {
            StringBuilder sb = new StringBuilder();
            double conf = 1;
            for (Cell c : row) {
                sb.append(c.text()).append(' ');
                conf = Math.min(conf, c.confidence());
            }
            lines.add(new TextGraphParser.Line(sb.toString(), conf));
        }
        return TextGraphParser.parse(lines);
    }

    static boolean looksLikeMatrix(List<List<Cell>> all) {
        List<List<Cell>> rows = new ArrayList<>(all);
        if (rows.size() >= 3 && allWords(rows.get(0)) && hasNumber(rows.get(1))) {
            rows.remove(0);
        }
        int n = rows.size();
        if (n < 2) {
            return false;
        }
        int good = 0;
        int diagonalOk = 0;
        for (int i = 0; i < n; i++) {
            List<Cell> r = rows.get(i);
            boolean labelled = r.size() == n + 1 && !r.get(0).strictNumber() && !r.get(0).none();
            List<Cell> cells = labelled ? r.subList(1, r.size()) : r;
            if (cells.size() < n - 1 || cells.size() > n + 1) {
                continue;
            }
            int numeric = 0;
            for (Cell c : cells) {
                if (c.strictNumber() || c.none()) {
                    numeric++;
                }
            }
            if (numeric >= 0.7 * cells.size()) {
                good++;
            }
            if (i < cells.size()) {
                Cell d = cells.get(i);
                if (d.none() || (d.strictNumber() && isZero(d.text()))) {
                    diagonalOk++;
                }
            }
        }
        return good >= 0.7 * n && diagonalOk >= 0.6 * n;
    }

    private static boolean isZero(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.').replace('−', '-').replace('–', '-')) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean allWords(List<Cell> row) {
        for (Cell c : row) {
            if (c.strictNumber() || c.none()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNumber(List<Cell> row) {
        for (Cell c : row) {
            if (c.strictNumber()) {
                return true;
            }
        }
        return false;
    }
}

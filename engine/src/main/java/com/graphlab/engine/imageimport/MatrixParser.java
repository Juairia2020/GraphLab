package com.graphlab.engine.imageimport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a photo of an adjacency matrix (as recognised text) into a {@link DetectedGraph}.
 *
 * <p>Rules: a row of names above the grid gives the node names; a name at the start of each row is dropped; 0, a dash,
 * "inf" or an infinity sign mean "no edge"; a matrix that equals its transpose is undirected; every cell that had to be
 * guessed (O read as 0, a missing or unreadable cell) gets a low confidence so the review screen flags it.
 */
public final class MatrixParser {
    private static final double UNREADABLE = 0.3;

    private MatrixParser() {
    }

    /** From recognised words with positions: words are grouped into rows by height, then read left to right. */
    public static DetectedGraph parse(List<OcrToken> tokens) {
        return parseRows(group(tokens));
    }

    /** From plain text, one matrix row per line. */
    public static DetectedGraph parseText(String text) {
        List<List<Cell>> rows = new ArrayList<>();
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
        }
        return parseRows(rows);
    }

    static List<List<Cell>> group(List<OcrToken> tokens) {
        List<OcrToken> sorted = new ArrayList<>(tokens);
        sorted.sort(Comparator.comparingDouble(OcrToken::cy));
        List<Double> heights = new ArrayList<>();
        for (OcrToken t : sorted) {
            heights.add(t.height());
        }
        heights.sort(null);
        double median = heights.isEmpty() ? 10 : heights.get(heights.size() / 2);
        List<List<OcrToken>> rows = new ArrayList<>();
        List<Double> rowY = new ArrayList<>();
        for (OcrToken t : sorted) {
            int last = rows.size() - 1;
            if (last >= 0 && Math.abs(t.cy() - rowY.get(last)) <= 0.6 * median) {
                rows.get(last).add(t);
                List<OcrToken> r = rows.get(last);
                double sum = 0;
                for (OcrToken x : r) {
                    sum += x.cy();
                }
                rowY.set(last, sum / r.size());
            } else {
                List<OcrToken> r = new ArrayList<>();
                r.add(t);
                rows.add(r);
                rowY.add(t.cy());
            }
        }
        List<List<Cell>> out = new ArrayList<>();
        for (List<OcrToken> r : rows) {
            r.sort(Comparator.comparingDouble(OcrToken::cx));
            List<Cell> cells = new ArrayList<>();
            for (OcrToken t : r) {
                cells.add(new Cell(t.text(), t.confidence()));
            }
            out.add(cells);
        }
        return out;
    }

    private record Value(Double weight, double confidence, String reason) {
    }

    static DetectedGraph parseRows(List<List<Cell>> input) {
        List<String> warnings = new ArrayList<>();
        List<List<Cell>> rows = new ArrayList<>();
        for (List<Cell> r : input) {
            if (!r.isEmpty()) {
                rows.add(r);
            }
        }
        if (rows.isEmpty()) {
            return new DetectedGraph(List.of(), List.of(), false, 1, false, false, DetectedGraph.Source.MATRIX,
                    List.of("No text was found."));
        }
        // header: a first row without a single number or "no edge" mark
        List<Cell> header = null;
        if (isWordRow(rows.get(0)) && rows.size() > 1) {
            header = rows.remove(0);
        }
        int n0 = rows.size();
        List<Cell> rowNames = new ArrayList<>();
        List<List<Cell>> body = new ArrayList<>();
        boolean anyRowName = false;
        for (List<Cell> r : rows) {
            if (r.size() == n0 + 1 && !r.get(0).strictNumber() && !r.get(0).none()) {
                rowNames.add(r.get(0));
                body.add(new ArrayList<>(r.subList(1, r.size())));
                anyRowName = true;
            } else {
                rowNames.add(null);
                body.add(new ArrayList<>(r));
            }
        }
        int maxCols = 0;
        for (List<Cell> r : body) {
            maxCols = Math.max(maxCols, r.size());
        }
        int n = Math.max(n0, maxCols);
        if (n > 60) {
            warnings.add("The matrix is larger than 60 by 60, which GraphLab cannot hold.");
        }
        if (n > n0) {
            warnings.add("The photo shows " + n0 + " rows but " + n + " columns. Missing cells were left empty for you to check.");
        }
        while (body.size() < n) {
            body.add(new ArrayList<>());
            rowNames.add(null);
        }
        // names
        List<Cell> names = new ArrayList<>();
        if (header != null) {
            List<Cell> h = header;
            if (h.size() == n + 1) {
                h = h.subList(1, h.size());
            }
            if (h.size() == n) {
                names.addAll(h);
            } else {
                warnings.add("The name row has " + h.size() + " names for " + n + " nodes. Names were set to A, B, C ...");
            }
        } else if (anyRowName) {
            boolean all = true;
            for (int i = 0; i < n; i++) {
                all &= rowNames.get(i) != null;
            }
            if (all) {
                names.addAll(rowNames);
            }
        }
        // cells
        Value[][] m = new Value[n][n];
        List<Integer> lowCells = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Cell> r = body.get(i);
            for (int j = 0; j < n; j++) {
                if (j >= r.size()) {
                    m[i][j] = new Value(null, UNREADABLE, "this cell is missing in the photo");
                    continue;
                }
                m[i][j] = readCell(r.get(j));
            }
            if (r.size() > n) {
                warnings.add("Row " + (i + 1) + " has extra cells that were ignored.");
            }
        }
        List<DetectedGraph.Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean named = names.size() == n;
            String label = named ? names.get(i).text().trim() : GraphLabels.of(i);
            double conf = named ? names.get(i).confidence() : 1.0;
            nodes.add(new DetectedGraph.Node(i, label, 0, 0, conf, !named, conf < DetectedGraph.REVIEW_BELOW ? "the name was hard to read" : ""));
        }
        for (int i = 0; i < n; i++) {
            Value d = m[i][i];
            if (d.weight() != null && d.weight() != 0) {
                warnings.add("Cell " + nodes.get(i).label() + "," + nodes.get(i).label() + " is not 0. A node joined to itself was ignored.");
            }
        }
        // direction
        int asym = 0;
        int pairs = 0;
        boolean lowMismatch = false;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Value a = m[i][j];
                Value b = m[j][i];
                boolean pa = a.weight() != null;
                boolean pb = b.weight() != null;
                if (pa || pb) {
                    pairs++;
                }
                if (pa != pb || (pa && !a.weight().equals(b.weight()))) {
                    asym++;
                    lowMismatch |= a.confidence() < DetectedGraph.REVIEW_BELOW || b.confidence() < DetectedGraph.REVIEW_BELOW;
                }
            }
        }
        boolean directed = asym > 0;
        double dirConf = !directed ? 0.95 : (lowMismatch || asym * 4 < pairs ? 0.5 : 0.95);
        // edges
        List<DetectedGraph.Edge> edges = new ArrayList<>();
        boolean weighted = false;
        for (int i = 0; i < n; i++) {
            for (int j = directed ? 0 : i + 1; j < n; j++) {
                if (i == j) {
                    continue;
                }
                Value v = m[i][j];
                double conf = v.confidence();
                String reason = v.reason();
                Double w = v.weight();
                if (!directed) {
                    Value o = m[j][i];
                    if (w == null && o.weight() != null) {
                        continue;
                    }
                    if (w != null) {
                        conf = Math.min(conf, o.confidence());
                        if (reason.isEmpty()) {
                            reason = o.reason();
                        }
                    }
                }
                if (w == null) {
                    if (v.confidence() < DetectedGraph.REVIEW_BELOW) {
                        lowCells.add(i * n + j);
                    }
                    continue;
                }
                weighted |= w != 1.0;
                edges.add(new DetectedGraph.Edge(edges.size(), i, j, w, 1.0, conf, reason));
            }
        }
        for (int cell : lowCells) {
            warnings.add("The cell " + nodes.get(cell / n).label() + "," + nodes.get(cell % n).label()
                    + " could not be read, so no edge was made there.");
        }
        if (!weighted) {
            List<DetectedGraph.Edge> same = new ArrayList<>();
            for (DetectedGraph.Edge e : edges) {
                same.add(new DetectedGraph.Edge(e.id(), e.from(), e.to(), 1.0, 1.0, Math.max(e.weightConfidence(), 0), e.reason()));
            }
            edges = same;
        }
        return new DetectedGraph(nodes, edges, directed, dirConf, weighted, false, DetectedGraph.Source.MATRIX, warnings);
    }

    private static boolean isWordRow(List<Cell> row) {
        for (Cell c : row) {
            if (c.strictNumber() || c.none()) {
                return false;
            }
        }
        return true;
    }

    private static Value readCell(Cell c) {
        if (c.none()) {
            return new Value(null, c.confidence(), "");
        }
        if (c.strictNumber()) {
            double v = Double.parseDouble(c.text().trim().replace('−', '-').replace('–', '-').replace(',', '.'));
            return v == 0 ? new Value(null, c.confidence(), "") : new Value(v, c.confidence(), "");
        }
        ReadNumber.Value r = ReadNumber.of(c.text(), c.confidence());
        if (r == null) {
            return new Value(null, UNREADABLE, "\"" + c.text() + "\" is not a number");
        }
        return r.number() == 0 ? new Value(null, r.confidence(), "read \"" + c.text() + "\" as 0")
                : new Value(r.number(), r.confidence(), "read \"" + c.text() + "\" as " + fmt(r.number()));
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

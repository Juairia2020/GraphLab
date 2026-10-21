package com.graphlab.engine.imageimport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a photo of an edge list ("A B 4", "A-B:4", "A -> B (4)") that has been turned into text. Unlike
 * {@code EdgeListParser}, which stops at the first bad line, this one keeps going: a line it cannot read becomes a
 * warning, and a repaired number (O read as 0) gets a low confidence so it is flagged for review.
 */
public final class TextGraphParser {
    public record Line(String text, double confidence) {
    }

    private TextGraphParser() {
    }

    public static DetectedGraph parse(String text) {
        List<Line> lines = new ArrayList<>();
        for (String l : text.split("\\R")) {
            lines.add(new Line(l, 1.0));
        }
        return parse(lines);
    }

    public static DetectedGraph parse(List<Line> lines) {
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> ids = new LinkedHashMap<>();
        List<int[]> ends = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<Double> confs = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        int arrows = 0;
        int plain = 0;
        boolean anyWeight = false;
        int lineNo = 0;
        for (Line line : lines) {
            lineNo++;
            String t = line.text();
            int hash = t.indexOf('#');
            if (hash >= 0) {
                t = t.substring(0, hash);
            }
            t = t.trim();
            if (t.isEmpty()) {
                continue;
            }
            boolean arrow = t.contains("->") || t.contains("→") || t.contains("=>");
            t = t.replace("<->", " ").replace("->", " ").replace("=>", " ").replace("→", " ").replace("—", " ").replace("–", " ")
                    .replace("--", " ");
            t = t.replaceAll("(?<=[\\p{L}\\d])-(?=\\p{L})", " ");
            t = t.replaceAll("[,;:|(){}\\[\\]]", " ").trim();
            String[] w = t.isEmpty() ? new String[0] : t.split("\\s+");
            if (w.length < 2 || w.length > 3) {
                warnings.add("Line " + lineNo + " (\"" + line.text().trim() + "\") could not be read as an edge.");
                continue;
            }
            if (w[0].equals(w[1])) {
                warnings.add("Line " + lineNo + ": " + w[0] + " is joined to itself. The line was ignored.");
                continue;
            }
            Double weight = null;
            double conf = line.confidence();
            String reason = "";
            if (w.length == 3) {
                ReadNumber.Value v = ReadNumber.of(w[2], line.confidence());
                if (v == null) {
                    conf = 0.3;
                    reason = "\"" + w[2] + "\" is not a number";
                } else {
                    weight = v.number();
                    conf = v.confidence();
                    if (v.repaired()) {
                        reason = "read \"" + w[2] + "\" as " + MatrixParser.fmt(v.number());
                    }
                    anyWeight = true;
                }
            }
            if (arrow) {
                arrows++;
            } else {
                plain++;
            }
            int a = idOf(w[0], ids);
            int b = idOf(w[1], ids);
            ends.add(new int[]{a, b, arrow ? 1 : 0});
            weights.add(weight);
            confs.add(conf);
            reasons.add(reason);
        }
        boolean directed = arrows > 0;
        double dirConf = arrows > 0 && plain > 0 ? 0.5 : 0.9;
        List<DetectedGraph.Node> nodes = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ids.entrySet()) {
            boolean ok = e.getKey().length() <= 12;
            nodes.add(new DetectedGraph.Node(e.getValue(), e.getKey(), 0, 0, ok ? 1.0 : 0.5, false, ok ? "" : "the name is very long"));
        }
        List<DetectedGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < ends.size(); i++) {
            int a = ends.get(i)[0];
            int b = ends.get(i)[1];
            long key = directed || a < b ? (long) a * 1000 + b : (long) b * 1000 + a;
            if (seen.containsKey(key)) {
                warnings.add("The pair " + label(ids, a) + " and " + label(ids, b) + " appears twice. The later line was ignored.");
                continue;
            }
            seen.put(key, i);
            Double wt = weights.get(i);
            double wc = confs.get(i);
            if (wt == null && anyWeight && reasons.get(i).isEmpty()) {
                wc = 0.3;
                reasons.set(i, "no weight was read on this line");
            }
            if (wt == null && !anyWeight) {
                wt = 1.0;
                wc = 1.0;
            }
            edges.add(new DetectedGraph.Edge(edges.size(), a, b, wt, 1.0, wc, reasons.get(i)));
        }
        return new DetectedGraph(nodes, edges, directed, dirConf, anyWeight, false, DetectedGraph.Source.EDGE_LIST, warnings);
    }

    private static int idOf(String name, Map<String, Integer> ids) {
        Integer id = ids.get(name);
        if (id == null) {
            id = ids.size();
            ids.put(name, id);
        }
        return id;
    }

    private static String label(Map<String, Integer> ids, int id) {
        for (Map.Entry<String, Integer> e : ids.entrySet()) {
            if (e.getValue() == id) {
                return e.getKey();
            }
        }
        return "?";
    }
}

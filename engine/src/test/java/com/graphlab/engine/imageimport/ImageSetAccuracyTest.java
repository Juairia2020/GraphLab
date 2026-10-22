package com.graphlab.engine.imageimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.io.MiniJson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * Runs the assembler on the 20 synthetic photos in {@code tools/import-testset} (circles and lines from OpenCV Hough
 * transforms, words from Tesseract OCR; see make_set.py). The photos are drawn by a program, so the numbers measure this
 * pipeline on that set and not on real handwriting.
 */
public class ImageSetAccuracyTest {
    private static final Map<Character, Character> LOOK = Map.of('1', 'l', '0', 'O', '5', 'S', '2', 'Z', '8', 'B', '9', 'g', '6', 'G');

    static final class Outcome {
        String name;
        int level;
        boolean exact;
        boolean flagged;
        int flags;
        String why = "";
        boolean sure;
    }

    @SuppressWarnings("unchecked")
    static List<Outcome> run(String tokenKey, double noise, long seed, boolean[] confidentError) throws Exception {
        java.util.Random rnd = new java.util.Random(seed);
        String text;
        try (InputStream in = ImageSetAccuracyTest.class.getResourceAsStream("/imageimport/detections.json")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(text);
        List<Outcome> out = new ArrayList<>();
        for (Object po : (List<Object>) root.get("photos")) {
            Map<String, Object> p = (Map<String, Object>) po;
            List<Circle> circles = new ArrayList<>();
            for (Object o : (List<Object>) p.get("circles")) {
                Map<String, Object> c = (Map<String, Object>) o;
                circles.add(new Circle(d(c, "x"), d(c, "y"), d(c, "r"), d(c, "c")));
            }
            List<Segment> segs = new ArrayList<>();
            for (Object o : (List<Object>) p.get("segments")) {
                Map<String, Object> s = (Map<String, Object>) o;
                segs.add(new Segment(d(s, "x1"), d(s, "y1"), d(s, "x2"), d(s, "y2"), d(s, "c")));
            }
            List<OcrToken> toks = new ArrayList<>();
            for (Object o : (List<Object>) p.get(tokenKey)) {
                Map<String, Object> t = (Map<String, Object>) o;
                toks.add(new OcrToken((String) t.get("text"), d(t, "x"), d(t, "y"), d(t, "w"), d(t, "h"), d(t, "conf")));
            }
            boolean sure = false;
            if (noise > 0) {
                List<OcrToken> noisy = new ArrayList<>();
                for (OcrToken t : toks) {
                    double roll = rnd.nextDouble();
                    if (roll < noise / 2) {
                        continue;                                   // word not found
                    }
                    if (roll < noise) {                             // word read wrongly
                        String word = t.text();
                        String wrong = LOOK.containsKey(word.charAt(0)) && rnd.nextDouble() < 0.6 ? LOOK.get(word.charAt(0)) + word.substring(1)
                                : word.chars().allMatch(Character::isDigit) ? String.valueOf((Integer.parseInt(word) + 1 + rnd.nextInt(8)) % 100)
                                : String.valueOf((char) ('A' + rnd.nextInt(26)));
                        boolean confident = rnd.nextDouble() < 0.25;  // a quarter of the wrong words still look sure to the recogniser
                        sure |= confident && !wrong.equals(word);
                        noisy.add(new OcrToken(wrong, t.x(), t.y(), t.width(), t.height(), confident ? 0.95 : 0.35 + rnd.nextDouble() * 0.35));
                        continue;
                    }
                    noisy.add(t);
                }
                toks = noisy;
            }
            if (confidentError != null && sure) {
                confidentError[0] = true;
            }
            boolean weighted = (Boolean) p.get("weighted");
            DetectedGraph g = GeometryAssembler.assemble(circles, segs, toks, new GeometryAssembler.Options(false, true));
            ReviewSession review = new ReviewSession(g);
            Outcome o = new Outcome();
            o.sure = sure;
            o.name = (String) p.get("name");
            o.level = (int) d(p, "level");
            o.flags = review.flags().size();
            o.flagged = o.flags > 0;
            o.why = compare((Map<String, Object>) p.get("truth"), g, weighted);
            o.exact = o.why.isEmpty();
            if (o.flagged) {
                o.why += " FLAGS " + review.flags().stream().map(ReviewSession.Flag::message).collect(java.util.stream.Collectors.toList());
            }
            out.add(o);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static String compare(Map<String, Object> truth, DetectedGraph g, boolean weighted) {
        List<Object> tn = (List<Object>) truth.get("nodes");
        int[] map = new int[tn.size()];       // truth node -> result node
        for (int i = 0; i < tn.size(); i++) {
            Map<String, Object> n = (Map<String, Object>) tn.get(i);
            map[i] = -1;
            for (DetectedGraph.Node r : g.nodes()) {
                if (Math.hypot(r.x() - d(n, "x"), r.y() - d(n, "y")) < 25) {
                    map[i] = r.id();
                }
            }
            if (map[i] < 0) {
                return "node " + n.get("label") + " not found";
            }
            if (!g.nodes().get(map[i]).label().equals(n.get("label"))) {
                return "node " + n.get("label") + " read as " + g.nodes().get(map[i]).label();
            }
        }
        if (g.nodes().size() != tn.size()) {
            return "extra nodes";
        }
        Set<String> want = new TreeSet<>();
        for (Object eo : (List<Object>) truth.get("edges")) {
            Map<String, Object> e = (Map<String, Object>) eo;
            want.add(key(map[(int) d(e, "a")], map[(int) d(e, "b")], weighted ? d(e, "w") : 1.0));
        }
        Set<String> got = new TreeSet<>();
        for (DetectedGraph.Edge e : g.edges()) {
            got.add(key(e.from(), e.to(), e.weight() == null ? Double.NaN : e.weight()));
        }
        if (!want.equals(got)) {
            Set<String> missing = new TreeSet<>(want);
            missing.removeAll(got);
            Set<String> extra = new TreeSet<>(got);
            extra.removeAll(want);
            return "edges differ: missing " + missing + " extra " + extra;
        }
        return "";
    }

    private static String key(int a, int b, double w) {
        return Math.min(a, b) + "-" + Math.max(a, b) + ":" + w;
    }

    private static double d(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).doubleValue();
    }

    private static String summary(String title, List<Outcome> all, int[] counts) {
        StringBuilder report = new StringBuilder();
        for (Outcome o : all) {
            if (o.exact && !o.flagged) {
                counts[0]++;
            } else if (o.exact) {
                counts[1]++;
            } else if (o.flagged) {
                counts[2]++;
            } else {
                counts[3]++;
            }
            report.append(String.format("  %s level %d exact=%b flags=%d %s%n", o.name, o.level, o.exact, o.flags, o.why));
        }
        return title + ": " + all.size() + " photos; right and unflagged " + counts[0] + ", right but flagged " + counts[1]
                + ", wrong and flagged " + counts[2] + ", wrong and silent " + counts[3] + "\n" + report;
    }

    @Test
    public void reportAndCheck() throws Exception {
        int[] perfect = new int[4];
        List<Outcome> geometryOnly = run("tokens", 0, 1, null);
        System.out.println(summary("IMPORT SET, perfect recogniser (geometry only)", geometryOnly, perfect));
        int[] tess = new int[4];
        System.out.println(summary("IMPORT SET, real Tesseract", run("tokensTesseract", 0, 1, null), tess));
        assertEquals(20, geometryOnly.size());
        assertEquals("with a perfect recogniser every photo is read exactly", 20, perfect[0] + perfect[1]);
        assertEquals("wrong and silent with Tesseract", 0, tess[3]);

        // sensitivity: add recogniser mistakes (half missed words, half wrong words) to the perfect words, 25 trials per photo
        StringBuilder table = new StringBuilder("noise  trials  right+clean  right+flagged  wrong+flagged  wrong+silent  (silent with a sure misreading)\n");
        for (double noise : new double[]{0.0, 0.02, 0.05, 0.10}) {
            int[] c = new int[4];
            int silentWithoutSure = 0;
            int trials = 0;
            for (int t = 0; t < 25; t++) {
                for (Outcome o : run("tokens", noise, 1000 + t, new boolean[1])) {
                    trials++;
                    if (o.exact && !o.flagged) {
                        c[0]++;
                    } else if (o.exact) {
                        c[1]++;
                    } else if (o.flagged) {
                        c[2]++;
                    } else {
                        c[3]++;
                        if (!o.sure) {
                            silentWithoutSure++;
                        }
                    }
                }
            }
            table.append(String.format("%.2f   %d    %d    %d    %d    %d    (%d)%n", noise, trials, c[0], c[1], c[2], c[3], c[3] - silentWithoutSure));
            // the rule: a wrong result without any flag must come from a word the recogniser got wrong while sounding sure
            assertEquals("silent errors without a sure misreading at noise " + noise, 0, silentWithoutSure);
        }
        System.out.println(table);
    }
}

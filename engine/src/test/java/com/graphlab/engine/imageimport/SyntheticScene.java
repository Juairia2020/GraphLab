package com.graphlab.engine.imageimport;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** A drawn graph as a detector would report it, with the true answer kept for comparison. */
final class SyntheticScene {
    final List<Circle> circles = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    final List<Segment> segments = new ArrayList<>();
    final List<OcrToken> tokens = new ArrayList<>();
    final List<int[]> edges = new ArrayList<>();      // node index pairs
    final List<Integer> weights = new ArrayList<>();
    final List<Integer> weightToken = new ArrayList<>(); // index in tokens, or -1
    final List<Integer> labelToken = new ArrayList<>();
    final List<List<Integer>> edgeSegments = new ArrayList<>();
    boolean weighted;
    boolean named;

    static SyntheticScene make(Random rnd, boolean weighted, boolean named) {
        SyntheticScene s = new SyntheticScene();
        s.weighted = weighted;
        s.named = named;
        int n = 3 + rnd.nextInt(6);
        double[][] pos = new double[n][];
        for (int i = 0; i < n; i++) {
            for (int tries = 0; tries < 500; tries++) {
                double x = 60 + rnd.nextDouble() * 680;
                double y = 60 + rnd.nextDouble() * 480;
                boolean ok = true;
                for (int j = 0; j < i; j++) {
                    ok &= Math.hypot(x - pos[j][0], y - pos[j][1]) >= 150;
                }
                if (ok) {
                    pos[i] = new double[]{x, y};
                    break;
                }
            }
            if (pos[i] == null) {
                return make(rnd, weighted, named);
            }
        }
        String letters = "ABCDEFGHJKMNPRTUVWXYZ";
        List<Character> pool = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            pool.add(c);
        }
        java.util.Collections.shuffle(pool, rnd);
        double[] radius = new double[n];
        for (int i = 0; i < n; i++) {
            radius[i] = 22 + rnd.nextInt(9);
            s.circles.add(new Circle(pos[i][0], pos[i][1], radius[i], 0.95));
            String label = named ? String.valueOf(pool.get(i)) : null;
            s.labels.add(label);
            if (named) {
                s.labelToken.add(s.tokens.size());
                s.tokens.add(OcrToken.at(label, pos[i][0], pos[i][1], 0.9 + rnd.nextDouble() * 0.09));
            } else {
                s.labelToken.add(-1);
            }
        }
        // candidate pairs in random order
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs.add(new int[]{i, j});
            }
        }
        java.util.Collections.shuffle(pairs, rnd);
        List<double[]> mids = new ArrayList<>();
        int want = n - 1 + rnd.nextInt(n);
        for (int[] p : pairs) {
            if (s.edges.size() >= want) {
                break;
            }
            int a = p[0];
            int b = p[1];
            double dx = pos[b][0] - pos[a][0];
            double dy = pos[b][1] - pos[a][1];
            double d = Math.hypot(dx, dy);
            boolean ok = true;
            for (int k = 0; k < n && ok; k++) {
                if (k == a || k == b) {
                    continue;
                }
                double t = ((pos[k][0] - pos[a][0]) * dx + (pos[k][1] - pos[a][1]) * dy) / (d * d);
                double px = pos[a][0] + t * dx;
                double py = pos[a][1] + t * dy;
                if (t > -0.2 && t < 1.2 && Math.hypot(pos[k][0] - px, pos[k][1] - py) < radius[k] + 25) {
                    ok = false;
                }
            }
            double sx = pos[a][0] + dx / d * radius[a];
            double sy = pos[a][1] + dy / d * radius[a];
            double ex = pos[b][0] - dx / d * radius[b];
            double ey = pos[b][1] - dy / d * radius[b];
            double mx = (sx + ex) / 2;
            double my = (sy + ey) / 2;
            for (double[] m : mids) {
                ok &= Math.hypot(m[0] - mx, m[1] - my) >= 80;
            }
            for (int k = 0; k < n; k++) {
                ok &= Math.hypot(pos[k][0] - mx, pos[k][1] - my) >= radius[k] + 40;
            }
            if (!ok) {
                continue;
            }
            mids.add(new double[]{mx, my});
            int ei = s.edges.size();
            s.edges.add(new int[]{a, b});
            List<Integer> segIdx = new ArrayList<>();
            double j = 2;
            double[] A = {sx + (rnd.nextDouble() - .5) * j, sy + (rnd.nextDouble() - .5) * j};
            double[] B = {ex + (rnd.nextDouble() - .5) * j, ey + (rnd.nextDouble() - .5) * j};
            int style = rnd.nextInt(3);
            if (style == 1) { // broken in the middle where the number sits
                double gap = 6 + rnd.nextDouble() * 12;
                double ux = (B[0] - A[0]) / Math.hypot(B[0] - A[0], B[1] - A[1]);
                double uy = (B[1] - A[1]) / Math.hypot(B[0] - A[0], B[1] - A[1]);
                segIdx.add(s.segments.size());
                s.segments.add(new Segment(A[0], A[1], mx - ux * gap / 2, my - uy * gap / 2, 0.9));
                segIdx.add(s.segments.size());
                s.segments.add(new Segment(mx + ux * gap / 2, my + uy * gap / 2, B[0], B[1], 0.9));
            } else {
                segIdx.add(s.segments.size());
                s.segments.add(new Segment(A[0], A[1], B[0], B[1], 0.9));
                if (style == 2) { // doubled by the line detector, a little shifted and shorter
                    segIdx.add(s.segments.size());
                    s.segments.add(new Segment(A[0] + 1.5, A[1] + 1.5, B[0] - 6, B[1] - 6, 0.8));
                }
            }
            s.edgeSegments.add(segIdx);
            if (weighted) {
                int w = 1 + rnd.nextInt(30);
                s.weights.add(w);
                double nx = -dy / d;
                double ny = dx / d;
                s.weightToken.add(s.tokens.size());
                s.tokens.add(OcrToken.at(String.valueOf(w), mx + nx * 14, my + ny * 14, 0.88 + rnd.nextDouble() * 0.1));
            } else {
                s.weights.add(1);
                s.weightToken.add(-1);
            }
        }
        boolean[] touched = new boolean[n];
        for (int[] e : s.edges) {
            touched[e[0]] = true;
            touched[e[1]] = true;
        }
        for (boolean t : touched) {
            if (!t) {
                return make(rnd, weighted, named);
            }
        }
        return s;
    }

    String nameOf(int i) {
        return named ? labels.get(i) : null;
    }

    /** The true answer as a set of strings, using the given node names. */
    Set<String> truth(List<String> names) {
        Set<String> out = new TreeSet<>();
        for (int e = 0; e < edges.size(); e++) {
            out.add(edgeKey(names.get(edges.get(e)[0]), names.get(edges.get(e)[1]), weights.get(e)));
        }
        return out;
    }

    static String edgeKey(String a, String b, Number w) {
        String x = a.compareTo(b) <= 0 ? a : b;
        String y = a.compareTo(b) <= 0 ? b : a;
        return x + "-" + y + ":" + w.doubleValue();
    }

    static Set<String> read(DetectedGraph g) {
        Set<String> out = new TreeSet<>();
        for (DetectedGraph.Edge e : g.edges()) {
            out.add(edgeKey(g.nodes().get(e.from()).label(), g.nodes().get(e.to()).label(), e.weight() == null ? Double.NaN : e.weight()));
        }
        return out;
    }

    /** True when names, edges and weights match the truth. */
    boolean matches(DetectedGraph g) {
        if (g.nodes().size() != circles.size()) {
            return false;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < circles.size(); i++) {
            names.add(named ? labels.get(i) : null);
        }
        // node order in the result is left to right; compare by position
        List<String> byResult = new ArrayList<>();
        for (DetectedGraph.Node nd : g.nodes()) {
            int idx = -1;
            for (int i = 0; i < circles.size(); i++) {
                if (Math.hypot(circles.get(i).x() - nd.x(), circles.get(i).y() - nd.y()) < 1) {
                    idx = i;
                }
            }
            if (idx < 0) {
                return false;
            }
            if (named && !labels.get(idx).equals(nd.label())) {
                return false;
            }
            byResult.add(nd.label());
        }
        // truth keyed by the result's own labels (position-matched)
        Set<String> t = new TreeSet<>();
        for (int e = 0; e < edges.size(); e++) {
            String a = labelAtPosition(g, circles.get(edges.get(e)[0]));
            String b = labelAtPosition(g, circles.get(edges.get(e)[1]));
            t.add(edgeKey(a, b, weights.get(e)));
        }
        return t.equals(read(g)) && (g.weighted() == weighted);
    }

    private static String labelAtPosition(DetectedGraph g, Circle c) {
        for (DetectedGraph.Node nd : g.nodes()) {
            if (Math.hypot(c.x() - nd.x(), c.y() - nd.y()) < 1) {
                return nd.label();
            }
        }
        return "?";
    }
}

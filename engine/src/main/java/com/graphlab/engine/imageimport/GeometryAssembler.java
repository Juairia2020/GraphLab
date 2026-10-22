package com.graphlab.engine.imageimport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link DetectedGraph} from what the picture detector found: circles (nodes), line segments (edges) and
 * recognised words (names and weights). All lengths are in pixels.
 *
 * <p>Steps: merge duplicate circles; read each node's name from the word inside it; join broken or doubled line pieces
 * into whole lines (never across a node); snap each end to the nearest circle; give each line the nearest unused number
 * as its weight. Anything doubtful gets a low confidence, so nothing is guessed silently.
 */
public final class GeometryAssembler {
    /** @param directed whether the person said the graph is directed (arrowheads are not read from the picture) */
    public record Options(boolean directed, boolean weighted) {
        public static Options defaults() {
            return new Options(false, true);
        }
    }

    private static final double SNAP_FACTOR = 0.8;     // how far (in radii) from a circle an end may be and still snap
    private static final double MERGE_ANGLE = Math.toRadians(8);
    private static final double MERGE_OFFSET = 12;     // pixels
    private static final double MERGE_GAP = 40;        // pixels: a number written on the line breaks it by about this much
    private static final int MAX_LINE_NOTES = 6;
    private static final double DANGLING_MIN_RADII = 3.5; // shorter loose lines are smudges and number strokes, not missed edges

    private GeometryAssembler() {
    }

    public static DetectedGraph assemble(List<Circle> circlesIn, List<Segment> segmentsIn, List<OcrToken> tokens, Options options) {
        List<String> warnings = new ArrayList<>();
        List<Circle> circles = mergeCircles(circlesIn);
        // reading order: left to right, then top to bottom
        circles.sort(Comparator.comparingDouble(Circle::x).thenComparingDouble(Circle::y));
        int n = circles.size();
        boolean[] used = new boolean[tokens.size()];

        // node names
        String[] labels = new String[n];
        double[] labelConf = new double[n];
        boolean[] generated = new boolean[n];
        String[] nodeReason = new String[n];
        for (int i = 0; i < n; i++) {
            Circle c = circles.get(i);
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int t = 0; t < tokens.size(); t++) {
                OcrToken k = tokens.get(t);
                double d = Math.hypot(k.cx() - c.x(), k.cy() - c.y());
                if (d <= c.r() && d < bd && !k.text().isBlank()) {
                    bd = d;
                    best = t;
                }
            }
            nodeReason[i] = "";
            if (best >= 0) {
                used[best] = true;
                labels[i] = tokens.get(best).text().trim();
                labelConf[i] = tokens.get(best).confidence();
                if (labelConf[i] < DetectedGraph.REVIEW_BELOW) {
                    nodeReason[i] = "the name was hard to read";
                }
            } else {
                labels[i] = GraphLabels.of(i);
                labelConf[i] = 1.0;
                generated[i] = true;
            }
            labelConf[i] = Math.min(labelConf[i], c.confidence());
            if (c.confidence() < DetectedGraph.REVIEW_BELOW && nodeReason[i].isEmpty()) {
                nodeReason[i] = "this circle was not clearly seen";
            }
        }
        // when some circles had names and others did not, an invented name may be wrong: flag it
        boolean anyNamed = false;
        for (int i = 0; i < n; i++) {
            anyNamed |= !generated[i];
        }
        if (anyNamed) {
            for (int i = 0; i < n; i++) {
                if (generated[i]) {
                    labelConf[i] = Math.min(labelConf[i], 0.6);
                    nodeReason[i] = "no name was read in this circle, so it was named " + labels[i];
                }
            }
        }
        // duplicate names: keep the first, rename the others and flag them
        Map<String, Integer> firstOf = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Integer f = firstOf.putIfAbsent(labels[i], i);
            if (f != null) {
                String fresh = GraphLabels.of(i);
                while (firstOf.containsKey(fresh)) {
                    fresh = fresh + "'";
                }
                firstOf.put(fresh, i);
                nodeReason[i] = "the name " + labels[i] + " is used twice";
                labels[i] = fresh;
                generated[i] = true;
                labelConf[i] = Math.min(labelConf[i], 0.4);
            }
        }

        // lines
        List<Segment> segs = mergeSegments(segmentsIn, circles);
        record Cand(int a, int b, double conf, String reason, double mx, double my, double len) {
        }
        List<Cand> cands = new ArrayList<>();
        List<Segment> dangling = new ArrayList<>();
        Map<Long, Integer> byPair = new HashMap<>();
        for (Segment s : segs) {
            int a = snap(circles, s.x1(), s.y1());
            int b = snap(circles, s.x2(), s.y2());
            if (a < 0 || b < 0 || a == b) {
                if (s.length() > DANGLING_MIN_RADII * averageRadius(circles)) {
                    dangling.add(s);
                }
                continue;
            }
            double conf = s.confidence();
            String reason = "";
            Circle ca = circles.get(a);
            Circle cb = circles.get(b);
            double ea = Math.hypot(s.x1() - ca.x(), s.y1() - ca.y()) - ca.r();
            double eb = Math.hypot(s.x2() - cb.x(), s.y2() - cb.y()) - cb.r();
            double gap = Math.max(Math.max(ea, eb), 0);
            double snapLimit = SNAP_FACTOR * Math.max(ca.r(), cb.r());
            double quality = 1 - 0.5 * Math.min(1, gap / snapLimit);
            conf = Math.min(conf, quality);
            if (quality < DetectedGraph.REVIEW_BELOW) {
                reason = "the line does not quite touch the circle";
            }
            if (conf < DetectedGraph.REVIEW_BELOW && reason.isEmpty()) {
                reason = "the line was not clearly seen";
            }
            int through = passesThrough(circles, a, b);
            if (through >= 0) {
                conf = Math.min(conf, 0.5);
                reason = "the line passes over node " + labels[through];
            }
            // the weight is read at the middle of the line between the two circles
            double dx = cb.x() - ca.x();
            double dy = cb.y() - ca.y();
            double d = Math.hypot(dx, dy);
            double sx = ca.x() + dx / d * ca.r();
            double sy = ca.y() + dy / d * ca.r();
            double ex = cb.x() - dx / d * cb.r();
            double ey = cb.y() - dy / d * cb.r();
            long key = a < b ? (long) a * 100000 + b : (long) b * 100000 + a;
            Integer old = byPair.get(key);
            Cand c = new Cand(a, b, conf, reason, (sx + ex) / 2, (sy + ey) / 2, Math.hypot(ex - sx, ey - sy));
            if (old != null) {
                if (cands.get(old).conf() < conf) {
                    cands.set(old, c);
                }
                continue;
            }
            byPair.put(key, cands.size());
            cands.add(c);
        }

        dangling.sort(Comparator.comparingDouble(Segment::length).reversed());
        for (int i = 0; i < dangling.size() && i < MAX_LINE_NOTES; i++) {
            Segment s = dangling.get(i);
            warnings.add("A line near (" + Math.round((s.x1() + s.x2()) / 2) + ", " + Math.round((s.y1() + s.y2()) / 2)
                    + ") does not join two circles and was ignored.");
        }
        if (dangling.size() > MAX_LINE_NOTES) {
            warnings.add((dangling.size() - MAX_LINE_NOTES) + " more short lines did not join two circles and were ignored.");
        }

        // weights: nearest unused number to each line's middle, each number used once
        double[] weight = new double[cands.size()];
        double[] wconf = new double[cands.size()];
        String[] wreason = new String[cands.size()];
        boolean[] has = new boolean[cands.size()];
        record Pair(int cand, int token, double dist, double value, double conf, boolean repaired) {
        }
        List<Pair> pairs = new ArrayList<>();
        for (int t = 0; t < tokens.size(); t++) {
            if (used[t]) {
                continue;
            }
            ReadNumber.Value v = ReadNumber.of(tokens.get(t).text(), tokens.get(t).confidence());
            if (v == null) {
                continue;
            }
            for (int c = 0; c < cands.size(); c++) {
                double d = Math.hypot(tokens.get(t).cx() - cands.get(c).mx(), tokens.get(t).cy() - cands.get(c).my());
                double reach = Math.max(30, 0.4 * cands.get(c).len());
                if (d <= reach) {
                    pairs.add(new Pair(c, t, d / reach, v.number(), v.confidence(), v.repaired()));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::dist));
        boolean[] tokenTaken = new boolean[tokens.size()];
        for (Pair p : pairs) {
            if (has[p.cand()] || tokenTaken[p.token()]) {
                continue;
            }
            has[p.cand()] = true;
            tokenTaken[p.token()] = true;
            used[p.token()] = true;
            weight[p.cand()] = p.value();
            double conf = p.conf() * (1 - 0.2 * p.dist());
            String reason = p.repaired() ? "read \"" + tokens.get(p.token()).text() + "\" as " + MatrixParser.fmt(p.value()) : "";
            // a number about as close to another line is probably ambiguous
            for (int c = 0; c < cands.size(); c++) {
                if (c == p.cand()) {
                    continue;
                }
                double d2 = Math.hypot(tokens.get(p.token()).cx() - cands.get(c).mx(), tokens.get(p.token()).cy() - cands.get(c).my());
                double d1 = p.dist() * Math.max(30, 0.4 * cands.get(p.cand()).len());
                if (d2 < 1.5 * Math.max(d1, 1) && d2 <= Math.max(30, 0.4 * cands.get(c).len())) {
                    conf = Math.min(conf, 0.6);
                    reason = "the number could belong to two lines";
                    break;
                }
            }
            wconf[p.cand()] = conf;
            wreason[p.cand()] = reason;
        }
        boolean anyWeight = false;
        for (boolean h : has) {
            anyWeight |= h;
        }
        boolean weighted = options.weighted() && anyWeight;
        for (int t = 0; t < tokens.size(); t++) {
            if (!used[t] && ReadNumber.of(tokens.get(t).text(), 1) != null && weighted) {
                warnings.add("The number \"" + tokens.get(t).text().trim() + "\" near (" + Math.round(tokens.get(t).cx()) + ", "
                        + Math.round(tokens.get(t).cy()) + ") was not matched to any line.");
            }
        }

        boolean[] touched = new boolean[n];
        for (Cand c : cands) {
            touched[c.a()] = true;
            touched[c.b()] = true;
        }
        for (int i = 0; i < n; i++) {
            if (!touched[i] && n > 1) {
                labelConf[i] = Math.min(labelConf[i], 0.7);
                if (nodeReason[i].isEmpty()) {
                    nodeReason[i] = "no line touches this circle";
                }
            }
        }
        List<DetectedGraph.Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Circle c = circles.get(i);
            nodes.add(new DetectedGraph.Node(i, labels[i], c.x(), c.y(), labelConf[i], generated[i], nodeReason[i]));
        }
        List<DetectedGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < cands.size(); i++) {
            Cand c = cands.get(i);
            Double w;
            double wc;
            String reason = c.reason();
            if (!weighted) {
                w = 1.0;
                wc = 1.0;
            } else if (has[i]) {
                w = weight[i];
                wc = wconf[i];
                if (reason.isEmpty()) {
                    reason = wreason[i];
                }
            } else {
                w = null;
                wc = 0.3;
                if (reason.isEmpty()) {
                    reason = "no weight was found near this line";
                }
            }
            edges.add(new DetectedGraph.Edge(i, c.a(), c.b(), w, c.conf(), wc, reason));
        }
        return new DetectedGraph(nodes, edges, options.directed(), 1.0, weighted, true, DetectedGraph.Source.PICTURE, warnings);
    }

    /** Where the app should look for text: inside each circle (names) and beside the middle of each line (weights). */
    public record Probes(List<Circle> circles, List<double[]> weightSpots) {
    }

    /**
     * Finds the places to read: the merged circles (the same ones {@link #assemble} uses, in the same order) and the
     * middle of every line that joins two circles. Each spot is {x, y, reach}: the app reads text inside that circle of
     * reach pixels. The recogniser then runs on a few small pictures instead of the whole photo, which is more accurate
     * for a lone digit.
     */
    public static Probes probes(List<Circle> circlesIn, List<Segment> segmentsIn) {
        List<Circle> circles = mergeCircles(circlesIn);
        circles.sort(Comparator.comparingDouble(Circle::x).thenComparingDouble(Circle::y));
        List<double[]> spots = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Segment s : mergeSegments(segmentsIn, circles)) {
            int a = snap(circles, s.x1(), s.y1());
            int b = snap(circles, s.x2(), s.y2());
            if (a < 0 || b < 0 || a == b || !seen.add(a < b ? (long) a * 100000 + b : (long) b * 100000 + a)) {
                continue;
            }
            Circle ca = circles.get(a);
            Circle cb = circles.get(b);
            double dx = cb.x() - ca.x();
            double dy = cb.y() - ca.y();
            double d = Math.hypot(dx, dy);
            double mx = (ca.x() + dx / d * ca.r() + cb.x() - dx / d * cb.r()) / 2;
            double my = (ca.y() + dy / d * ca.r() + cb.y() - dy / d * cb.r()) / 2;
            double len = d - ca.r() - cb.r();
            spots.add(new double[]{mx, my, Math.max(30, 0.4 * len)});
        }
        return new Probes(circles, spots);
    }

    static List<Circle> mergeCircles(List<Circle> in) {
        List<Circle> sorted = new ArrayList<>(in);
        sorted.sort(Comparator.comparingDouble(Circle::confidence).reversed());
        List<Circle> kept = new ArrayList<>();
        for (Circle c : sorted) {
            boolean dup = false;
            for (int i = 0; i < kept.size(); i++) {
                Circle k = kept.get(i);
                double d = Math.hypot(c.x() - k.x(), c.y() - k.y());
                if (d < 0.7 * Math.max(c.r(), k.r())) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                kept.add(c);
            }
        }
        // circles that overlap without being duplicates are suspicious: lower their confidence
        List<Circle> out = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Circle c = kept.get(i);
            boolean overlap = false;
            for (int j = 0; j < kept.size(); j++) {
                if (i != j && Math.hypot(c.x() - kept.get(j).x(), c.y() - kept.get(j).y()) < c.r() + kept.get(j).r()) {
                    overlap = true;
                }
            }
            out.add(overlap ? new Circle(c.x(), c.y(), c.r(), Math.min(c.confidence(), 0.6)) : c);
        }
        return out;
    }

    /** Joins doubled and broken pieces of one straight line. Never joins across a circle. */
    static List<Segment> mergeSegments(List<Segment> in, List<Circle> circles) {
        List<Segment> segs = new ArrayList<>();
        for (Segment s : in) {
            if (s.length() > 0) {
                segs.add(s);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < segs.size(); i++) {
                for (int j = i + 1; j < segs.size(); j++) {
                    Segment m = tryMerge(segs.get(i), segs.get(j), circles);
                    if (m != null) {
                        segs.set(i, m);
                        segs.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return segs;
    }

    private static Segment tryMerge(Segment a, Segment b, List<Circle> circles) {
        double ang = Math.atan2(a.y2() - a.y1(), a.x2() - a.x1());
        double angB = Math.atan2(b.y2() - b.y1(), b.x2() - b.x1());
        double diff = Math.abs(ang - angB) % Math.PI;
        diff = Math.min(diff, Math.PI - diff);
        if (diff > MERGE_ANGLE) {
            return null;
        }
        double ux = Math.cos(ang);
        double uy = Math.sin(ang);
        // offset of b's ends from a's line
        double o1 = Math.abs((b.x1() - a.x1()) * -uy + (b.y1() - a.y1()) * ux);
        double o2 = Math.abs((b.x2() - a.x1()) * -uy + (b.y2() - a.y1()) * ux);
        if (Math.max(o1, o2) > MERGE_OFFSET) {
            return null;
        }
        double[] pa = {proj(a.x1(), a.y1(), a, ux, uy), proj(a.x2(), a.y2(), a, ux, uy)};
        double[] pb = {proj(b.x1(), b.y1(), a, ux, uy), proj(b.x2(), b.y2(), a, ux, uy)};
        double aLo = Math.min(pa[0], pa[1]);
        double aHi = Math.max(pa[0], pa[1]);
        double bLo = Math.min(pb[0], pb[1]);
        double bHi = Math.max(pb[0], pb[1]);
        double gap = Math.max(bLo - aHi, aLo - bHi);
        if (gap > MERGE_GAP) {
            return null;
        }
        if (gap > 0) {
            double from = Math.min(aHi, bHi);
            double to = Math.max(aLo, bLo);
            for (Circle c : circles) {
                double cp = proj(c.x(), c.y(), a, ux, uy);
                double off = Math.abs((c.x() - a.x1()) * -uy + (c.y() - a.y1()) * ux);
                if (cp > from && cp < to && off < c.r()) {
                    return null;
                }
            }
        }
        double lo = Math.min(aLo, bLo);
        double hi = Math.max(aHi, bHi);
        return new Segment(a.x1() + ux * lo, a.y1() + uy * lo, a.x1() + ux * hi, a.y1() + uy * hi,
                Math.max(a.confidence(), b.confidence()));
    }

    private static double proj(double x, double y, Segment a, double ux, double uy) {
        return (x - a.x1()) * ux + (y - a.y1()) * uy;
    }

    private static int snap(List<Circle> circles, double x, double y) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < circles.size(); i++) {
            Circle c = circles.get(i);
            double d = Math.hypot(x - c.x(), y - c.y()) - c.r();
            if (d <= SNAP_FACTOR * c.r() && d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static int passesThrough(List<Circle> circles, int a, int b) {
        Circle ca = circles.get(a);
        Circle cb = circles.get(b);
        double dx = cb.x() - ca.x();
        double dy = cb.y() - ca.y();
        double len2 = dx * dx + dy * dy;
        for (int i = 0; i < circles.size(); i++) {
            if (i == a || i == b) {
                continue;
            }
            Circle c = circles.get(i);
            double t = ((c.x() - ca.x()) * dx + (c.y() - ca.y()) * dy) / len2;
            if (t <= 0 || t >= 1) {
                continue;
            }
            double px = ca.x() + t * dx;
            double py = ca.y() + t * dy;
            if (Math.hypot(c.x() - px, c.y() - py) < 0.7 * c.r()) {
                return i;
            }
        }
        return -1;
    }

    private static double averageRadius(List<Circle> circles) {
        if (circles.isEmpty()) {
            return 10;
        }
        double s = 0;
        for (Circle c : circles) {
            s += c.r();
        }
        return s / circles.size();
    }
}

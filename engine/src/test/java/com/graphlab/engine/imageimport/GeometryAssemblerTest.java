package com.graphlab.engine.imageimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class GeometryAssemblerTest {
    private static DetectedGraph run(SyntheticScene s, List<Circle> c, List<Segment> g, List<OcrToken> t) {
        return GeometryAssembler.assemble(c, g, t, GeometryAssembler.Options.defaults());
    }

    private static DetectedGraph run(SyntheticScene s) {
        return run(s, s.circles, s.segments, s.tokens);
    }

    private static boolean flagged(DetectedGraph g) {
        return !new ReviewSession(g).flags().isEmpty();
    }

    @Test
    public void cleanScenesAreReadExactlyAndNothingIsFlagged() {
        Random rnd = new Random(7);
        for (int i = 0; i < 400; i++) {
            SyntheticScene s = SyntheticScene.make(rnd, rnd.nextInt(4) != 0, rnd.nextBoolean());
            DetectedGraph g = run(s);
            assertTrue("scene " + i + ": " + g.edges() + " warnings " + g.warnings(), s.matches(g));
            assertEquals("scene " + i + " flags " + new ReviewSession(g).flags(), List.of(), new ReviewSession(g).flags());
        }
    }

    @Test
    public void duplicateCirclesAreMerged() {
        Random rnd = new Random(3);
        SyntheticScene s = SyntheticScene.make(rnd, true, true);
        List<Circle> c = new ArrayList<>(s.circles);
        for (Circle x : s.circles) {
            c.add(new Circle(x.x() + 3, x.y() - 2, x.r() + 1, 0.7));
        }
        DetectedGraph g = run(s, c, s.segments, s.tokens);
        assertTrue(s.matches(g));
        assertFalse(flagged(g));
    }

    @Test
    public void aLineThroughANodeIsNotJoinedAcrossIt() {
        // two collinear edges A-B and B-C must stay two edges
        List<Circle> c = List.of(new Circle(100, 100, 25, .9), new Circle(300, 100, 25, .9), new Circle(500, 100, 25, .9));
        List<Segment> g = List.of(new Segment(125, 100, 275, 100, .9), new Segment(325, 100, 475, 100, .9));
        List<OcrToken> t = List.of(OcrToken.at("A", 100, 100, .9), OcrToken.at("B", 300, 100, .9), OcrToken.at("C", 500, 100, .9),
                OcrToken.at("4", 200, 85, .9), OcrToken.at("6", 400, 85, .9));
        DetectedGraph d = GeometryAssembler.assemble(c, g, t, GeometryAssembler.Options.defaults());
        assertEquals(2, d.edges().size());
        assertEquals(4.0, d.edges().get(0).weight(), 0);
        assertEquals(6.0, d.edges().get(1).weight(), 0);
        assertTrue(new ReviewSession(d).flags().isEmpty());
    }

    @Test
    public void aLongLineOverANodeIsFlagged() {
        List<Circle> c = List.of(new Circle(100, 100, 25, .9), new Circle(300, 100, 25, .9), new Circle(500, 100, 25, .9));
        List<Segment> g = List.of(new Segment(125, 100, 475, 100, .9));
        List<OcrToken> t = List.of(OcrToken.at("A", 100, 100, .9), OcrToken.at("B", 300, 100, .9), OcrToken.at("C", 500, 100, .9),
                OcrToken.at("5", 300, 70, .9));
        DetectedGraph d = GeometryAssembler.assemble(c, g, t, GeometryAssembler.Options.defaults());
        assertTrue(flagged(d));
    }

    @Test
    public void unweightedPictureHasWeightOne() {
        Random rnd = new Random(5);
        SyntheticScene s = SyntheticScene.make(rnd, false, true);
        DetectedGraph g = run(s);
        assertFalse(g.weighted());
        for (DetectedGraph.Edge e : g.edges()) {
            assertEquals(1.0, e.weight(), 0);
        }
    }

    @Test
    public void numberCloseToTwoLinesIsFlagged() {
        // two parallel lines 30 px apart with one number between them
        List<Circle> c = List.of(new Circle(100, 100, 22, .9), new Circle(400, 100, 22, .9), new Circle(100, 130, 22, .9), new Circle(400, 130, 22, .9));
        List<Segment> g = List.of(new Segment(122, 100, 378, 100, .9), new Segment(122, 130, 378, 130, .9));
        List<OcrToken> t = List.of(OcrToken.at("5", 250, 115, .9));
        DetectedGraph d = GeometryAssembler.assemble(c, g, t, GeometryAssembler.Options.defaults());
        assertTrue(flagged(d));
    }

    @Test
    public void everyDetectedErrorIsFlagged() {
        // inject one fault into a clean scene. The result is either still exactly right, or something is flagged.
        // (A line the detector never saw, or a digit misread with high confidence, cannot be flagged by any program.)
        Random rnd = new Random(99);
        int[] caught = new int[8];
        int[] wrongFlagged = new int[8];
        for (int iter = 0; iter < 800; iter++) {
            SyntheticScene s = SyntheticScene.make(rnd, true, rnd.nextInt(3) != 0);
            List<Circle> c = new ArrayList<>(s.circles);
            List<Segment> g = new ArrayList<>(s.segments);
            List<OcrToken> t = new ArrayList<>(s.tokens);
            int fault = rnd.nextInt(8);
            int e = rnd.nextInt(s.edges.size());
            switch (fault) {
                case 0: { // low-confidence number
                    int k = s.weightToken.get(e);
                    OcrToken o = t.get(k);
                    t.set(k, new OcrToken(o.text(), o.x(), o.y(), o.width(), o.height(), 0.4));
                    break;
                }
                case 1: // number not found
                    t.set(s.weightToken.get(e), new OcrToken("", 0, 0, 1, 1, 0.9));
                    break;
                case 2: { // a digit read as a letter and repaired
                    int k = s.weightToken.get(e);
                    OcrToken o = t.get(k);
                    String text = o.text();
                    String bad = text.replace('1', 'l').replace('0', 'O').replace('5', 'S');
                    if (bad.equals(text)) {
                        bad = "l" + text;       // 1x: a different number, but repaired, so still flagged
                    }
                    t.set(k, new OcrToken(bad, o.x(), o.y(), o.width(), o.height(), o.confidence()));
                    break;
                }
                case 3: { // spurious line from a circle into empty space
                    Circle a = c.get(rnd.nextInt(c.size()));
                    boolean placed = false;
                    for (int tries = 0; tries < 30 && !placed; tries++) {
                        double ang = rnd.nextDouble() * 2 * Math.PI;
                        double len = 120 + rnd.nextDouble() * 60;
                        double ex = a.x() + Math.cos(ang) * (a.r() + len);
                        double ey = a.y() + Math.sin(ang) * (a.r() + len);
                        boolean clear = true;
                        for (Circle o : c) {
                            clear &= Math.hypot(ex - o.x(), ey - o.y()) > o.r() + 40;
                        }
                        if (clear) {
                            g.add(new Segment(a.x() + Math.cos(ang) * a.r(), a.y() + Math.sin(ang) * a.r(), ex, ey, 0.8));
                            placed = true;
                        }
                    }
                    if (!placed) {
                        continue;
                    }
                    break;
                }
                case 4: { // the line stops short of one circle
                    for (int si : s.edgeSegments.get(e)) {
                        Segment o = g.get(si);
                        double len = o.length();
                        double cut = 9 + rnd.nextDouble() * 21;
                        g.set(si, new Segment(o.x1(), o.y1(), o.x2() + (o.x1() - o.x2()) / len * cut, o.y2() + (o.y1() - o.y2()) / len * cut, o.confidence()));
                    }
                    break;
                }
                case 5: { // the name of one node is not read
                    if (!s.named) {
                        continue;
                    }
                    t.set(s.labelToken.get(rnd.nextInt(c.size())), new OcrToken("", 0, 0, 1, 1, 0.9));
                    break;
                }
                case 6: // an extra circle that no line touches
                    c.add(new Circle(20 + rnd.nextInt(10), 590, 24, 0.9));
                    break;
                default: // a circle is missed
                    c.remove(rnd.nextInt(c.size()));
                    break;
            }
            // blank tokens stand for "nothing read": remove them
            t.removeIf(x -> x.text().isEmpty());
            DetectedGraph r = run(s, c, g, t);
            boolean right = s.matches(r);
            boolean flags = flagged(r);
            if (fault == 3 && GeometryAssembler.mergeSegments(g, c).size() == GeometryAssembler.mergeSegments(s.segments, c).size()) {
                continue; // the extra line fell on top of a real line and merged with it: nothing was added
            }
            caught[fault]++;
            if (flags) {
                wrongFlagged[fault]++;
            }
            assertTrue("fault " + fault + " iter " + iter + " silently wrong: " + r.edges() + " " + r.warnings(), flags || right);
        }
        // faults that change the answer must be flagged every time, not just "not silently wrong"
        for (int f : new int[]{0, 1, 3, 6}) {
            assertEquals("fault " + f + " must always be flagged", caught[f], wrongFlagged[f]);
        }
    }

    @Test
    public void emptyInputsGiveAnEmptyGraph() {
        DetectedGraph d = GeometryAssembler.assemble(List.of(), List.of(), List.of(), GeometryAssembler.Options.defaults());
        assertEquals(0, d.nodes().size());
        assertNull(null);
    }

    @Test
    public void duplicateNamesAreRenamedAndFlagged() {
        List<Circle> c = List.of(new Circle(100, 100, 25, .9), new Circle(300, 100, 25, .9));
        List<Segment> g = List.of(new Segment(125, 100, 275, 100, .9));
        List<OcrToken> t = List.of(OcrToken.at("A", 100, 100, .9), OcrToken.at("A", 300, 100, .9));
        DetectedGraph d = GeometryAssembler.assemble(c, g, t, GeometryAssembler.Options.defaults());
        assertFalse(d.nodes().get(0).label().equals(d.nodes().get(1).label()));
        assertTrue(flagged(d));
    }

    @Test
    public void probesPointAtTheMiddleOfEveryRealLine() {
        Random rnd = new Random(21);
        for (int i = 0; i < 200; i++) {
            SyntheticScene s = SyntheticScene.make(rnd, true, true);
            GeometryAssembler.Probes p = GeometryAssembler.probes(s.circles, s.segments);
            assertEquals(s.circles.size(), p.circles().size());
            assertEquals("scene " + i, s.edges.size(), p.weightSpots().size());
            for (int e = 0; e < s.edges.size(); e++) {
                OcrToken weight = s.tokens.get(s.weightToken.get(e));
                boolean near = false;
                for (double[] spot : p.weightSpots()) {
                    near |= Math.hypot(spot[0] - weight.cx(), spot[1] - weight.cy()) <= spot[2];
                }
                assertTrue("scene " + i + " edge " + e + " has a probe near its number", near);
            }
        }
    }
}

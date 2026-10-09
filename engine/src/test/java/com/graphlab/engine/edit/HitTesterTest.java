package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class HitTesterTest {
    private GraphEditor ed;
    private Viewport vp;

    /** Nodes A(0,0) B(200,0) C(100,150); edges A-B and B-C. Density 1, zoom 1, 400x400 view centred on (100, 75). */
    @Before
    public void setUp() {
        ed = new GraphEditor(false, true);
        int a = ed.addNode(0, 0);
        int b = ed.addNode(200, 0);
        int c = ed.addNode(100, 150);
        ed.addEdge(a, b, 1);
        ed.addEdge(b, c, 2);
        vp = new Viewport();
        vp.setSize(400, 400);
        vp.set(1.0, 100, 75);
    }

    private double sx(double wx) {
        return vp.worldToScreenX(wx);
    }

    private double sy(double wy) {
        return vp.worldToScreenY(wy);
    }

    @Test
    public void findsTheNodeUnderTheFinger() {
        assertEquals(0, HitTester.nodeAt(ed, vp, sx(0) + 5, sy(0) - 5, 24, -1));
        assertEquals(2, HitTester.nodeAt(ed, vp, sx(100), sy(150), 24, -1));
    }

    @Test
    public void missesEmptySpace() {
        assertEquals(-1, HitTester.nodeAt(ed, vp, sx(100), sy(0), 24, -1));
    }

    @Test
    public void slopMakesSmallNodesEasyToHit() {
        vp.set(Viewport.MIN_ZOOM, 100, 75);    // zoomed far out: drawn radius is only 6.6 px
        double x = sx(0) + 20;                 // 20 px away: outside the drawn node, inside the 24 px slop
        assertEquals(0, HitTester.nodeAt(ed, vp, x, sy(0), 24, -1));
        assertEquals(-1, HitTester.nodeAt(ed, vp, sx(0) + 30, sy(0), 24, -1));
    }

    @Test
    public void excludedNodeIsIgnored() {
        assertEquals(-1, HitTester.nodeAt(ed, vp, sx(0), sy(0), 24, 0));
    }

    @Test
    public void nearestOfTwoOverlappingHitAreasWins() {
        GraphEditor close = new GraphEditor(false, true);
        close.addNode(0, 0);
        close.addNode(40, 0);
        Viewport v = new Viewport();
        v.setSize(400, 400);
        v.set(1.0, 20, 0);
        assertEquals(1, HitTester.nodeAt(close, v, v.worldToScreenX(30), v.worldToScreenY(0), 24, -1));
    }

    @Test
    public void findsTheEdgeUnderTheFinger() {
        assertEquals(0, HitTester.edgeAt(ed, vp, sx(100), sy(0) + 8, 20));   // middle of A-B
        assertEquals(1, HitTester.edgeAt(ed, vp, sx(150) + 4, sy(75), 20));  // middle of B-C
    }

    @Test
    public void edgeMissOutsideTheSlop() {
        assertEquals(-1, HitTester.edgeAt(ed, vp, sx(100), sy(0) + 60, 20));
    }

    @Test
    public void curvedReciprocalEdgesAreHitOnTheirOwnCurve() {
        GraphEditor d = new GraphEditor(true, true);
        int a = d.addNode(0, 0);
        int b = d.addNode(200, 0);
        int ab = d.addEdge(a, b, 1);
        int ba = d.addEdge(b, a, 2);
        Viewport v = new Viewport();
        v.setSize(400, 400);
        v.set(1.0, 100, 0);
        EdgeShape shape = new EdgeShape();
        shape.set((float) v.worldToScreenX(0), (float) v.worldToScreenY(0),
                (float) v.worldToScreenX(200), (float) v.worldToScreenY(0),
                (float) EditorMetrics.RECIPROCAL_BEND, (float) EditorMetrics.NODE_RADIUS);
        assertEquals(ab, HitTester.edgeAt(d, v, shape.midX, shape.midY, 8));
        assertEquals(ba, HitTester.edgeAt(d, v, shape.midX, 2 * v.worldToScreenY(0) - shape.midY, 8));
    }
}

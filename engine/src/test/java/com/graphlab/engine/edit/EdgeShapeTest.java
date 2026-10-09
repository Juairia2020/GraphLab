package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EdgeShapeTest {
    private static final float EPS = 1e-3f;

    @Test
    public void straightEdgeIsPulledBackToTheNodeRims() {
        EdgeShape s = new EdgeShape();
        s.set(0, 0, 100, 0, 0f, 20f);
        assertTrue(s.visible);
        assertFalse(s.curved);
        assertEquals(20, s.startX, EPS);
        assertEquals(80, s.endX, EPS);
        assertEquals(50, s.midX, EPS);
        assertEquals(1, s.dirX, EPS);
        assertEquals(0, s.dirY, EPS);
    }

    @Test
    public void touchingNodesDrawNothing() {
        EdgeShape s = new EdgeShape();
        s.set(0, 0, 30, 0, 0f, 20f);   // 30 apart, two radii need 40
        assertFalse(s.visible);
        assertEquals(Float.MAX_VALUE, s.distanceTo(15, 0), 0f);
    }

    @Test
    public void reciprocalEdgesBowToOppositeSides() {
        EdgeShape ab = new EdgeShape();
        EdgeShape ba = new EdgeShape();
        ab.set(0, 0, 200, 0, 18f, 20f);
        ba.set(200, 0, 0, 0, 18f, 20f);
        assertTrue(ab.curved);
        assertTrue(ab.midY * ba.midY < 0);                 // opposite sides of the straight line
        assertEquals(Math.abs(ab.midY), Math.abs(ba.midY), EPS);
        assertTrue(Math.abs(ab.midY) > 5f);                // really bowed
    }

    @Test
    public void arrowDirectionPointsIntoTheEndNode() {
        EdgeShape s = new EdgeShape();
        s.set(0, 0, 200, 100, 18f, 20f);
        float len = (float) Math.hypot(s.dirX, s.dirY);
        assertEquals(1f, len, EPS);                        // unit vector
        assertTrue(s.dirX > 0);                            // heading towards the node on the right
    }

    @Test
    public void distanceToAStraightEdge() {
        EdgeShape s = new EdgeShape();
        s.set(0, 0, 200, 0, 0f, 20f);
        assertEquals(0f, s.distanceTo(100, 0), EPS);
        assertEquals(15f, s.distanceTo(100, 15), EPS);
        assertEquals(10f, s.distanceTo(10, 0), EPS);       // 10 before the start at x = 20
    }

    @Test
    public void distanceToACurvedEdgeFollowsTheCurve() {
        EdgeShape s = new EdgeShape();
        s.set(0, 0, 200, 0, 18f, 20f);
        float onCurve = s.distanceTo(s.midX, s.midY);
        assertEquals(0f, onCurve, 0.5f);
        assertTrue(s.distanceTo(100, 0) > 5f);             // the straight line is no longer the edge
    }

    @Test
    public void segmentDistanceHandlesTheEnds() {
        assertEquals(5f, EdgeShape.segmentDistance(-3, 4, 0, 0, 10, 0), EPS);
        assertEquals(5f, EdgeShape.segmentDistance(13, 4, 0, 0, 10, 0), EPS);
        assertEquals(5f, EdgeShape.segmentDistance(3, 4, 7, 7, 7, 7), EPS);   // zero-length segment is a point
    }
}

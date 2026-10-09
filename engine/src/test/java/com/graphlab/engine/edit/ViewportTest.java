package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ViewportTest {
    private static Viewport sized(double density, int w, int h) {
        Viewport vp = new Viewport();
        vp.setDensity(density);
        vp.setSize(w, h);
        return vp;
    }

    @Test
    public void worldCentreIsTheMiddleOfTheView() {
        Viewport vp = sized(2.0, 400, 600);
        vp.set(1.0, 50, 70);
        assertEquals(200, vp.worldToScreenX(50), 1e-9);
        assertEquals(300, vp.worldToScreenY(70), 1e-9);
        assertEquals(2.0, vp.scale(), 1e-9);
    }

    @Test
    public void screenAndWorldRoundTrip() {
        Viewport vp = sized(2.625, 1080, 1800);
        vp.set(1.7, -33, 120);
        for (double x : new double[] {-500, 0, 12.5, 999}) {
            assertEquals(x, vp.screenToWorldX(vp.worldToScreenX(x)), 1e-9);
            assertEquals(x, vp.screenToWorldY(vp.worldToScreenY(x)), 1e-9);
        }
    }

    @Test
    public void panMovesThePictureWithTheFinger() {
        Viewport vp = sized(1.0, 400, 400);
        vp.set(1.0, 0, 0);
        double before = vp.worldToScreenX(10);
        vp.panByScreen(30, -20);
        assertEquals(before + 30, vp.worldToScreenX(10), 1e-9);
        assertEquals(200 - 20, vp.worldToScreenY(0), 1e-9);
    }

    @Test
    public void zoomKeepsTheWorldPointUnderTheFingers() {
        Viewport vp = sized(2.0, 800, 1200);
        vp.set(1.0, 10, 20);
        double wx = vp.screenToWorldX(150);
        double wy = vp.screenToWorldY(900);
        vp.zoomAt(1.8, 150, 900);
        assertEquals(150, vp.worldToScreenX(wx), 1e-6);
        assertEquals(900, vp.worldToScreenY(wy), 1e-6);
        assertEquals(1.8, vp.zoom(), 1e-9);
    }

    @Test
    public void zoomIsClamped() {
        Viewport vp = sized(1.0, 400, 400);
        vp.zoomAt(1000, 200, 200);
        assertEquals(Viewport.MAX_ZOOM, vp.zoom(), 1e-9);
        vp.zoomAt(1e-9, 200, 200);
        assertEquals(Viewport.MIN_ZOOM, vp.zoom(), 1e-9);
    }

    @Test
    public void rotationKeepsZoomAndTheCentre() {
        Viewport vp = sized(2.0, 1080, 1800);
        vp.set(2.2, 40, 60);
        vp.setSize(1800, 1080);   // phone turned sideways
        assertEquals(2.2, vp.zoom(), 1e-9);
        assertEquals(900, vp.worldToScreenX(40), 1e-9);   // still in the middle
        assertEquals(540, vp.worldToScreenY(60), 1e-9);
    }

    @Test
    public void fitShowsTheWholeGraphWithRoomForTheNodes() {
        Viewport vp = sized(2.0, 800, 1000);
        double[] bounds = {0, 0, 1000, 500};
        vp.fit(bounds, EditorMetrics.NODE_RADIUS, 80);
        // The left-most and right-most node edges must land inside the margins.
        assertTrue(vp.worldToScreenX(0 - EditorMetrics.NODE_RADIUS) >= 80 - 1e-6);
        assertTrue(vp.worldToScreenX(1000 + EditorMetrics.NODE_RADIUS) <= 800 - 80 + 1e-6);
        assertEquals(500, vp.centerX(), 1e-9);
        assertEquals(250, vp.centerY(), 1e-9);
    }

    @Test
    public void fitNeverZoomsInPastTheFitLimit() {
        Viewport vp = sized(1.0, 800, 800);
        vp.fit(new double[] {0, 0, 0, 0}, EditorMetrics.NODE_RADIUS, 40);   // one node
        assertEquals(Viewport.MAX_FIT_ZOOM, vp.zoom(), 1e-9);
    }

    @Test
    public void fitOnAnEmptyGraphResetsTheCamera() {
        Viewport vp = sized(1.0, 800, 800);
        vp.set(3, 500, 500);
        vp.fit(null, EditorMetrics.NODE_RADIUS, 40);
        assertEquals(1.0, vp.zoom(), 1e-9);
        assertEquals(0, vp.centerX(), 1e-9);
    }

    @Test
    public void fitBeforeTheViewHasASizeIsRememberedOnce() {
        Viewport vp = new Viewport();
        vp.consumeFitRequest();                       // clear the initial request
        vp.fit(new double[] {0, 0, 100, 100}, 22, 40);
        assertEquals(1.0, vp.zoom(), 1e-9);           // nothing happened yet
        assertTrue(vp.consumeFitRequest());
        assertFalse(vp.consumeFitRequest());          // consumed
    }
}

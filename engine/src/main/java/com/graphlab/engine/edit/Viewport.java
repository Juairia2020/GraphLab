package com.graphlab.engine.edit;

/**
 * The camera over the graph: which world point sits in the middle of the view, and how far
 * it is zoomed. It lives in the ViewModel, so zoom and position survive rotation; only the
 * pixel size of the view is replaced when the screen changes shape.
 *
 * <p>screen = (world - center) * scale + size / 2, where scale = zoom * density.
 */
public final class Viewport {
    public static final double MIN_ZOOM = 0.3;
    public static final double MAX_ZOOM = 4.0;
    /** Fitting a tiny graph never zooms in past this, so it does not fill the screen. */
    public static final double MAX_FIT_ZOOM = 1.5;

    private double density = 1.0;
    private double zoom = 1.0;
    private double centerX;
    private double centerY;
    private int width;
    private int height;
    private boolean fitRequested = true;

    /** Pixels per world unit at zoom 1 (the screen density, so one world unit is one dp). */
    public void setDensity(double density) {
        if (density > 0) {
            this.density = density;
        }
    }

    /** Keeps the centre and the zoom: after a rotation the same part of the graph stays in the middle. */
    public void setSize(int width, int height) {
        this.width = Math.max(width, 0);
        this.height = Math.max(height, 0);
    }

    public boolean hasSize() {
        return width > 0 && height > 0;
    }

    public double zoom() {
        return zoom;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    /** Pixels per world unit right now. */
    public double scale() {
        return zoom * density;
    }

    public void set(double zoom, double centerX, double centerY) {
        this.zoom = clampZoom(zoom);
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public double worldToScreenX(double wx) {
        return (wx - centerX) * scale() + width / 2.0;
    }

    public double worldToScreenY(double wy) {
        return (wy - centerY) * scale() + height / 2.0;
    }

    public double screenToWorldX(double sx) {
        return (sx - width / 2.0) / scale() + centerX;
    }

    public double screenToWorldY(double sy) {
        return (sy - height / 2.0) / scale() + centerY;
    }

    /** Slides the picture by (dx, dy) pixels, as if the finger dragged the paper. */
    public void panByScreen(double dx, double dy) {
        double s = scale();
        centerX -= dx / s;
        centerY -= dy / s;
    }

    /** Zooms by {@code factor} while the world point under (fx, fy) stays under the fingers. */
    public void zoomAt(double factor, double fx, double fy) {
        double wx = screenToWorldX(fx);
        double wy = screenToWorldY(fy);
        zoom = clampZoom(zoom * factor);
        double s = scale();
        centerX = wx - (fx - width / 2.0) / s;
        centerY = wy - (fy - height / 2.0) / s;
    }

    /**
     * Centres and zooms so everything inside {@code bounds} ({minX, minY, maxX, maxY}) is
     * visible, with {@code padWorld} extra world units around it (the node radius) and
     * {@code marginPx} of empty screen. {@code null} bounds (an empty graph) reset the camera.
     * Before the view has a size this only records the request; see {@link #consumeFitRequest()}.
     */
    public void fit(double[] bounds, double padWorld, double marginPx) {
        if (!hasSize()) {
            fitRequested = true;
            return;
        }
        fitRequested = false;
        if (bounds == null) {
            zoom = 1.0;
            centerX = 0;
            centerY = 0;
            return;
        }
        double w = bounds[2] - bounds[0] + 2 * padWorld;
        double h = bounds[3] - bounds[1] + 2 * padWorld;
        double availW = Math.max(1, width - 2 * marginPx);
        double availH = Math.max(1, height - 2 * marginPx);
        double zx = availW / (w * density);
        double zy = availH / (h * density);
        zoom = clampZoom(Math.min(Math.min(zx, zy), MAX_FIT_ZOOM));
        centerX = (bounds[0] + bounds[2]) / 2.0;
        centerY = (bounds[1] + bounds[3]) / 2.0;
    }

    public void requestFit() {
        fitRequested = true;
    }

    /** True once if a fit was asked for before the view had a size. Clears the request. */
    public boolean consumeFitRequest() {
        boolean was = fitRequested;
        fitRequested = false;
        return was;
    }

    private static double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }
}

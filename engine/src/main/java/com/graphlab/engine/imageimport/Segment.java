package com.graphlab.engine.imageimport;

/** A straight line found in the picture, from (x1, y1) to (x2, y2) in pixels, confidence 0 to 1. */
public record Segment(double x1, double y1, double x2, double y2, double confidence) {
    public double length() {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}

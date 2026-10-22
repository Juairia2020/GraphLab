package com.graphlab.engine.imageimport;

/** A node found in the picture: centre and radius in pixels, confidence 0 to 1. */
public record Circle(double x, double y, double r, double confidence) {
    public Circle {
        if (!(r > 0)) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}

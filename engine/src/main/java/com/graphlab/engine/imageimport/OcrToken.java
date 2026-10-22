package com.graphlab.engine.imageimport;

import java.util.Objects;

/**
 * One piece of recognised text with its box in image pixels ({@code x}, {@code y} is the top-left corner) and the
 * recogniser's confidence from 0 to 1. The detector in the app turns ML Kit's elements into these; tests make them by hand.
 */
public record OcrToken(String text, double x, double y, double width, double height, double confidence) {
    public OcrToken {
        Objects.requireNonNull(text, "text");
        if (confidence < 0 || confidence > 1 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public double cx() {
        return x + width / 2;
    }

    public double cy() {
        return y + height / 2;
    }

    public static OcrToken at(String text, double cx, double cy, double confidence) {
        return new OcrToken(text, cx - 8, cy - 6, 16, 12, confidence);
    }
}

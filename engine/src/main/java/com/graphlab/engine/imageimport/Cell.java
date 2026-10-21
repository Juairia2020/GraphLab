package com.graphlab.engine.imageimport;

/** One word or number read from a photo of a matrix, with its recogniser confidence. */
record Cell(String text, double confidence) {
    boolean strictNumber() {
        return text.trim().matches("[-−–]?\\d+([.,]\\d+)?");
    }

    boolean none() {
        return ReadNumber.isNone(text);
    }
}

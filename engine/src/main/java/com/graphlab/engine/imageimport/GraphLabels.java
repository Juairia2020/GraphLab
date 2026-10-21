package com.graphlab.engine.imageimport;

/** Names for nodes the picture did not name: A, B, ... Z, then A1, B1, ... */
final class GraphLabels {
    private GraphLabels() {
    }

    static String of(int i) {
        char c = (char) ('A' + i % 26);
        return i < 26 ? String.valueOf(c) : c + String.valueOf(i / 26);
    }
}

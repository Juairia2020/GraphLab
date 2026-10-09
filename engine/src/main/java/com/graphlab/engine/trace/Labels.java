package com.graphlab.engine.trace;

import java.util.Locale;

/** Formats numbers for the small labels shown above nodes. */
public final class Labels {
    /** The infinity sign, shown for "no distance yet". */
    public static final String INFINITY = "\u221e";

    private Labels() {
    }

    /** 7 gives "7", 2.5 gives "2.5", 3.14159 gives "3.14", infinity gives the infinity sign. */
    public static String number(double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return INFINITY;
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-" + INFINITY;
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text.equals("-0") ? "0" : text;
    }

    /** The reverse of {@link #number(double)}, used by tests that replay labels back into numbers. */
    public static double parse(String label) {
        if (label.equals(INFINITY)) {
            return Double.POSITIVE_INFINITY;
        }
        if (label.equals("-" + INFINITY)) {
            return Double.NEGATIVE_INFINITY;
        }
        return Double.parseDouble(label);
    }
}

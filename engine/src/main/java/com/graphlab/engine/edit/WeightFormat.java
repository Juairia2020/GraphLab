package com.graphlab.engine.edit;

import java.util.Locale;

/** Turns edge weights into short text and back. Pure Java so it can be unit tested. */
public final class WeightFormat {
    /** Weights beyond this are rejected: they would make pills unreadable. */
    public static final double MAX_ABS = 1_000_000;

    public enum Error { NONE, EMPTY, NOT_A_NUMBER, TOO_LARGE }

    /** The outcome of {@link #parse}. */
    public static final class Parsed {
        private final double value;
        private final Error error;

        private Parsed(double value, Error error) {
            this.value = value;
            this.error = error;
        }

        public boolean isValid() {
            return error == Error.NONE;
        }

        /** The number. Only meaningful when {@link #isValid()}. */
        public double value() {
            return value;
        }

        public Error error() {
            return error;
        }
    }

    private WeightFormat() {
    }

    /** 7 for 7.0, 2.5 for 2.5, 0.33 for a third. At most two decimals. */
    public static String format(double weight) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return "?";
        }
        if (weight == Math.rint(weight) && Math.abs(weight) < 1e15) {
            return Long.toString((long) weight);
        }
        String text = String.format(Locale.ROOT, "%.2f", weight);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        String trimmed = text.substring(0, end);
        return trimmed.equals("-0") || trimmed.isEmpty() ? "0" : trimmed;
    }

    /**
     * Reads what a person typed. Accepts a leading minus (also the typographic one), a plain
     * decimal point or a decimal comma. Rejects empty text, anything that is not a plain number
     * (no exponents, no spaces inside) and values beyond {@link #MAX_ABS}.
     */
    public static Parsed parse(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return new Parsed(0, Error.EMPTY);
        }
        s = s.replace('\u2212', '-').replace(',', '.');
        if (!s.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")) {
            return new Parsed(0, Error.NOT_A_NUMBER);
        }
        double value;
        try {
            value = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return new Parsed(0, Error.NOT_A_NUMBER);
        }
        if (Math.abs(value) > MAX_ABS) {
            return new Parsed(0, Error.TOO_LARGE);
        }
        return new Parsed(value == 0 ? 0 : value, Error.NONE);
    }
}

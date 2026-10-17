package com.graphlab.engine.experiment;

/** Checks the number of trials typed on the Experiment screen. */
public final class TrialCount {
    public static final int MIN = 1;
    public static final int MAX = 20_000;
    public static final int DEFAULT = 500;
    /** The service updates its notification this often. */
    public static final int REPORT_EVERY = 20;

    private TrialCount() {
    }

    /**
     * @return the number
     * @throws IllegalArgumentException with a message fit to show, for empty text, non-numbers and numbers out of range
     */
    public static int parse(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Enter how many trials to run.");
        }
        long value;
        try {
            value = Long.parseLong(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The number of trials must be a whole number.");
        }
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("Choose between " + MIN + " and " + MAX + " trials.");
        }
        return (int) value;
    }
}

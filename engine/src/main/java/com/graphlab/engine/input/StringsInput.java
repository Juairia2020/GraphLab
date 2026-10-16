package com.graphlab.engine.input;

/**
 * Two short pieces of text for the string algorithms (MS12). Each has 1 to {@value #MAX_LENGTH} letters or digits, so
 * the table (length + 1 rows and columns) stays readable on a phone (the grid scrolls). Upper and lower case are different characters.
 */
public record StringsInput(String first, String second) implements Input {
    public static final int MAX_LENGTH = 32;

    public StringsInput {
        first = check(first, "first");
        second = check(second, "second");
    }

    private static String check(String text, String which) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Type the " + which + " text.");
        }
        if (t.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The " + which + " text has " + t.length() + " characters; at most " + MAX_LENGTH + " are allowed.");
        }
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isLetterOrDigit(t.charAt(i))) {
                throw new IllegalArgumentException("Use only letters and digits in the " + which + " text, not '" + t.charAt(i) + "'.");
            }
        }
        return t;
    }

    @Override
    public InputKind kind() {
        return InputKind.STRINGS;
    }
}

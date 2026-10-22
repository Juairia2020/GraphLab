package com.graphlab.engine.imageimport;

/**
 * Reads a number from recognised text, forgiving the usual mix-ups of handwriting (O for 0, l or I for 1, S for 5 ...).
 * A repaired reading is returned with a lower confidence, so the review screen flags it.
 */
final class ReadNumber {
    /** How much a repaired reading is trusted compared with a clean one. */
    static final double REPAIR_FACTOR = 0.6;

    record Value(double number, double confidence, boolean repaired) {
    }

    private ReadNumber() {
    }

    /** A number, or null when the text is not one. */
    static Value of(String raw, double tokenConfidence) {
        String text = raw.trim().replace('−', '-').replace('–', '-').replace('—', '-');
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("-?\\d+([.,]\\d+)?")) {
            return new Value(Double.parseDouble(text.replace(',', '.')), tokenConfidence, false);
        }
        StringBuilder fixed = new StringBuilder();
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
                fixed.append(c);
            } else if (c == '-' && i == 0) {
                fixed.append(c);
            } else if (c == '.' || c == ',') {
                fixed.append('.');
            } else {
                char m = lookAlike(c);
                if (m == 0) {
                    return null;
                }
                fixed.append(m);
            }
        }
        // a lone letter is a name, not a number: only repair when the text is short or has real digits in it
        if (digits == 0 && text.length() > 2) {
            return null;
        }
        if (digits == 0 && !isDigitLike(text)) {
            return null;
        }
        String s = fixed.toString();
        if (!s.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return new Value(Double.parseDouble(s), tokenConfidence * REPAIR_FACTOR, true);
    }

    private static boolean isDigitLike(String text) {
        for (char c : text.toCharArray()) {
            if (lookAlike(c) == 0) {
                return false;
            }
        }
        return true;
    }

    private static char lookAlike(char c) {
        switch (c) {
            case 'O':
            case 'o':
            case 'Q':
            case 'D':
                return '0';
            case 'l':
            case 'I':
            case '|':
            case '!':
            case 'i':
                return '1';
            case 'Z':
            case 'z':
                return '2';
            case 'S':
            case 's':
                return '5';
            case 'B':
                return '8';
            case 'g':
            case 'q':
                return '9';
            default:
                return 0;
        }
    }

    /** True for the marks that mean "no edge here" in a matrix. */
    static boolean isNone(String raw) {
        String t = raw.trim();
        return t.equals("∞") || t.equalsIgnoreCase("inf") || t.equalsIgnoreCase("oo") || t.equals("-") || t.equals("–")
                || t.equals("—") || t.equals(".") || t.equals("INF");
    }
}

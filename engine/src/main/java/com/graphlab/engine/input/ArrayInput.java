package com.graphlab.engine.input;

import java.util.List;

/**
 * A list of whole numbers to sort. At most {@value #MAX_SIZE} of them, each from 0 to
 * {@value #MAX_VALUE}, so the bars fit on a phone and a trace stays short.
 */
public record ArrayInput(List<Integer> values) implements Input {
    public static final int MAX_SIZE = 16;
    public static final int MAX_VALUE = 99;

    public ArrayInput {
        values = List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Give at least one number to sort.");
        }
        if (values.size() > MAX_SIZE) {
            throw new IllegalArgumentException("At most " + MAX_SIZE + " numbers fit on the screen, got "
                    + values.size() + ".");
        }
        for (int v : values) {
            if (v < 0 || v > MAX_VALUE) {
                throw new IllegalArgumentException("Numbers must be between 0 and " + MAX_VALUE + ", got " + v + ".");
            }
        }
    }

    @Override
    public InputKind kind() {
        return InputKind.ARRAY;
    }
}

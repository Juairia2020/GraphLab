package com.graphlab.engine.trace;

import java.util.List;

/**
 * An array of values with one {@link State} per cell. Sorting algorithms replay through this.
 *
 * <p>MS1 adds it only as the result of the "merge sort spike" (step 5 of the plan), to prove
 * the step model fits a non-graph algorithm. MS5 builds the real sorting algorithms on it.
 */
public record ArrayFrame(List<Integer> values, List<State> states) implements Frame {

    public ArrayFrame {
        values = List.copyOf(values);
        states = List.copyOf(states);
        if (values.size() != states.size()) {
            throw new IllegalArgumentException(
                    "values (" + values.size() + ") and states (" + states.size()
                            + ") must be the same length");
        }
    }
}

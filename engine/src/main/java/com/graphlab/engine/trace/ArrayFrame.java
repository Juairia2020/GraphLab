package com.graphlab.engine.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A row of bars: a whole number and a {@link State} per cell. Sorting and the greedy knapsack
 * replay through this. Three optional extras help the screen explain a step:
 *
 * <ul>
 *   <li>{@code cellText}: a short line under a cell (for the knapsack, "w10" for weight 10); empty
 *       when no cell has any;</li>
 *   <li>{@code markers}: named pointers such as "lo", "hi", "i", "j", each at one cell index.</li>
 * </ul>
 *
 * <p>Colours mean the same in every sorting trace: IDLE is outside the part being worked on,
 * FRONTIER is inside it, CHOSEN is being compared or moved right now, PATH is a pivot, VISITED is
 * sorted. Everything is an unmodifiable copy.
 */
public record ArrayFrame(
        List<Integer> values,
        List<State> states,
        List<String> cellText,
        Map<String, Integer> markers) implements Frame {

    /** Bars and states only, as the MS1 spike built them. */
    public ArrayFrame(List<Integer> values, List<State> states) {
        this(values, states, List.of(), Map.of());
    }

    public ArrayFrame {
        values = List.copyOf(values);
        states = List.copyOf(states);
        cellText = List.copyOf(cellText);
        if (values.size() != states.size()) {
            throw new IllegalArgumentException(
                    "values (" + values.size() + ") and states (" + states.size()
                            + ") must be the same length");
        }
        if (!cellText.isEmpty() && cellText.size() != values.size()) {
            throw new IllegalArgumentException(
                    "cellText (" + cellText.size() + ") must be empty or as long as values (" + values.size() + ")");
        }
        Map<String, Integer> copy = new LinkedHashMap<>(markers);
        for (Map.Entry<String, Integer> e : copy.entrySet()) {
            if (e.getValue() < 0 || e.getValue() >= values.size()) {
                throw new IllegalArgumentException("Marker " + e.getKey() + " points at " + e.getValue()
                        + ", outside 0.." + (values.size() - 1));
            }
        }
        markers = Collections.unmodifiableMap(copy);
    }

    /** The text under cell {@code index}, or an empty string. */
    public String text(int index) {
        return cellText.isEmpty() ? "" : cellText.get(index);
    }

    public static Builder builder(List<Integer> values) {
        return new Builder(values);
    }

    /** Mutable scratch space an algorithm paints on and snapshots with {@link #build()} after each step. */
    public static final class Builder {
        private final List<Integer> values;
        private final List<State> states = new ArrayList<>();
        private final List<String> text = new ArrayList<>();
        private final Map<String, Integer> markers = new LinkedHashMap<>();
        private boolean anyText;

        private Builder(List<Integer> initial) {
            this.values = new ArrayList<>(initial);
            for (int i = 0; i < values.size(); i++) {
                states.add(State.IDLE);
                text.add("");
            }
        }

        public int size() {
            return values.size();
        }

        public int value(int index) {
            return values.get(index);
        }

        public State state(int index) {
            return states.get(index);
        }

        public Builder set(int index, int value) {
            values.set(index, value);
            return this;
        }

        public Builder swap(int i, int j) {
            Collections.swap(values, i, j);
            Collections.swap(text, i, j);
            return this;
        }

        public Builder state(int index, State state) {
            states.set(index, state);
            return this;
        }

        /** Sets cells {@code lo..hi} (inclusive) to {@code state}. An empty range (lo above hi) does nothing. */
        public Builder states(int lo, int hi, State state) {
            for (int i = lo; i <= hi; i++) {
                states.set(i, state);
            }
            return this;
        }

        public Builder allStates(State state) {
            return states(0, values.size() - 1, state);
        }

        public Builder recolour(State from, State to) {
            states.replaceAll(s -> s == from ? to : s);
            return this;
        }

        public Builder mark(String name, int index) {
            markers.put(name, index);
            return this;
        }

        public Builder unmark(String name) {
            markers.remove(name);
            return this;
        }

        public Builder clearMarks() {
            markers.clear();
            return this;
        }

        public Builder text(int index, String line) {
            text.set(index, line);
            anyText |= !line.isEmpty();
            return this;
        }

        /** Replaces every value (and its text) in one go, for a step that reorders the whole list. */
        public Builder replaceAll(List<Integer> newValues, List<String> newText) {
            if (newValues.size() != values.size()) {
                throw new IllegalArgumentException("A list cannot change length");
            }
            for (int i = 0; i < newValues.size(); i++) {
                values.set(i, newValues.get(i));
                text.set(i, newText.isEmpty() ? "" : newText.get(i));
            }
            anyText = newText.stream().anyMatch(t -> !t.isEmpty());
            return this;
        }

        public ArrayFrame build() {
            return new ArrayFrame(values, states, anyText || text.stream().anyMatch(t -> !t.isEmpty()) ? text : List.of(), markers);
        }
    }
}

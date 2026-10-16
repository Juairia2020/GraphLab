package com.graphlab.engine.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A table: row and column headers, a text in every cell and a {@link State} per cell. The
 * knapsack's dynamic-programming table and Floyd-Warshall's distance matrix replay through this.
 * Empty text means "not computed yet".
 *
 * <p>Everything is an unmodifiable copy, so each step can keep its own frame.
 */
public record GridFrame(
        String corner,
        List<String> rowHeaders,
        List<String> colHeaders,
        List<List<String>> cells,
        List<List<State>> states) implements Frame {

    public GridFrame {
        rowHeaders = List.copyOf(rowHeaders);
        colHeaders = List.copyOf(colHeaders);
        cells = copyRows(cells);
        states = copyRows(states);
        if (cells.size() != rowHeaders.size() || states.size() != rowHeaders.size()) {
            throw new IllegalArgumentException("A grid needs one row of cells and states per row header");
        }
        for (int r = 0; r < rowHeaders.size(); r++) {
            if (cells.get(r).size() != colHeaders.size() || states.get(r).size() != colHeaders.size()) {
                throw new IllegalArgumentException("Row " + r + " needs one cell and one state per column header");
            }
        }
    }

    private static <T> List<List<T>> copyRows(List<List<T>> rows) {
        List<List<T>> out = new ArrayList<>(rows.size());
        for (List<T> row : rows) {
            out.add(List.copyOf(row));
        }
        return Collections.unmodifiableList(out);
    }

    public int rows() {
        return rowHeaders.size();
    }

    public int cols() {
        return colHeaders.size();
    }

    public String cell(int row, int col) {
        return cells.get(row).get(col);
    }

    public State state(int row, int col) {
        return states.get(row).get(col);
    }

    public static Builder builder(String corner, List<String> rowHeaders, List<String> colHeaders) {
        return new Builder(corner, rowHeaders, colHeaders);
    }

    /** Mutable scratch space an algorithm paints on and snapshots with {@link #build()} after each step. */
    public static final class Builder {
        private final String corner;
        private final List<String> rowHeaders;
        private final List<String> colHeaders;
        private final String[][] cells;
        private final State[][] states;

        private Builder(String corner, List<String> rowHeaders, List<String> colHeaders) {
            this.corner = corner;
            this.rowHeaders = List.copyOf(rowHeaders);
            this.colHeaders = List.copyOf(colHeaders);
            this.cells = new String[rowHeaders.size()][colHeaders.size()];
            this.states = new State[rowHeaders.size()][colHeaders.size()];
            for (int r = 0; r < cells.length; r++) {
                java.util.Arrays.fill(cells[r], "");
                java.util.Arrays.fill(states[r], State.IDLE);
            }
        }

        public int rows() {
            return cells.length;
        }

        public int cols() {
            return colHeaders.size();
        }

        public Builder set(int row, int col, String text) {
            cells[row][col] = text;
            return this;
        }

        public String cell(int row, int col) {
            return cells[row][col];
        }

        public Builder state(int row, int col, State state) {
            states[row][col] = state;
            return this;
        }

        public State state(int row, int col) {
            return states[row][col];
        }

        public Builder stateRow(int row, State state) {
            java.util.Arrays.fill(states[row], state);
            return this;
        }

        public Builder stateCol(int col, State state) {
            for (State[] row : states) {
                row[col] = state;
            }
            return this;
        }

        public Builder allStates(State state) {
            for (State[] row : states) {
                java.util.Arrays.fill(row, state);
            }
            return this;
        }

        public Builder recolour(State from, State to) {
            for (State[] row : states) {
                for (int c = 0; c < row.length; c++) {
                    if (row[c] == from) {
                        row[c] = to;
                    }
                }
            }
            return this;
        }

        public GridFrame build() {
            List<List<String>> cellRows = new ArrayList<>(cells.length);
            List<List<State>> stateRows = new ArrayList<>(cells.length);
            for (int r = 0; r < cells.length; r++) {
                cellRows.add(java.util.Arrays.asList(cells[r].clone()));
                stateRows.add(java.util.Arrays.asList(states[r].clone()));
            }
            return new GridFrame(corner, rowHeaders, colHeaders, cellRows, stateRows);
        }
    }
}

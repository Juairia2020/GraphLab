package com.graphlab.engine.arena;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.input.CoinInput;
import com.graphlab.engine.input.StringsInput;
import com.graphlab.engine.input.TextInput;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * "Fill the table" for the MS12 dynamic-programming algorithms. The table is the engine's own
 * ({@link Result#matrix()}), so the blanks always hold what the algorithm computed. A blank is only allowed where every cell
 * it is computed from is shown, and where no shown cell is computed from it, so each blank can be worked out from the cells
 * around it.
 */
final class DpTables {
    private static final String[] IDS = {"coin-change", "lcs", "edit-distance"};

    private DpTables() {
    }

    /** @param which 0 coin change, 1 longest common subsequence, 2 edit distance */
    static Question create(int which, Level level, Random r) {
        String id = IDS[which];
        com.graphlab.engine.input.Input input;
        String intro;
        String rule;
        List<String> rowHeaders = new ArrayList<>();
        List<String> colHeaders = new ArrayList<>();
        if (which == 0) {
            int n = 2 + level.ordinal();
            List<Integer> pool = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));
            Collections.shuffle(pool, r);
            List<Integer> coins = new ArrayList<>(pool.subList(0, n));
            if (!coins.contains(1) && r.nextBoolean()) {
                coins.set(0, 1);
            }
            Collections.sort(coins);
            CoinInput coinInput = new CoinInput(coins, 6 + 2 * level.ordinal() + r.nextInt(3));
            input = coinInput;
            intro = "Coin change: the fewest coins for each amount. Coins " + coins + ", amount up to " + coinInput.amount() + ".";
            rule = "A cell is the smaller of two numbers: the cell above (skip the coin), or one plus the cell to the left by the "
                    + "coin's value in the same row (use the coin again). A cell with no way to make the amount is an infinity sign.";
        } else {
            int length = 4 + level.ordinal();
            String a = word(r, length);
            String b = word(r, length - 1 + r.nextInt(2));
            input = new StringsInput(a, b);
            intro = (which == 1 ? "Longest common subsequence of " : "Edit distance from ") + a + " to " + b + ".";
            rule = which == 1
                    ? "If the two letters match, the cell is one more than the cell up and to the left. Otherwise it is the larger of the cell above and the cell on the left."
                    : "If the two letters match, copy the cell up and to the left. Otherwise it is one plus the smallest of the cell up and to the left (replace), above (delete) and on the left (insert).";
        }
        Result result = Algorithms.byId(id).run(input, Params.none()).result();
        List<List<Double>> matrix = result.matrix();
        int rows = matrix.size();
        int cols = matrix.get(0).size();
        if (which == 0) {
            CoinInput c = (CoinInput) input;
            rowHeaders.add("none");
            for (int coin : c.coins()) {
                rowHeaders.add("coin " + coin);
            }
            for (int a = 0; a < cols; a++) {
                colHeaders.add(Integer.toString(a));
            }
        } else {
            StringsInput s = (StringsInput) input;
            rowHeaders.add("-");
            for (char ch : s.first().toCharArray()) {
                rowHeaders.add(String.valueOf(ch));
            }
            colHeaders.add("-");
            for (char ch : s.second().toCharArray()) {
                colHeaders.add(String.valueOf(ch));
            }
        }

        int wanted = 2 + level.ordinal();
        boolean[][] blank = new boolean[rows][cols];
        List<int[]> blanks = new ArrayList<>();
        List<int[]> cells = new ArrayList<>();
        for (int i = 1; i < rows; i++) {
            for (int j = 1; j < cols; j++) {
                if (!Double.isInfinite(matrix.get(i).get(j))) {
                    cells.add(new int[] {i, j});
                }
            }
        }
        Collections.shuffle(cells, r);
        for (int[] cell : cells) {
            if (blanks.size() == wanted) {
                break;
            }
            if (allowed(which, input, blank, cell[0], cell[1], rows, cols)) {
                blank[cell[0]][cell[1]] = true;
                blanks.add(cell);
            }
        }
        if (blanks.size() < wanted) {
            return null;
        }
        List<String> shown = new ArrayList<>();
        List<Integer> solution = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double v = matrix.get(i).get(j);
                if (blank[i][j]) {
                    shown.add("");
                    solution.add((int) Math.rint(v));
                } else {
                    shown.add(Double.isInfinite(v) ? "∞" : Integer.toString((int) Math.rint(v)));
                }
            }
        }
        String values = solution.toString().replace("[", "").replace("]", "");
        String answer = values + " (in the order of the blank cells, row by row)";
        String prompt = intro + " " + rule + " Type the " + wanted + " blank cells.";
        String why = "Every blank follows the same rule as the rest of the table. The right values are " + answer
                + ". The engine's own run of " + Algorithms.byId(id).name() + " gives the same table.";
        Task task = new Task(Interaction.TABLE, shown, solution, 0, cols, answer, rowHeaders, colHeaders);
        // the problem is stated in the prompt; the question shows it as text
        return new Question(QuestionKind.FILL_TABLE, level, prompt, new TextInput(intro), -1, -1, List.of(), 0, why, null, id,
                null, task);
    }

    /** Cells a cell is computed from. */
    private static List<int[]> depends(int which, com.graphlab.engine.input.Input input, int i, int j) {
        List<int[]> out = new ArrayList<>();
        if (which == 0) {
            out.add(new int[] {i - 1, j});
            int coin = ((CoinInput) input).coins().get(i - 1);
            if (j - coin >= 0) {
                out.add(new int[] {i, j - coin});
            }
        } else {
            out.add(new int[] {i - 1, j - 1});
            out.add(new int[] {i - 1, j});
            out.add(new int[] {i, j - 1});
        }
        return out;
    }

    private static boolean allowed(int which, com.graphlab.engine.input.Input input, boolean[][] blank, int i, int j, int rows, int cols) {
        for (int[] d : depends(which, input, i, j)) {
            if (blank[d[0]][d[1]]) {
                return false;
            }
        }
        // no blank may be computed from this cell
        for (int a = 1; a < rows; a++) {
            for (int b = 1; b < cols; b++) {
                if (blank[a][b]) {
                    for (int[] d : depends(which, input, a, b)) {
                        if (d[0] == i && d[1] == j) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static String word(Random r, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append((char) ('A' + r.nextInt(3)));
        }
        return out.toString();
    }
}

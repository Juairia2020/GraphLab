package com.graphlab.engine.algo.dp;

import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.input.StringsInput;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Longest common subsequence (MS12). {@code L[i][j]} is the length of the longest common subsequence of the first
 * {@code i} letters of one text and the first {@code j} letters of the other. The table is walked back from the bottom
 * right corner to read the subsequence itself.
 *
 * <p>Result: {@link Result#value()} is the length and {@link Result#text()} the subsequence (empty when there is none).
 * When several subsequences are equally long the walk prefers the cell above, so the answer is always the same.
 */
public final class Lcs extends DpAlgorithm<StringsInput> {
    public static final String ID = "lcs";

    private static final List<String> PSEUDOCODE = List.of(
            "L[0][j] = 0 and L[i][0] = 0  (an empty text has nothing in common)",
            "for each i = 1..n and each j = 1..m",
            "    if a[i] equals b[j]: L[i][j] = L[i-1][j-1] + 1",
            "    otherwise: L[i][j] = the larger of L[i-1][j] and L[i][j-1]",
            "start at L[n][m] and walk back while i > 0 and j > 0",
            "    if a[i] equals b[j]: this letter is in the answer; go up and left",
            "    otherwise go to the larger of the cell above and the cell on the left (above if equal)",
            "the answer is the letters collected, read from the start; its length is L[n][m]");

    public Lcs() {
        super(StringsInput.class, InputKind.STRINGS, "two pieces of text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Longest common subsequence";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(StringsInput input, Params params) {
        String a = input.first();
        String b = input.second();
        int n = a.length();
        int m = b.length();

        List<String> rowHeaders = new ArrayList<>();
        rowHeaders.add("-");
        for (int i = 0; i < n; i++) {
            rowHeaders.add((i + 1) + ": " + a.charAt(i));
        }
        List<String> colHeaders = new ArrayList<>();
        colHeaders.add("-");
        for (int j = 0; j < m; j++) {
            colHeaders.add((j + 1) + ": " + b.charAt(j));
        }

        int[][] l = new int[n + 1][m + 1];
        GridFrame.Builder paint = GridFrame.builder("first \\ second", rowHeaders, colHeaders);
        TraceRecorder rec = new TraceRecorder().counter("cells filled", 0).counter("length", 0);

        for (int i = 0; i <= n; i++) {
            paint.set(i, 0, "0").state(i, 0, State.VISITED);
        }
        for (int j = 0; j <= m; j++) {
            paint.set(0, j, "0").state(0, j, State.VISITED);
        }
        long filled = n + m + 1;
        rec.set("cells filled", filled);
        rec.step(paint.build(), 1, "The first row and column are 0: an empty text has nothing in common with anything");

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                boolean same = a.charAt(i - 1) == b.charAt(j - 1);
                l[i][j] = same ? l[i - 1][j - 1] + 1 : Math.max(l[i - 1][j], l[i][j - 1]);
                filled++;
                rec.set("cells filled", filled);
                settle(paint);
                paint.set(i, j, Integer.toString(l[i][j]));
                if (same) {
                    paint.state(i - 1, j - 1, State.FRONTIER);
                } else {
                    paint.state(i - 1, j, State.FRONTIER).state(i, j - 1, State.FRONTIER);
                }
                paint.state(i, j, State.CHOSEN);
                String message = same
                        ? "'" + a.charAt(i - 1) + "' = '" + b.charAt(j - 1) + "': one more than the cell up and left = " + l[i][j]
                        : "'" + a.charAt(i - 1) + "' differs from '" + b.charAt(j - 1) + "': the larger of above (" + l[i - 1][j]
                                + ") and left (" + l[i][j - 1] + ") = " + l[i][j];
                rec.step(paint.build(), same ? 3 : 4, message);
            }
        }
        settle(paint);
        rec.set("length", l[n][m]);
        rec.step(paint.build(), 4, "The table is full. The longest common subsequence has " + l[n][m]
                + (l[n][m] == 1 ? " letter" : " letters"));

        StringBuilder reversed = new StringBuilder();
        int i = n;
        int j = m;
        paint.state(i, j, State.PATH);
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                reversed.append(a.charAt(i - 1));
                i--;
                j--;
                paint.state(i, j, State.PATH);
                rec.step(paint.build(), 6, "'" + a.charAt(i) + "' matches: it is in the answer. Go up and left");
            } else if (l[i - 1][j] >= l[i][j - 1]) {
                i--;
                paint.state(i, j, State.PATH);
                rec.step(paint.build(), 7, "No match: the cell above is at least as large, so go up");
            } else {
                j--;
                paint.state(i, j, State.PATH);
                rec.step(paint.build(), 7, "No match: the cell on the left is larger, so go left");
            }
        }
        String answer = reversed.reverse().toString();
        rec.step(paint.build(), 8, answer.isEmpty()
                ? "Done: the texts have no letter in common"
                : "Done: the longest common subsequence is " + answer + " (" + answer.length() + " letters)");

        List<List<Double>> matrix = new ArrayList<>();
        for (int[] row : l) {
            List<Double> r = new ArrayList<>();
            for (int v : row) {
                r.add((double) v);
            }
            matrix.add(r);
        }
        Result result = Result.builder().value(l[n][m]).text(answer).matrix(matrix).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

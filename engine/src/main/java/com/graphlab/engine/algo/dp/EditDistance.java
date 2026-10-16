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
import java.util.Collections;
import java.util.List;

/**
 * Edit distance (Levenshtein, MS12): the fewest single-letter edits (replace, delete, insert) that turn the first text into
 * the second. {@code D[i][j]} is the distance between the first {@code i} letters of one text and the first {@code j} of the
 * other. The walk back reads the edits themselves.
 *
 * <p>Result: {@link Result#value()} is the distance; {@link Result#text()} is the edit script from the start of the first
 * text to the end, as tokens separated by spaces: {@code keep:K}, {@code replace:S>M} (S becomes M), {@code delete:A},
 * {@code insert:B}. Replaying the script on the first text gives the second text, and the tokens that are not
 * {@code keep} number exactly the distance. When several scripts are equally short, the walk prefers keep, then replace,
 * then delete, then insert.
 */
public final class EditDistance extends DpAlgorithm<StringsInput> {
    public static final String ID = "edit-distance";

    private static final List<String> PSEUDOCODE = List.of(
            "D[i][0] = i and D[0][j] = j  (turning a text into nothing, or nothing into a text, takes one edit per letter)",
            "for each i = 1..n and each j = 1..m",
            "    if a[i] equals b[j]: D[i][j] = D[i-1][j-1]",
            "    otherwise: D[i][j] = 1 + the smallest of replace D[i-1][j-1], delete D[i-1][j], insert D[i][j-1]",
            "start at D[n][m] and walk back until i = 0 and j = 0",
            "    equal letters or a replace: go up and left; a delete: go up; an insert: go left",
            "the edits, read from the start, turn the first text into the second",
            "the answer is D[n][m]");

    public EditDistance() {
        super(StringsInput.class, InputKind.STRINGS, "two pieces of text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Edit distance";
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

        int[][] d = new int[n + 1][m + 1];
        GridFrame.Builder paint = GridFrame.builder("from \\ to", rowHeaders, colHeaders);
        TraceRecorder rec = new TraceRecorder().counter("cells filled", 0).counter("distance", 0);

        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
            paint.set(i, 0, Integer.toString(i)).state(i, 0, State.VISITED);
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
            paint.set(0, j, Integer.toString(j)).state(0, j, State.VISITED);
        }
        long filled = n + m + 1;
        rec.set("cells filled", filled);
        rec.step(paint.build(), 1, "The first column counts deletions and the first row counts insertions: one edit per letter");

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                boolean same = a.charAt(i - 1) == b.charAt(j - 1);
                if (same) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    d[i][j] = 1 + Math.min(d[i - 1][j - 1], Math.min(d[i - 1][j], d[i][j - 1]));
                }
                filled++;
                rec.set("cells filled", filled);
                settle(paint);
                paint.set(i, j, Integer.toString(d[i][j]));
                paint.state(i - 1, j - 1, State.FRONTIER);
                if (!same) {
                    paint.state(i - 1, j, State.FRONTIER).state(i, j - 1, State.FRONTIER);
                }
                paint.state(i, j, State.CHOSEN);
                String message = same
                        ? "'" + a.charAt(i - 1) + "' = '" + b.charAt(j - 1) + "': no edit needed, copy the cell up and left = " + d[i][j]
                        : "'" + a.charAt(i - 1) + "' differs from '" + b.charAt(j - 1) + "': 1 + the smallest of replace ("
                                + d[i - 1][j - 1] + "), delete (" + d[i - 1][j] + "), insert (" + d[i][j - 1] + ") = " + d[i][j];
                rec.step(paint.build(), same ? 3 : 4, message);
            }
        }
        settle(paint);
        rec.set("distance", d[n][m]);
        rec.step(paint.build(), 4, "The table is full. The edit distance is " + d[n][m]);

        List<String> tokens = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        int i = n;
        int j = m;
        paint.state(i, j, State.PATH);
        while (i > 0 || j > 0) {
            String token;
            String sentence;
            if (i > 0 && j > 0 && a.charAt(i - 1) == b.charAt(j - 1) && d[i][j] == d[i - 1][j - 1]) {
                token = "keep:" + a.charAt(i - 1);
                sentence = "'" + a.charAt(i - 1) + "' matches: keep it";
                i--;
                j--;
            } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
                token = "replace:" + a.charAt(i - 1) + ">" + b.charAt(j - 1);
                sentence = "Replace '" + a.charAt(i - 1) + "' with '" + b.charAt(j - 1) + "'";
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                token = "delete:" + a.charAt(i - 1);
                sentence = "Delete '" + a.charAt(i - 1) + "'";
                i--;
            } else {
                token = "insert:" + b.charAt(j - 1);
                sentence = "Insert '" + b.charAt(j - 1) + "'";
                j--;
            }
            tokens.add(token);
            sentences.add(sentence);
            paint.state(i, j, State.PATH);
            rec.step(paint.build(), 6, sentence);
        }
        Collections.reverse(tokens);
        String script = String.join(" ", tokens);
        rec.step(paint.build(), 8, d[n][m] == 0
                ? "Done: the texts are equal, no edit is needed"
                : "Done: " + d[n][m] + (d[n][m] == 1 ? " edit turns " : " edits turn ") + a + " into " + b);

        List<List<Double>> matrix = new ArrayList<>();
        for (int[] row : d) {
            List<Double> r = new ArrayList<>();
            for (int v : row) {
                r.add((double) v);
            }
            matrix.add(r);
        }
        Result result = Result.builder().value(d[n][m]).text(script).matrix(matrix).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

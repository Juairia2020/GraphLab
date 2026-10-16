package com.graphlab.engine.algo.dp;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Matrix-chain multiplication order (MS18). The numbers p0, p1, ..., pn describe n matrices: matrix Ai has p(i-1) rows and p(i)
 * columns. Multiplying a p x q matrix by a q x r matrix costs p*q*r scalar multiplications. {@code m[i][j]} is the cheapest cost of
 * multiplying Ai..Aj; it is the best over split points k of m[i][k] + m[k+1][j] + p(i-1)*p(k)*p(j). Chains are filled from short to long. The
 * smallest k wins a tie.
 *
 * <p>Result: {@link Result#value()} is the cheapest cost and {@link Result#text()} the bracketing, for example "((A1A2)A3)".
 */
public final class MatrixChain extends DpAlgorithm<ArrayInput> {
    public static final String ID = "matrix-chain";

    private static final List<String> PSEUDOCODE = List.of(
            "matrix Ai has p[i-1] rows and p[i] columns; m[i][i] = 0",
            "for chain length L = 2..n",
            "    for each start i (so j = i + L - 1)",
            "        m[i][j] = the smallest, over split k = i..j-1, of m[i][k] + m[k+1][j] + p[i-1] * p[k] * p[j]",
            "        remember the best k as split[i][j]",
            "the answer is m[1][n]",
            "read the bracketing from split[1][n], splitting both sides the same way");

    public MatrixChain() {
        super(ArrayInput.class, InputKind.ARRAY, "a list of numbers");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Matrix-chain multiplication";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(ArrayInput input, Params params) {
        List<Integer> p = input.values();
        int n = p.size() - 1;
        if (n < 2) {
            throw new IllegalArgumentException(name() + " needs at least three numbers (two matrices).");
        }
        for (int v : p) {
            if (v < 1) {
                throw new IllegalArgumentException(name() + " needs every size to be at least 1.");
            }
        }
        List<String> heads = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            heads.add("A" + i);
        }
        GridFrame.Builder paint = GridFrame.builder("", heads, heads);
        TraceRecorder rec = new TraceRecorder().counter("splits tried", 0).counter("cost", 0);
        long[][] m = new long[n + 1][n + 1];
        int[][] split = new int[n + 1][n + 1];
        StringBuilder dims = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            dims.append(i == 1 ? "" : ", ").append("A").append(i).append(" is ").append(p.get(i - 1)).append("x").append(p.get(i));
            paint.set(i - 1, i - 1, "0").state(i - 1, i - 1, State.VISITED);
        }
        rec.step(paint.build(), 1, "The matrices: " + dims + ". A single matrix costs 0");
        long tried = 0;
        for (int len = 2; len <= n; len++) {
            for (int i = 1; i + len - 1 <= n; i++) {
                int j = i + len - 1;
                long best = Long.MAX_VALUE;
                int bestK = -1;
                for (int k = i; k < j; k++) {
                    tried++;
                    long cost = m[i][k] + m[k + 1][j] + (long) p.get(i - 1) * p.get(k) * p.get(j);
                    if (cost < best) {
                        best = cost;
                        bestK = k;
                    }
                }
                m[i][j] = best;
                split[i][j] = bestK;
                rec.set("splits tried", tried);
                rec.set("cost", best);
                paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
                paint.set(i - 1, j - 1, Long.toString(best)).state(i - 1, j - 1, State.CHOSEN);
                paint.state(i - 1, bestK - 1, State.FRONTIER).state(bestK, j - 1, State.FRONTIER);
                rec.step(paint.build(), 4, "A" + i + ".." + "A" + j + ": best split after A" + bestK + " costs m[" + i + "][" + bestK + "] + m["
                        + (bestK + 1) + "][" + j + "] + " + p.get(i - 1) + "*" + p.get(bestK) + "*" + p.get(j) + " = " + best);
            }
        }
        paint.recolour(State.CHOSEN, State.VISITED).recolour(State.FRONTIER, State.VISITED);
        paint.state(0, n - 1, State.PATH);
        String order = n == 1 ? "A1" : bracket(split, 1, n);
        rec.set("cost", m[1][n]);
        rec.step(paint.build(), 6, "The cheapest way costs " + m[1][n] + " multiplications. Order: " + order);
        Result result = Result.builder().value(m[1][n]).text(order).build();
        return new Trace(ID, params, rec.steps(), result);
    }

    static String bracket(int[][] split, int i, int j) {
        if (i == j) {
            return "A" + i;
        }
        return "(" + bracket(split, i, split[i][j]) + bracket(split, split[i][j] + 1, j) + ")";
    }
}

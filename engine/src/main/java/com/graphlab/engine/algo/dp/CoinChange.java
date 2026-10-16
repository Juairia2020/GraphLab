package com.graphlab.engine.algo.dp;

import com.graphlab.engine.input.CoinInput;
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
 * Fewest coins for an amount (MS12). {@code T[i][a]} is the fewest coins that make {@code a} using only the first
 * {@code i} coin values; each coin may be used again and again, so "take" reads from the same row. The table is then
 * walked back to find which coins were used.
 *
 * <p>Result: {@link Result#value()} is the fewest coins, or -1 when the amount cannot be made; {@link Result#chosen()}
 * holds the 0-based index of each coin used (a coin used twice appears twice); {@link Result#text()} is like "3 + 3";
 * {@link Result#matrix()} is the table, with +infinity for "impossible".
 */
public final class CoinChange extends DpAlgorithm<CoinInput> {
    public static final String ID = "coin-change";

    private static final List<String> PSEUDOCODE = List.of(
            "T[0][0] = 0 and T[0][a] = infinity for a > 0  (no coins make a positive amount)",
            "for each coin i = 1..n and each amount a = 0..target",
            "    skip = T[i-1][a]",
            "    take = T[i][a - coin_i] + 1   (only if coin_i <= a; the same coin may be used again)",
            "    T[i][a] = the smaller of skip and take",
            "start at T[n][target] and walk back",
            "    if T[i][a] equals T[i-1][a]: coin i is not needed, go up; otherwise use coin i and a = a - coin_i",
            "the answer is T[n][target]; infinity means the amount cannot be made");

    private static final int INF = Integer.MAX_VALUE / 2;

    public CoinChange() {
        super(CoinInput.class, InputKind.COINS, "coins and an amount");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Coin change";
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    static String show(int value) {
        return value >= INF ? INFINITY : Integer.toString(value);
    }

    @Override
    protected Trace solve(CoinInput input, Params params) {
        List<Integer> coins = input.coins();
        int n = coins.size();
        int target = input.amount();

        List<String> rowHeaders = new ArrayList<>();
        rowHeaders.add("0: none");
        for (int i = 0; i < n; i++) {
            rowHeaders.add((i + 1) + ": coin " + coins.get(i));
        }
        List<String> colHeaders = new ArrayList<>();
        for (int a = 0; a <= target; a++) {
            colHeaders.add(Integer.toString(a));
        }

        int[][] t = new int[n + 1][target + 1];
        GridFrame.Builder paint = GridFrame.builder("coin \\ amount", rowHeaders, colHeaders);
        TraceRecorder rec = new TraceRecorder().counter("cells filled", 0).counter("fewest coins", 0);

        for (int a = 0; a <= target; a++) {
            t[0][a] = a == 0 ? 0 : INF;
            paint.set(0, a, show(t[0][a])).state(0, a, State.VISITED);
        }
        long filled = target + 1;
        rec.set("cells filled", filled);
        rec.step(paint.build(), 1, "Row 0: with no coins only the amount 0 can be made (with 0 coins); every other amount is impossible");

        for (int i = 1; i <= n; i++) {
            int coin = coins.get(i - 1);
            for (int a = 0; a <= target; a++) {
                int skip = t[i - 1][a];
                boolean fits = coin <= a;
                int take = fits && t[i][a - coin] < INF ? t[i][a - coin] + 1 : INF;
                t[i][a] = Math.min(skip, take);
                filled++;
                rec.set("cells filled", filled);

                settle(paint);
                paint.set(i, a, show(t[i][a]));
                paint.state(i - 1, a, State.FRONTIER);
                if (fits) {
                    paint.state(i, a - coin, State.FRONTIER);
                }
                paint.state(i, a, State.CHOSEN);
                String message;
                if (!fits) {
                    message = "Coin " + coin + " is bigger than the amount " + a + ", so copy " + show(skip) + " from above";
                } else {
                    message = "Amount " + a + ", coin " + coin + ": skip it = " + show(skip) + ", take it = "
                            + show(t[i][a - coin]) + " + 1 = " + show(take) + ". "
                            + (take < skip ? "Taking is better: " + show(take) : "Skipping is as good or better: " + show(skip));
                }
                rec.step(paint.build(), fits ? 5 : 3, message);
            }
        }
        settle(paint);
        int best = t[n][target];
        rec.set("fewest coins", best >= INF ? 0 : best);
        rec.step(paint.build(), 5, best >= INF
                ? "The table is full. The amount " + target + " cannot be made with these coins"
                : "The table is full. The fewest coins for " + target + " is " + best);

        List<Integer> used = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        if (best < INF) {
            int i = n;
            int a = target;
            paint.state(i, a, State.PATH);
            while (a > 0) {
                if (t[i][a] == t[i - 1][a]) {
                    i--;
                    paint.state(i, a, State.PATH);
                    rec.step(paint.build(), 7, "T[" + (i + 1) + "] equals the cell above, so coin " + coins.get(i) + " is not needed here");
                } else {
                    int coin = coins.get(i - 1);
                    used.add(i - 1);
                    text.append(text.length() == 0 ? "" : " + ").append(coin);
                    a -= coin;
                    paint.state(i, a, State.PATH);
                    rec.step(paint.build(), 7, "The cell differs from the one above, so use coin " + coin + ". Amount left " + a);
                }
            }
        }
        rec.step(paint.build(), 8, best >= INF
                ? "Done: " + target + " cannot be made"
                : "Done: " + target + " = " + text + ", which is " + best + (best == 1 ? " coin" : " coins"));

        List<List<Double>> matrix = new ArrayList<>();
        for (int[] row : t) {
            List<Double> r = new ArrayList<>();
            for (int v : row) {
                r.add(v >= INF ? Double.POSITIVE_INFINITY : (double) v);
            }
            matrix.add(r);
        }
        Result result = Result.builder().value(best >= INF ? -1 : best).chosen(used).text(text.toString()).matrix(matrix).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

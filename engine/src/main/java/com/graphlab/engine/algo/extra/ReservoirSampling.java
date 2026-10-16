package com.graphlab.engine.algo.extra;

import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.Params;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Trace;
import com.graphlab.engine.trace.TraceRecorder;
import com.graphlab.engine.trace.Visual;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reservoir sampling, Algorithm R (MS18). The list is a stream read once, from left to right. The first {@value #K} items go into the
 * reservoir. For the item at position i (counting from 1, after the first K), pick a random whole number j from 1 to i; if j is at most
 * K, the item replaces reservoir slot j. Afterwards every item has the same chance, K/n, of being in the reservoir. The random numbers
 * come from {@link Random} seeded with {@link Params#seed()}, so a run can be repeated.
 *
 * <p>Result: {@link Result#chosen()} is the positions (0-based) of the items in the reservoir, in slot order;
 * {@link Result#text()} their values.
 */
public final class ReservoirSampling extends ArrayInputAlgorithm {
    public static final String ID = "reservoir-sampling";
    /** The size of the reservoir. A shorter stream is kept whole. */
    public static final int K = 3;

    private static final List<String> PSEUDOCODE = List.of(
            "the reservoir has room for K items; read the stream from left to right",
            "the first K items go straight into the reservoir",
            "for each later item at position i (counting from 1)",
            "    pick a random whole number j from 1 to i",
            "    if j <= K: the item replaces slot j of the reservoir",
            "    otherwise: the item is dropped",
            "when the stream ends, the reservoir is a fair sample: every item had chance K/n");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Reservoir sampling";
    }

    @Override
    public AlgorithmCategory category() {
        return AlgorithmCategory.RANDOMIZED;
    }

    @Override
    public Visual visual() {
        return Visual.ARRAY;
    }

    @Override
    public List<String> pseudocode() {
        return PSEUDOCODE;
    }

    @Override
    protected Trace solve(ArrayInput input, Params params) {
        List<Integer> a = input.values();
        int n = a.size();
        int k = Math.min(K, n);
        Random random = new Random(params.seed());
        ArrayFrame.Builder paint = ArrayFrame.builder(a);
        TraceRecorder rec = new TraceRecorder().counter("seen", 0).counter("replaced", 0);
        int[] slot = new int[k];   // stream position held by each reservoir slot
        int replaced = 0;
        rec.step(paint.build(), 1, "A stream of " + n + " items. The reservoir keeps " + k + ". Read left to right");
        for (int i = 0; i < n; i++) {
            rec.set("seen", i + 1);
            paint.recolour(State.CHOSEN, State.IDLE);
            if (i < k) {
                slot[i] = i;
                paint.state(i, State.PATH).text(i, "slot " + (i + 1));
                rec.step(paint.build(), 2, "Item " + (i + 1) + " (" + a.get(i) + ") goes into slot " + (i + 1));
                continue;
            }
            paint.state(i, State.CHOSEN);
            int j = random.nextInt(i + 1) + 1;
            if (j <= k) {
                int old = slot[j - 1];
                paint.state(old, State.FAIL).text(old, "dropped");
                slot[j - 1] = i;
                paint.state(i, State.PATH).text(i, "slot " + j);
                replaced++;
                rec.set("replaced", replaced);
                rec.step(paint.build(), 5, "Item " + (i + 1) + " (" + a.get(i) + "): random j = " + j + " from 1 to " + (i + 1)
                        + ", j <= " + k + ", so it replaces " + a.get(old) + " in slot " + j);
            } else {
                paint.state(i, State.FAIL).text(i, "dropped");
                rec.step(paint.build(), 6, "Item " + (i + 1) + " (" + a.get(i) + "): random j = " + j + " from 1 to " + (i + 1)
                        + ", j > " + k + ", so it is dropped");
            }
        }
        List<Integer> chosen = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int s = 0; s < k; s++) {
            chosen.add(slot[s]);
            text.append(text.length() == 0 ? "" : " ").append(a.get(slot[s]));
        }
        rec.step(paint.build(), 7, "The reservoir holds " + text + ". Each item had chance " + k + "/" + n);
        Result result = Result.builder().chosen(chosen).text(text.toString()).value(k).build();
        return new Trace(ID, params, rec.steps(), result);
    }
}

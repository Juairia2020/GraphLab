package com.graphlab.engine.experiment;

import java.util.Random;

/**
 * The hiring (secretary) problem. n candidates come in random order and each must be accepted or refused on the
 * spot. The rule: look at the first n/e candidates without hiring, then hire the first one better than all of them
 * (or the last one, if none is). The histogram value is the rank of the person hired, 1 for the very best.
 * The best candidate is hired about 37% of the time.
 */
public final class SecretaryTrials implements Trials {
    private final int n;

    public SecretaryTrials(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("Need at least 2 candidates");
        }
        this.n = n;
    }

    @Override
    public int trial(long seed) {
        Random random = new Random(seed);
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[i] = i + 1;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = rank[i];
            rank[i] = rank[j];
            rank[j] = t;
        }
        int look = (int) Math.floor(n / Math.E);
        int bestSeen = Integer.MAX_VALUE;
        for (int i = 0; i < look; i++) {
            bestSeen = Math.min(bestSeen, rank[i]);
        }
        for (int i = look; i < n; i++) {
            if (rank[i] < bestSeen) {
                return rank[i];
            }
        }
        return rank[n - 1];
    }
}

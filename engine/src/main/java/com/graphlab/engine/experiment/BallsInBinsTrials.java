package com.graphlab.engine.experiment;

import java.util.Random;

/**
 * Random hashing: throw n balls into n bins, each ball into a uniformly random bin. The histogram value is the
 * load of the fullest bin. It is about ln n / ln ln n, much more than the average of 1.
 */
public final class BallsInBinsTrials implements Trials {
    private final int n;

    public BallsInBinsTrials(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Need at least 1 bin");
        }
        this.n = n;
    }

    @Override
    public int trial(long seed) {
        Random random = new Random(seed);
        int[] load = new int[n];
        int fullest = 0;
        for (int ball = 0; ball < n; ball++) {
            int bin = random.nextInt(n);
            load[bin]++;
            fullest = Math.max(fullest, load[bin]);
        }
        return fullest;
    }
}

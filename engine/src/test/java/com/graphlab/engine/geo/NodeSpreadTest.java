package com.graphlab.engine.geo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class NodeSpreadTest {
    private static double minDistance(double[][] xy) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < xy.length; i++) {
            for (int j = i + 1; j < xy.length; j++) {
                best = Math.min(best, Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]));
            }
        }
        return best;
    }

    @Test
    public void everyPairEndsAtLeastTheGapApart() {
        Random r = new Random(7);
        for (int round = 0; round < 300; round++) {
            int n = 2 + r.nextInt(12);
            double spread = r.nextBoolean() ? 1 : 40;   // a tight cluster or a loose one
            double[][] xy = new double[n][2];
            for (double[] p : xy) {
                p[0] = r.nextDouble() * spread;
                p[1] = r.nextDouble() * spread;
            }
            NodeSpread.spread(xy, 90);
            assertTrue("round " + round + " min " + minDistance(xy), minDistance(xy) >= 90 - 1e-6);
        }
    }

    @Test
    public void pointsOnTheSameSpotAreSeparated() {
        double[][] xy = new double[6][2];
        NodeSpread.spread(xy, 90);
        assertTrue(minDistance(xy) >= 90 - 1e-6);
    }

    @Test
    public void pointsThatAreAlreadyFarApartDoNotMove() {
        double[][] xy = {{0, 0}, {200, 0}, {0, 300}};
        double[][] copy = {{0, 0}, {200, 0}, {0, 300}};
        NodeSpread.spread(xy, 90);
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(copy[i], xy[i], 0);
        }
    }

    @Test
    public void theResultIsRepeatable() {
        double[][] a = {{0, 0}, {5, 5}, {5, 5}, {10, 0}};
        double[][] b = {{0, 0}, {5, 5}, {5, 5}, {10, 0}};
        NodeSpread.spread(a, 90);
        NodeSpread.spread(b, 90);
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(a[i], b[i], 0);
        }
    }
}

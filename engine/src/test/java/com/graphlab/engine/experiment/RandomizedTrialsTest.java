package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RandomizedTrialsTest {
    private static Histogram run(Trials t, int count) {
        Histogram h = new Histogram();
        for (int i = 0; i < count; i++) {
            h.add(t.trial(KargerTrials.seedFor(7, i)));
        }
        return h;
    }

    private static double mean(Histogram h) {
        double sum = 0;
        for (int[] e : h.entries()) {
            sum += (double) e[0] * e[1];
        }
        return sum / h.total();
    }

    @Test
    public void sameSeedSameResult() {
        for (ExperimentAlgorithm a : ExperimentAlgorithm.values()) {
            if (!a.needsGraph()) {
                Trials t = a.create(null);
                assertEquals(a.id(), t.trial(5), t.trial(5));
            }
        }
    }

    @Test
    public void quickSortSmallSorts() {
        // n = 2: exactly one comparison in total, 0.5 per item rounds to 1 (Math.round(0.5) is 1).
        assertEquals(1L, new QuickSortTrials(2).comparisons(1));
        // n = 3: between 2 (middle pivot) and 3 comparisons.
        for (long s = 0; s < 50; s++) {
            long c = new QuickSortTrials(3).comparisons(s);
            assertTrue(c == 2 || c == 3);
        }
    }

    @Test
    public void quickSortOnSortedInputBeatsTheQuadraticCase() {
        double mean = mean(run(new QuickSortTrials(100), 300));
        // Expected about 2 ln 100 = 9.2 per item; the first-pivot case would be about 50.
        assertTrue("mean " + mean, mean > 6 && mean < 13);
    }

    @Test
    public void quickSelectFindsTheMedianWithFewComparisons() {
        QuickSelectTrials q = new QuickSelectTrials(101);
        for (long s = 0; s < 100; s++) {
            int[] found = new int[1];
            long c = q.comparisons(s, found);
            assertEquals(50, found[0]);
            assertTrue(c >= 100 && c <= 101L * 101 / 2);
        }
        double mean = mean(run(new QuickSelectTrials(100), 300));
        assertTrue("mean " + mean, mean > 2 && mean < 5.5);
    }

    @Test
    public void ballsInBinsFullestBinIsSensible() {
        assertEquals(1, new BallsInBinsTrials(1).trial(3));
        Histogram h = run(new BallsInBinsTrials(100), 300);
        assertTrue(h.smallest() >= 2);
        assertTrue(h.largest() <= 10);
        double mean = mean(h);
        assertTrue("mean " + mean, mean > 3 && mean < 5.5);
    }

    @Test
    public void secretaryHiresTheBestAboutAThirdOfTheTime() {
        Histogram h = run(new SecretaryTrials(100), 3000);
        assertTrue(h.smallest() == 1);
        double share = h.smallestShare();
        assertTrue("share " + share, share > 0.30 && share < 0.44);
        assertTrue(h.largest() <= 100);
    }

    @Test
    public void badSizesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new QuickSortTrials(1));
        assertThrows(IllegalArgumentException.class, () -> new QuickSelectTrials(1));
        assertThrows(IllegalArgumentException.class, () -> new BallsInBinsTrials(0));
        assertThrows(IllegalArgumentException.class, () -> new SecretaryTrials(1));
    }

    @Test
    public void algorithmLookup() {
        assertSame(ExperimentAlgorithm.QUICK_SORT, ExperimentAlgorithm.byId("quicksort"));
        assertSame(ExperimentAlgorithm.KARGER, ExperimentAlgorithm.byId("nonsense"));
        assertTrue(ExperimentAlgorithm.KARGER.needsGraph());
        assertThrows(IllegalArgumentException.class, () -> ExperimentAlgorithm.KARGER.create(null));
        for (ExperimentAlgorithm a : ExperimentAlgorithm.values()) {
            assertSame(a, ExperimentAlgorithm.byId(a.id()));
            assertNotEquals("", a.title());
        }
    }

    @Test
    public void runnerAcceptsAnyTrials() {
        ExperimentRunner.Outcome out = ExperimentRunner.run(new BallsInBinsTrials(20), 1, 50, new PauseGate(), 10, 0, null);
        assertEquals(50, out.done());
        assertEquals(50, out.histogram().total());
    }

    @Test
    public void recordSummaryUsesTheAlgorithmsWords() {
        Histogram h = new Histogram();
        h.add(3, 4);
        h.add(4, 6);
        String s = new ExperimentRecord("balls", 10, 10, 1, h).summary();
        assertTrue(s, s.contains("lowest load 3"));
        assertTrue(new ExperimentRecord("karger", 10, 10, 1, h).summary().contains("best cut 3"));
    }
}

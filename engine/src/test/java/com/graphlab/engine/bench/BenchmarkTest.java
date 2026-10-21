package com.graphlab.engine.bench;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class BenchmarkTest {
    @Test
    public void aWorkloadThatIsExactlyNLogNFitsPerfectly() {
        // a fake clock that advances by n*log2(n) microseconds per run makes the expected curve exact
        AtomicLong clock = new AtomicLong();
        Benchmark.Workload w = (n, seed) -> () -> clock.addAndGet((long) (Benchmark.shape(n) * 1000));
        Benchmark.Result r = Benchmark.run(Benchmark.doubling(1000, 6), 1, 3, w, clock::get, 1, () -> false);
        assertEquals(6, r.points().size());
        assertEquals(1.0, r.rSquared(), 1e-6);
        assertTrue(r.maxRelativeError() < 1e-3);
        assertEquals(0.001, r.scale(), 1e-6);
    }

    @Test
    public void aQuadraticWorkloadDoesNotFitTheNLogNCurveWell() {
        AtomicLong clock = new AtomicLong();
        Benchmark.Workload w = (n, seed) -> () -> clock.addAndGet((long) n * n);
        Benchmark.Result r = Benchmark.run(Benchmark.doubling(1000, 7), 0, 1, w, clock::get, 1, () -> false);
        assertTrue("max error " + r.maxRelativeError(), r.maxRelativeError() > 0.3);
    }

    @Test
    public void theMedianIgnoresOneSlowRun() {
        AtomicLong clock = new AtomicLong();
        int[] call = {0};
        Benchmark.Workload w = (n, seed) -> () -> clock.addAndGet(call[0]++ % 3 == 1 ? 900_000_000L : 1_000_000L);
        Benchmark.Result r = Benchmark.run(List.of(10, 20), 0, 3, w, clock::get, 1, () -> false);
        assertTrue("median kept: " + r.points().get(0).millis(), r.points().get(0).millis() < 5);
    }

    @Test
    public void cancellingKeepsTheSizesAlreadyMeasured() {
        AtomicLong clock = new AtomicLong();
        int[] asked = {0};
        Benchmark.Workload w = (n, seed) -> () -> clock.addAndGet(n);
        Benchmark.Result r = Benchmark.run(Benchmark.doubling(100, 6), 0, 1, w, clock::get, 1, () -> ++asked[0] > 3);
        assertEquals(3, r.points().size());
        assertTrue(r.cancelled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void oneSizeIsNotACurve() {
        Benchmark.run(List.of(100), 0, 1, (n, s) -> () -> { }, System::nanoTime, 1, () -> false);
    }

    @Test
    public void realKruskalTimingGrowsLikeNLogN() {
        // loose on purpose: real clocks are noisy. 8x the nodes should cost clearly more, and far less than 8^2 times.
        Benchmark.Result r = Benchmark.run(Benchmark.doubling(20_000, 4), 2, 5, Benchmark.kruskal(), System::nanoTime, 7, () -> false);
        double small = r.points().get(0).millis();
        double large = r.points().get(3).millis();
        assertTrue("larger input takes longer: " + small + " vs " + large, large > small);
        assertTrue("not quadratic: ratio " + large / small, large / small < 60);
        assertTrue("shape fits: r2 " + r.rSquared(), r.rSquared() > 0.8);
    }

    @Test
    public void theListenerHearsEachSizeInOrder() {
        AtomicLong clock = new AtomicLong();
        List<Integer> heard = new java.util.ArrayList<>();
        Benchmark.run(Benchmark.doubling(100, 4), 0, 1, (n, s) -> () -> clock.addAndGet(n), clock::get, 1, () -> false,
                (index, total, n, ms) -> heard.add(n * 10 + index));
        assertEquals(List.of(1000, 2001, 4002, 8003), heard);
    }

    @Test
    public void doublingStopsAtTheCap() {
        assertEquals(List.of(1_000_000, 2_000_000, 4_000_000), Benchmark.doubling(1_000_000, 10));
    }
}

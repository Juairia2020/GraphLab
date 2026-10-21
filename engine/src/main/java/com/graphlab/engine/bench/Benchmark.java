package com.graphlab.engine.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Times a workload on inputs of increasing size and compares the measurements with a scaled n log n curve (MS15, G3).
 *
 * <p>Each size is run {@code warmups} times first (so the JIT has compiled the code), then {@code repeats} times; the median
 * is kept because one slow run (a garbage collection) should not bend the chart. The model is {@code scale * n * log2(n)}
 * with the scale chosen by least squares, so the chart shows the SHAPE of growth, not an absolute speed. {@code clock}
 * returns nanoseconds (System.nanoTime in the app; a counter in tests).
 */
public final class Benchmark {
    /** What is timed. */
    public interface Workload {
        /** Prepare the input for size n (not timed). */
        Runnable prepare(int n, long seed);
    }

    public record Point(int n, double millis, double modelMillis) {
    }

    /**
     * @param scale            milliseconds per (n log2 n)
     * @param maxRelativeError the largest |measured - model| / measured over the points
     * @param rSquared         1 means the model explains the measurements perfectly
     */
    public record Result(List<Point> points, double scale, double maxRelativeError, double rSquared, boolean cancelled) {
        public Result {
            points = List.copyOf(points);
        }
    }

    /** Told after each size is measured, so a screen can draw the points so far. */
    public interface Listener {
        void onMeasured(int index, int total, int n, double millis);
    }

    private Benchmark() {
    }

    public static Result run(List<Integer> sizes, int warmups, int repeats, Workload workload, LongSupplier clock, long seed,
                             BooleanSupplier cancelled) {
        return run(sizes, warmups, repeats, workload, clock, seed, cancelled, null);
    }

    public static Result run(List<Integer> sizes, int warmups, int repeats, Workload workload, LongSupplier clock, long seed,
                             BooleanSupplier cancelled, Listener listener) {
        if (sizes.size() < 2 || repeats < 1 || warmups < 0) {
            throw new IllegalArgumentException("Need at least 2 sizes, 1 repeat and 0 or more warm-ups");
        }
        List<Integer> ns = new ArrayList<>();
        List<Double> ms = new ArrayList<>();
        boolean stopped = false;
        for (int n : sizes) {
            if (n < 2) {
                throw new IllegalArgumentException("Sizes must be 2 or more");
            }
            if (cancelled.getAsBoolean()) {
                stopped = true;
                break;
            }
            for (int w = 0; w < warmups; w++) {
                workload.prepare(n, seed + w).run();
            }
            double[] runs = new double[repeats];
            for (int r = 0; r < repeats; r++) {
                Runnable job = workload.prepare(n, seed + 1000 + r);
                long start = clock.getAsLong();
                job.run();
                runs[r] = (clock.getAsLong() - start) / 1_000_000.0;
            }
            Arrays.sort(runs);
            ns.add(n);
            ms.add(runs[repeats / 2]);
            if (listener != null) {
                listener.onMeasured(ns.size() - 1, sizes.size(), n, runs[repeats / 2]);
            }
        }
        return fit(ns, ms, stopped);
    }

    /** Least-squares scale for t = c * n log2 n, and how well it fits. */
    public static Result fit(List<Integer> ns, List<Double> ms, boolean cancelled) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < ns.size(); i++) {
            double f = shape(ns.get(i));
            num += ms.get(i) * f;
            den += f * f;
        }
        double scale = den == 0 ? 0 : num / den;
        double mean = 0;
        for (double v : ms) {
            mean += v;
        }
        mean = ns.isEmpty() ? 0 : mean / ms.size();
        double ssRes = 0;
        double ssTot = 0;
        double worst = 0;
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < ns.size(); i++) {
            double model = scale * shape(ns.get(i));
            points.add(new Point(ns.get(i), ms.get(i), model));
            ssRes += (ms.get(i) - model) * (ms.get(i) - model);
            ssTot += (ms.get(i) - mean) * (ms.get(i) - mean);
            if (ms.get(i) > 0) {
                worst = Math.max(worst, Math.abs(ms.get(i) - model) / ms.get(i));
            }
        }
        double r2 = ssTot == 0 ? 1 : 1 - ssRes / ssTot;
        return new Result(points, scale, worst, r2, cancelled);
    }

    static double shape(int n) {
        return n * (Math.log(n) / Math.log(2));
    }

    /** Sizes doubling from {@code first}: first, 2 first, 4 first ... {@code count} of them. */
    public static List<Integer> doubling(int first, int count) {
        List<Integer> out = new ArrayList<>();
        long n = first;
        for (int i = 0; i < count; i++) {
            if (n > 5_000_000) {
                break;
            }
            out.add((int) n);
            n *= 2;
        }
        return out;
    }

    /**
     * Kruskal on a random graph with n nodes and 4n edges: sort the edges, then union-find. Written without the engine's
     * Graph class (which holds at most 60 nodes and records every step), so it measures the algorithm and not the tracing.
     */
    public static Workload kruskal() {
        return (n, seed) -> {
            Random random = new Random(seed);
            int m = 4 * n;
            int[] from = new int[m];
            int[] to = new int[m];
            long[] order = new long[m];   // weight in the high bits, edge index in the low bits: one primitive sort
            for (int i = 0; i < m; i++) {
                from[i] = random.nextInt(n);
                to[i] = random.nextInt(n);
                order[i] = ((long) random.nextInt(1_000_000) << 24) | i;
            }
            return () -> {
                Arrays.sort(order);
                int[] parent = new int[n];
                for (int i = 0; i < n; i++) {
                    parent[i] = i;
                }
                int used = 0;
                for (int k = 0; k < m && used < n - 1; k++) {
                    int e = (int) (order[k] & 0xFFFFFF);
                    int a = find(parent, from[e]);
                    int b = find(parent, to[e]);
                    if (a != b) {
                        parent[a] = b;
                        used++;
                    }
                }
                sink = used;
            };
        };
    }

    private static volatile int sink;

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }
}

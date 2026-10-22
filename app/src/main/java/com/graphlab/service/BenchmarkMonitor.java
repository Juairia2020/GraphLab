package com.graphlab.service;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.bench.Benchmark;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Where the benchmark service tells the screen how far it is (MS15). One per process, like the experiment monitor. */
public final class BenchmarkMonitor {
    /** An immutable picture: the points measured so far, and the fitted result once the run is over. */
    public static final class Snapshot {
        public final boolean running;
        public final int total;
        public final List<Integer> ns;
        public final List<Double> ms;
        @Nullable
        public final Benchmark.Result result;

        Snapshot(boolean running, int total, List<Integer> ns, List<Double> ms, @Nullable Benchmark.Result result) {
            this.running = running;
            this.total = total;
            this.ns = List.copyOf(ns);
            this.ms = List.copyOf(ms);
            this.result = result;
        }
    }

    public interface Listener {
        void onChanged(@NonNull Snapshot snapshot);
    }

    private static final BenchmarkMonitor INSTANCE = new BenchmarkMonitor();

    public static BenchmarkMonitor get() {
        return INSTANCE;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot current = new Snapshot(false, 0, new ArrayList<>(), new ArrayList<>(), null);

    private BenchmarkMonitor() {
    }

    @NonNull
    public Snapshot current() {
        return current;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    void publish(boolean running, int total, List<Integer> ns, List<Double> ms, @Nullable Benchmark.Result result) {
        final Snapshot snapshot = new Snapshot(running, total, ns, ms, result);
        current = snapshot;
        main.post(() -> {
            for (Listener l : listeners) {
                l.onChanged(snapshot);
            }
        });
    }
}

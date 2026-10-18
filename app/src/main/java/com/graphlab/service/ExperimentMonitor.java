package com.graphlab.service;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.experiment.Histogram;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where the running experiment tells the screens how it is doing. The service (a background thread) publishes a
 * {@link Snapshot}; screens that are on display listen and are called on the main thread. One instance lives in the
 * AppContainer, so a screen that is rotated or comes back from the background just reads {@link #current()}.
 * Works because the service and the screens share one process.
 */
public final class ExperimentMonitor {

    public enum State {
        IDLE, RUNNING, PAUSED
    }

    /** An immutable picture of the experiment at one moment. */
    public static final class Snapshot {
        public final State state;
        public final String graphName;
        public final int done;
        public final int total;
        /** The counts so far; a private copy. */
        public final Histogram histogram;
        /** Meaningful when PAUSED: true if the low battery paused it, false if the user did. */
        public final boolean pausedByBattery;
        /** The id of the last result saved in this process, or 0. A change means "new result to show". */
        public final long lastRunId;
        /** Set when the service could not start or lost its graph; shown once. */
        @Nullable
        public final String error;

        public Snapshot(@NonNull State state, @NonNull String graphName, int done, int total, @NonNull Histogram histogram,
                        boolean pausedByBattery, long lastRunId, @Nullable String error) {
            this.state = state;
            this.graphName = graphName;
            this.done = done;
            this.total = total;
            this.histogram = histogram;
            this.pausedByBattery = pausedByBattery;
            this.lastRunId = lastRunId;
            this.error = error;
        }

        public boolean active() {
            return state != State.IDLE;
        }
    }

    /** Called on the main thread. */
    public interface Listener {
        void onChanged(@NonNull Snapshot snapshot);
    }

    private static final Snapshot IDLE = new Snapshot(State.IDLE, "", 0, 0, new Histogram(), false, 0L, null);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot current = IDLE;

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

    /** Any thread. */
    public void publish(@NonNull Snapshot snapshot) {
        current = snapshot;
        main.post(() -> {
            for (Listener listener : listeners) {
                listener.onChanged(snapshot);
            }
        });
    }

    /** Back to idle, keeping the id of the last saved result. */
    @NonNull
    public Snapshot idleAfter(long lastRunId, @Nullable String error) {
        return new Snapshot(State.IDLE, "", 0, 0, new Histogram(), false, lastRunId, error);
    }
}

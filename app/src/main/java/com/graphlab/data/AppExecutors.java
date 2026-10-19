package com.graphlab.data;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;

/**
 * Where background work runs. All database and file work goes through one single-threaded
 * executor, so tasks run in the order they were submitted: a save that was started before a
 * list query has always finished when the query runs. Results are handed back on the main
 * thread.
 */
public final class AppExecutors {

    /** Work that may fail. */
    public interface Work<T> {
        T run() throws Exception;
    }

    /** What the main thread gets back. Exactly one of the two methods is called. */
    public interface Callback<T> {
        void onSuccess(T result);

        void onFailure(@NonNull Exception error);
    }

    private final ExecutorService disk = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "graphlab-disk");
        thread.setDaemon(true);
        return thread;
    });
    /** Two threads for requests to the web: a slow server must never hold up the database queue above. */
    private final ExecutorService network = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "graphlab-network");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Runs {@code work} off the main thread; no result. Failures are swallowed on purpose (logging must never crash the app). */
    public void disk(@NonNull Runnable work) {
        disk.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException ignored) {
                // A failed background write must not take the app down.
            }
        });
    }

    /** Runs {@code work} off the main thread and delivers its result (or error) on the main thread. */
    public <T> void disk(@NonNull Work<T> work, @NonNull Callback<T> callback) {
        disk.execute(() -> {
            T result;
            try {
                result = work.run();
            } catch (Exception e) {
                main.post(() -> callback.onFailure(e));
                return;
            }
            main.post(() -> callback.onSuccess(result));
        });
    }

    /** Like {@link #disk(Work, Callback)} but on the network threads, for work that waits for the web. */
    public <T> void network(@NonNull Work<T> work, @NonNull Callback<T> callback) {
        network.execute(() -> {
            T result;
            try {
                result = work.run();
            } catch (Exception e) {
                main.post(() -> callback.onFailure(e));
                return;
            }
            main.post(() -> callback.onSuccess(result));
        });
    }

    /**
     * Like {@link #disk(Work)} but returns a Future, for a background thread (the experiment service) that must wait for
     * the answer. The work still runs on the disk thread, in order with every other save. Never call get() on the main thread.
     */
    @NonNull
    public <T> Future<T> diskFuture(@NonNull Work<T> work) {
        return disk.submit(work::run);
    }

    public void main(@NonNull Runnable work) {
        main.post(work);
    }
}

package com.graphlab.data;

import android.content.Context;
import androidx.annotation.NonNull;
import com.graphlab.data.db.GraphDb;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.data.media.BackgroundStore;
import com.graphlab.service.ExperimentMonitor;

/**
 * The objects the whole app shares, created once in the Application and wired by hand (no
 * dependency-injection library). Reach it with {@code GraphLabApp.container(context)}.
 */
public final class AppContainer {
    private final AppExecutors executors = new AppExecutors();
    private final GraphRepository repository;
    private final LifecycleLogger lifecycleLogger;
    private final ExperimentMonitor monitor = new ExperimentMonitor();
    private final BackgroundStore backgrounds;
    private final ProgressStore progress;

    public AppContainer(@NonNull Context context) {
        // The database file is only opened when the first query runs, on the disk executor.
        this.repository = new GraphRepository(new GraphDb(context));
        this.lifecycleLogger = new LifecycleLogger(executors, repository);
        this.backgrounds = new BackgroundStore(context);
        this.progress = new ProgressStore(context, executors, repository);
        this.progress.load();   // queued first on the disk thread, so later changes cannot be lost
    }

    @NonNull
    public AppExecutors executors() {
        return executors;
    }

    @NonNull
    public GraphRepository repository() {
        return repository;
    }

    @NonNull
    public LifecycleLogger lifecycleLogger() {
        return lifecycleLogger;
    }

    /** How the running experiment (MS8) is doing; the service publishes here and the Lab listens. */
    @NonNull
    public ExperimentMonitor monitor() {
        return monitor;
    }

    /** xp, gems, streak, puzzle stars and mastery (MS12). */
    @NonNull
    public ProgressStore progress() {
        return progress;
    }

    /** The photos behind graphs (MS10): copied into the app's own folder, so no storage permission is needed. */
    @NonNull
    public BackgroundStore backgrounds() {
        return backgrounds;
    }
}

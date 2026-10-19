package com.graphlab.data;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.ui.me.LifecycleLogActivity;

/**
 * Writes every Activity lifecycle event to the database, for the lifecycle log screen in Me.
 * Registered once in the Application, so it sees all Activities. Rotating the phone shows up as
 * "stopped (rotating)", "destroyed (rotating)" then "created", which is the quickest way to see
 * what the Activity lifecycle really does.
 *
 * <p>The log screen does not log itself, so looking at the list does not add rows to it.
 */
public final class LifecycleLogger implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "GraphLab.Lifecycle";
    private static final int KEEP = 500;
    private static final int TRIM_EVERY = 100;

    private final AppExecutors executors;
    private final GraphRepository repository;
    private int sinceTrim;

    public LifecycleLogger(@NonNull AppExecutors executors, @NonNull GraphRepository repository) {
        this.executors = executors;
        this.repository = repository;
    }

    private void log(@NonNull Activity activity, @NonNull String event) {
        if (activity instanceof LifecycleLogActivity) {
            return;
        }
        final String source = activity.getClass().getSimpleName();
        final long now = System.currentTimeMillis();
        Log.d(TAG, source + " " + event);
        final boolean trim = ++sinceTrim >= TRIM_EVERY;
        if (trim) {
            sinceTrim = 0;
        }
        executors.disk(() -> {
            repository.addLog(now, source, event);
            if (trim) {
                repository.trimLogs(KEEP);
            }
        });
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        log(activity, savedInstanceState == null ? "created" : "created (restoring saved state)");
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        log(activity, "started");
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        log(activity, "resumed");
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        log(activity, "paused");
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        log(activity, activity.isChangingConfigurations() ? "stopped (rotating)" : "stopped");
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        log(activity, "saved state");
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        String reason = activity.isChangingConfigurations() ? " (rotating)" : activity.isFinishing() ? " (finishing)" : "";
        log(activity, "destroyed" + reason);
    }
}

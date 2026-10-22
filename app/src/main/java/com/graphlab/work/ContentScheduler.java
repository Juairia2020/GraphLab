package com.graphlab.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.graphlab.data.content.ContentSync;
import java.util.concurrent.TimeUnit;

/** Plans the content refresh (MS7): every 24 hours when the device is online, and once on request (a push message). */
public final class ContentScheduler {
    static final String UNIQUE_PERIODIC = "content-refresh";
    static final String UNIQUE_NOW = "content-refresh-now";
    public static final long PERIOD_HOURS = 24;

    private ContentScheduler() {
    }

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Keeps the daily job in place. Called at every app start; an existing job is kept, so the clock does not restart. */
    public static void restore(@NonNull Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!ContentSync.isConfigured()) {
            manager.cancelUniqueWork(UNIQUE_PERIODIC);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ContentRefreshWorker.class, PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(online())
                .build();
        manager.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Refreshes soon, for example because a push message said new content is out. Several requests count as one. */
    public static void refreshNow(@NonNull Context context) {
        if (!ContentSync.isConfigured()) {
            return;
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ContentRefreshWorker.class)
                .setConstraints(online())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request);
    }
}

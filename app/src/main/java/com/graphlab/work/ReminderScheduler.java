package com.graphlab.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.graphlab.data.prefs.ReminderPrefs;
import com.graphlab.engine.remind.ReminderPlanner;
import com.graphlab.engine.remind.ReminderSettings;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Turns {@link ReminderSettings} into WorkManager work: one unique periodic job that repeats every
 * 24 hours, first run at the chosen time. Calling it again with new settings replaces the job, and
 * switching the reminder off cancels it. Android may deliver the reminder a few minutes late to
 * save battery; that is normal for WorkManager.
 */
public final class ReminderScheduler {
    static final String UNIQUE_NAME = "daily-reminder";

    private ReminderScheduler() {
    }

    /**
     * Makes sure the scheduled work exists when the settings say it should. Called at every app
     * start; an existing job is kept as it is, so starting the app never moves the reminder.
     */
    public static void restore(@NonNull Context context) {
        enqueue(context, ReminderPrefs.load(context), ExistingPeriodicWorkPolicy.KEEP);
    }

    /** Called when the user changes the reminder: the job is replaced so the new time takes effect. */
    public static void apply(@NonNull Context context, @NonNull ReminderSettings settings) {
        enqueue(context, settings, ExistingPeriodicWorkPolicy.UPDATE);
    }

    private static void enqueue(Context context, ReminderSettings settings, ExistingPeriodicWorkPolicy policy) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        if (!settings.enabled()) {
            manager.cancelUniqueWork(UNIQUE_NAME);
            return;
        }
        Notifications.ensureChannels(context);
        long delay = ReminderPlanner.delayUntilNextMillis(ZonedDateTime.now(), settings.hour(), settings.minute());
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ReminderWorker.class,
                ReminderPlanner.PERIOD_HOURS, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        manager.enqueueUniquePeriodicWork(UNIQUE_NAME, policy, request);
    }
}

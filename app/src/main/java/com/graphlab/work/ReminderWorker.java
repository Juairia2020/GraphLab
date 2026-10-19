package com.graphlab.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.graphlab.data.prefs.ReminderPrefs;

/**
 * Runs about once a day in the background (WorkManager starts it, even after a reboot) and shows
 * the reminder if it is still switched on (the daily puzzle nudge, MS17, when today's puzzle was not played). It does nothing else, and never fails: a missed
 * reminder is better than a crash.
 */
public final class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (ReminderPrefs.load(context).enabled()) {
            String today = com.graphlab.engine.arena.DailyPuzzle.dateString(System.currentTimeMillis());
            if (com.graphlab.data.prefs.ExtrasPrefs.daily(context, today) == null
                    && !com.graphlab.data.prefs.ExtrasPrefs.dailyStarted(context, today)) {
                Notifications.showDailyNudge(context);   // MS17: the puzzle of the day is still waiting
            } else {
                Notifications.showReminder(context);
            }
        }
        return Result.success();
    }
}

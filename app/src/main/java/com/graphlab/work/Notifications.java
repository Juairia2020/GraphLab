package com.graphlab.work;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.graphlab.R;
import com.graphlab.engine.remind.ReminderPlanner;
import com.graphlab.ui.arena.ArenaActivity;
import java.time.LocalDate;

/** The daily reminder notification: its channel, its text and the intent that opens the Arena. */
public final class Notifications {
    public static final String CHANNEL_REMINDERS = "reminders";
    /** Progress of a long experiment (MS8). Low importance: no sound, it only has to be visible. */
    public static final String CHANNEL_EXPERIMENTS = "experiments";
    /** The playback controls of a run that keeps playing in the background. Low importance: no sound. */
    public static final String CHANNEL_PLAYBACK = "playback";
    /** News and content updates sent through Firebase Cloud Messaging (MS7). */
    public static final String CHANNEL_NEWS = "news";
    private static final int ID_REMINDER = 1001;

    private Notifications() {
    }

    /** Android 8 and newer need a channel before any notification. Creating it twice does nothing. */
    public static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.reminder_channel_description));
        NotificationChannel experiments = new NotificationChannel(CHANNEL_EXPERIMENTS,
                context.getString(R.string.experiment_channel_name), NotificationManager.IMPORTANCE_LOW);
        experiments.setDescription(context.getString(R.string.experiment_channel_description));
        NotificationChannel news = new NotificationChannel(CHANNEL_NEWS,
                context.getString(R.string.news_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        news.setDescription(context.getString(R.string.news_channel_description));
        NotificationChannel playback = new NotificationChannel(CHANNEL_PLAYBACK,
                context.getString(R.string.playback_channel_name), NotificationManager.IMPORTANCE_LOW);
        playback.setDescription(context.getString(R.string.playback_channel_description));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(playback);
            manager.createNotificationChannel(news);
            manager.createNotificationChannel(channel);
            manager.createNotificationChannel(experiments);
        }
    }

    /** True if the user (or the system) allows this app to show notifications. */
    public static boolean areAllowed(@NonNull Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Shows today's reminder, if notifications are allowed. Tapping it starts {@link ArenaActivity}
     * with an explicit intent that carries {@link ArenaActivity#EXTRA_FROM_REMINDER}: this is how the
     * background worker talks to an Activity.
     */
    @SuppressWarnings("MissingPermission")   // checked through areAllowed(), which also covers Android 13's runtime permission
    public static void showReminder(@NonNull Context context) {
        if (!areAllowed(context)) {
            return;
        }
        ensureChannels(context);
        String[] messages = context.getResources().getStringArray(R.array.reminder_messages);
        int index = ReminderPlanner.messageIndex(LocalDate.now().toEpochDay(), messages.length);

        Intent open = new Intent(context, ArenaActivity.class)
                .putExtra(ArenaActivity.EXTRA_FROM_REMINDER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_tab_arena)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(messages[index])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messages[index]))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(ID_REMINDER, builder.build());
    }

    private static final int ID_DAILY = 1002;

    /**
     * MS17: "today's puzzle is ready". Shown by the daily reminder worker when the player has not played the puzzle of the day.
     * Tapping it opens the daily puzzle screen.
     */
    @SuppressWarnings("MissingPermission")   // checked through areAllowed()
    public static void showDailyNudge(@NonNull Context context) {
        if (!areAllowed(context)) {
            return;
        }
        ensureChannels(context);
        Intent open = com.graphlab.ui.arena.DailyPuzzleActivity.intent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_tab_arena)
                .setContentTitle(context.getString(R.string.daily_nudge_title))
                .setContentText(context.getString(R.string.daily_nudge_body))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.daily_nudge_body)))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(ID_DAILY, builder.build());
    }
}

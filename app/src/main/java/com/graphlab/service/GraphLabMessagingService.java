package com.graphlab.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.graphlab.R;
import com.graphlab.data.cloud.CloudConfig;
import com.graphlab.ui.arena.ArenaActivity;
import com.graphlab.work.ContentScheduler;
import com.graphlab.work.Notifications;
import java.util.Map;

/**
 * Receives Firebase Cloud Messaging messages (MS7). Every install joins the {@link #TOPIC} topic, so one message
 * from the Firebase console reaches everybody.
 *
 * <p>A message may carry data keys:
 * <ul>
 *   <li>{@code content = update}: fetch the hosted question and level files now.</li>
 *   <li>{@code title} and {@code body}: show a notification (only when notifications are allowed).</li>
 * </ul>
 * Nothing in a message is trusted beyond that: no link is opened, and the text is shown as plain text.
 * (While the app is in the background, a message that has a "notification" part is shown by the system instead.)
 */
public class GraphLabMessagingService extends FirebaseMessagingService {
    /** The topic every install subscribes to. */
    public static final String TOPIC = "graphlab-news";
    static final String KEY_CONTENT = "content";
    static final String VALUE_UPDATE = "update";
    private static final int ID_NEWS = 1003;
    private static final int MAX_TITLE = 60;
    private static final int MAX_BODY = 240;

    /** Joins the topic. Safe to call at every start; does nothing when this build has no messaging set-up. */
    public static void subscribe() {
        if (!CloudConfig.isMessagingConfigured()) {
            return;
        }
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC);
        } catch (RuntimeException notReady) {
            // Firebase did not start; the next app start tries again
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // A new token needs no action of ours: topic membership is re-established from the app start.
        subscribe();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if (VALUE_UPDATE.equals(data.get(KEY_CONTENT))) {
            ContentScheduler.refreshNow(this);
        }
        String title = data.get("title");
        String body = data.get("body");
        if (message.getNotification() != null) {
            title = title != null ? title : message.getNotification().getTitle();
            body = body != null ? body : message.getNotification().getBody();
        }
        if (title != null || body != null) {
            show(this, clip(title == null ? getString(R.string.app_name) : title, MAX_TITLE), clip(body == null ? "" : body, MAX_BODY));
        }
    }

    static String clip(String text, int max) {
        String clean = text.replaceAll("\\p{Cntrl}", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    @SuppressWarnings("MissingPermission")   // areAllowed() covers Android 13's runtime permission
    private static void show(Context context, String title, String body) {
        if (!Notifications.areAllowed(context)) {
            return;
        }
        Notifications.ensureChannels(context);
        Intent open = new Intent(context, ArenaActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifications.CHANNEL_NEWS)
                .setSmallIcon(R.drawable.ic_tab_arena)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(ID_NEWS, builder.build());
    }
}

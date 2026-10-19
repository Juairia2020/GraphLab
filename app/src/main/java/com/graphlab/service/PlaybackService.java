package com.graphlab.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.graphlab.R;
import com.graphlab.engine.play.Playback;
import com.graphlab.ui.lab.LabActivity;
import com.graphlab.work.Notifications;
import java.util.function.IntFunction;

/**
 * Keeps an algorithm playing after the person leaves the app, with the controls of a video player in the notification:
 * previous step, play or pause, next step, close, and a seek bar (a {@link MediaSession}). The Run screen hands its
 * {@link Playback} over when it goes to the background while playing ({@link #handOff}) and takes it back when it returns
 * ({@link #takeBack}); both sides share the same Playback object, so the step and the speed carry over.
 *
 * <p>It is a foreground service of type mediaPlayback, started while the app is still on screen. If notifications are not
 * allowed the hand-over is refused and the Run screen pauses as before. A paused playback is stopped after
 * {@link #IDLE_STOP_MILLIS} so the notification does not stay for ever.
 */
public class PlaybackService extends Service {
    public static final String ACTION_START = "com.graphlab.action.PLAYBACK_START";
    public static final String ACTION_TOGGLE = "com.graphlab.action.PLAYBACK_TOGGLE";
    public static final String ACTION_NEXT = "com.graphlab.action.PLAYBACK_NEXT";
    public static final String ACTION_PREVIOUS = "com.graphlab.action.PLAYBACK_PREVIOUS";
    public static final String ACTION_CLOSE = "com.graphlab.action.PLAYBACK_CLOSE";

    private static final int NOTIFICATION_ID = 2003;
    private static final long IDLE_STOP_MILLIS = 10 * 60 * 1000L;
    private static final long NOTIFY_EVERY_MILLIS = 500;
    /** One step is this many "milliseconds" on the seek bar, so the bar moves at the real speed of the playback. */
    private static final long BAR_UNIT = Playback.BASE_DELAY_MILLIS;

    /** What the Run screen gives the service. */
    private static final class Source {
        final Playback playback;
        final String title;
        final IntFunction<String> caption;

        Source(Playback playback, String title, IntFunction<String> caption) {
            this.playback = playback;
            this.title = title;
            this.caption = caption;
        }
    }

    @Nullable
    private static volatile Source pending;
    @Nullable
    private static volatile PlaybackService live;

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Source source;
    @Nullable
    private MediaSession session;
    private long lastNotifyAt;
    private boolean foreground;

    // ---- how the Run screen uses it -----------------------------------------------------

    /**
     * Hands a playing run to the service. Call while the app is still visible (onPause or onStop of the Run screen).
     *
     * @return false when it could not start (notifications are off, or Android refused); the caller then pauses
     */
    public static boolean handOff(@NonNull Context context, @NonNull Playback playback, @NonNull String title,
                                  @NonNull IntFunction<String> caption) {
        Context app = context.getApplicationContext();
        if (!Notifications.areAllowed(app)) {
            return false;
        }
        PlaybackService existing = live;
        if (existing != null && existing.source != null && existing.source.playback == playback) {
            return true;   // already in the background (handed over before and not taken back)
        }
        pending = new Source(playback, title, caption);
        try {
            ContextCompat.startForegroundService(app, new Intent(app, PlaybackService.class).setAction(ACTION_START));
            return true;
        } catch (RuntimeException refused) {   // for example ForegroundServiceStartNotAllowedException
            pending = null;
            return false;
        }
    }

    /** The Run screen is back: the service lets go of this playback at once and stops. */
    public static void takeBack(@NonNull Context context, @NonNull Playback playback) {
        PlaybackService service = live;
        if (service != null && service.source != null && service.source.playback == playback) {
            service.shutdown(false);
        } else if (pending != null && pending.playback == playback) {
            pending = null;
        }
    }

    // ---- lifecycle ----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
        Notifications.ensureChannels(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            Source next = pending;
            pending = null;
            if (next != null) {
                source = next;
            }
            if (source == null) {
                // Started with nothing to play (the hand-over was cancelled): still go to the foreground, then stop.
                ServiceCompat.startForeground(this, NOTIFICATION_ID, bareNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                foreground = true;
                shutdown();
                return START_NOT_STICKY;
            }
            begin();
        } else if (source != null && ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (source != null && ACTION_NEXT.equals(action)) {
            step(true);
        } else if (source != null && ACTION_PREVIOUS.equals(action)) {
            step(false);
        } else if (ACTION_CLOSE.equals(action) || source == null) {
            shutdown();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (live == this) {
            live = null;
        }
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        shutdown();
    }

    // ---- playing ------------------------------------------------------------------------

    private void begin() {
        session = new MediaSession(this, "GraphLabPlayback");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                setPlaying(true);
            }

            @Override
            public void onPause() {
                setPlaying(false);
            }

            @Override
            public void onSkipToNext() {
                step(true);
            }

            @Override
            public void onSkipToPrevious() {
                step(false);
            }

            @Override
            public void onSeekTo(long positionMillis) {
                if (source != null) {
                    source.playback.seek((int) Math.round((double) positionMillis / BAR_UNIT));
                    handler.removeCallbacks(tick);
                    publish(true);
                    scheduleIdleStop();
                }
            }

            @Override
            public void onStop() {
                shutdown();
            }
        }, handler);
        session.setActive(true);
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        foreground = true;
        publish(true);
        // The Run screen's own clock is stopped by the caller; this one carries on from the same step.
        handler.removeCallbacks(tick);
        if (source.playback.isPlaying()) {
            handler.postDelayed(tick, source.playback.delayMillis());
        } else {
            scheduleIdleStop();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Source s = source;
            if (s == null) {
                return;
            }
            boolean again = s.playback.tick();
            publish(!again);
            if (again) {
                handler.postDelayed(this, s.playback.delayMillis());
            } else {
                scheduleIdleStop();   // finished
            }
        }
    };

    private void toggle() {
        Source s = source;
        if (s == null) {
            return;
        }
        setPlaying(!s.playback.isPlaying());
    }

    private void setPlaying(boolean play) {
        Source s = source;
        if (s == null || s.playback.isPlaying() == play) {
            return;
        }
        handler.removeCallbacks(tick);
        handler.removeCallbacks(idleStop);
        if (play) {
            s.playback.togglePlay();   // from the last step this starts again from step 0, like a video
            handler.postDelayed(tick, s.playback.delayMillis());
        } else {
            s.playback.pause();
            scheduleIdleStop();
        }
        publish(true);
    }

    private void step(boolean forward) {
        Source s = source;
        if (s == null) {
            return;
        }
        handler.removeCallbacks(tick);
        if (forward) {
            s.playback.next();   // a manual move pauses, as in the app
        } else {
            s.playback.previous();
        }
        publish(true);
        scheduleIdleStop();
    }

    private final Runnable idleStop = this::shutdown;

    private void scheduleIdleStop() {
        handler.removeCallbacks(idleStop);
        handler.postDelayed(idleStop, IDLE_STOP_MILLIS);
    }

    private void shutdown() {
        shutdown(true);
    }

    /**
     * Lets go of the playback and ends the service. Safe to call more than once.
     *
     * @param pause true when the person closed it (or it timed out): the run is left paused. False when the Run screen takes
     *              it back: it carries on exactly as it was.
     */
    private void shutdown(boolean pause) {
        handler.removeCallbacksAndMessages(null);
        if (pause && source != null) {
            source.playback.pause();
        }
        source = null;
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        stopSelf();
    }

    // ---- what the notification and the media session show ------------------------------

    private void publish(boolean force) {
        Source s = source;
        if (s == null || session == null) {
            return;
        }
        Playback p = s.playback;
        long duration = Math.max(1, p.size() - 1) * BAR_UNIT;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, caption(s))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(p.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        p.index() * BAR_UNIT, p.isPlaying() ? (float) p.speed() : 0f, SystemClock.elapsedRealtime())
                .build());
        long now = SystemClock.elapsedRealtime();
        if (force || now - lastNotifyAt >= NOTIFY_EVERY_MILLIS) {
            lastNotifyAt = now;
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification());
            }
        }
    }

    private String caption(Source s) {
        int index = s.playback.index();
        String text = s.caption.apply(index);
        return getString(R.string.playback_notification_step, index, Math.max(0, s.playback.size() - 1), text);
    }

    private Notification bareNotification() {
        return new Notification.Builder(this, Notifications.CHANNEL_PLAYBACK)
                .setSmallIcon(R.drawable.ic_tab_lab)
                .setContentTitle(getString(R.string.app_name))
                .build();
    }

    @SuppressWarnings("deprecation")
    private Notification notification() {
        Source s = source;
        if (s == null) {
            return bareNotification();
        }
        boolean playing = s.playback.isPlaying();
        Notification.Builder builder = new Notification.Builder(this, Notifications.CHANNEL_PLAYBACK)
                .setSmallIcon(R.drawable.ic_tab_lab)
                .setContentTitle(s.title)
                .setContentText(caption(s))
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, LabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(action(R.drawable.ic_prev, R.string.playback_prev, ACTION_PREVIOUS))
                .addAction(action(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? R.string.playback_pause : R.string.playback_play, ACTION_TOGGLE))
                .addAction(action(R.drawable.ic_next, R.string.playback_next, ACTION_NEXT))
                .addAction(action(R.drawable.ic_more, R.string.playback_close, ACTION_CLOSE));
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (session != null) {
            style.setMediaSession(session.getSessionToken());
        }
        return builder.setStyle(style).build();
    }

    private Notification.Action action(int icon, int label, String action) {
        PendingIntent intent = PendingIntent.getService(this, action.hashCode(),
                new Intent(this, PlaybackService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), intent).build();
    }
}

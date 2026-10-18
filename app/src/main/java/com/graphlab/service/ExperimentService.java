package com.graphlab.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.db.GraphRecord;
import com.graphlab.engine.experiment.ExperimentRecord;
import com.graphlab.engine.experiment.ExperimentRunner;
import com.graphlab.engine.experiment.Histogram;
import com.graphlab.engine.experiment.ExperimentAlgorithm;
import com.graphlab.engine.experiment.Trials;
import com.graphlab.engine.experiment.PauseGate;
import com.graphlab.engine.experiment.TrialCount;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.provider.GraphContract;
import com.graphlab.receiver.BatteryStateReceiver;
import com.graphlab.receiver.RunFinishedReceiver;
import com.graphlab.ui.lab.LabActivity;
import com.graphlab.work.Notifications;

/**
 * Runs a long Karger experiment as a foreground service (MS8, checklist item 8). A foreground service is the
 * way to keep working after the user leaves the app: it shows an ongoing notification, and Android keeps the process
 * alive while it runs.
 *
 * <p><b>Rules it follows.</b> {@code startForeground} is called first thing in {@code onStartCommand}, before any work, because
 * Android allows only a few seconds between {@code startForegroundService} and the notification. The manifest
 * declares the type {@code dataSync} and the matching permissions (required from Android 14). Android 15 caps a
 * dataSync service at a few hours in total: {@link #onTimeout} then ends the run and keeps the trials finished so far.
 *
 * <p><b>What it does.</b> One worker thread loads the graph, runs the trials with {@link ExperimentRunner}, updates the
 * notification every {@link TrialCount#REPORT_EVERY} trials and publishes progress to {@link ExperimentMonitor}.
 * A runtime {@link BatteryStateReceiver} pauses the run on low battery and resumes it when the battery is okay
 * again (unless the user paused it). At the end the result is saved with its histogram, the ContentProvider's
 * observers are notified and {@link RunFinishedReceiver#ACTION_RUN_FINISHED} is broadcast inside the app.
 * If the process is killed the experiment is lost (START_NOT_STICKY), nothing half-saved is left behind.
 */
public class ExperimentService extends Service {
    public static final String ACTION_START = "com.graphlab.action.EXPERIMENT_START";
    public static final String ACTION_PAUSE = "com.graphlab.action.EXPERIMENT_PAUSE";
    public static final String ACTION_RESUME = "com.graphlab.action.EXPERIMENT_RESUME";
    public static final String ACTION_CANCEL = "com.graphlab.action.EXPERIMENT_CANCEL";
    public static final String EXTRA_ALGORITHM = "algorithm";
    public static final String EXTRA_GRAPH_ID = "graph_id";
    public static final String EXTRA_TRIALS = "trials";
    public static final String EXTRA_SEED = "seed";
    public static final String EXTRA_PACE_MS = "pace_ms";

    private static final int NOTIFICATION_ID = 2001;

    private AppContainer container;
    private ExperimentMonitor monitor;
    private PauseGate gate;
    private BatteryStateReceiver batteryReceiver;
    private volatile boolean running;
    private volatile boolean timedOut;
    private volatile String graphName = "";
    private volatile int done;
    private volatile int total;
    private volatile Histogram latest = new Histogram();

    // ---- how the screens start and steer it ---------------------------------------------

    /** Starts an experiment. Call only while the app is on screen (a background app may not start a foreground service). */
    public static void start(@NonNull Context context, @NonNull String algorithmId, long graphId, int trials, long seed, long paceMillis) {
        Intent intent = new Intent(context, ExperimentService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALGORITHM, algorithmId)
                .putExtra(EXTRA_GRAPH_ID, graphId)
                .putExtra(EXTRA_TRIALS, trials)
                .putExtra(EXTRA_SEED, seed)
                .putExtra(EXTRA_PACE_MS, paceMillis);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void pause(@NonNull Context context) {
        context.startService(new Intent(context, ExperimentService.class).setAction(ACTION_PAUSE));
    }

    public static void resume(@NonNull Context context) {
        context.startService(new Intent(context, ExperimentService.class).setAction(ACTION_RESUME));
    }

    public static void cancel(@NonNull Context context) {
        context.startService(new Intent(context, ExperimentService.class).setAction(ACTION_CANCEL));
    }

    // ---- lifecycle ----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        container = GraphLabApp.container(this);
        monitor = container.monitor();
        Notifications.ensureChannels(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;   // started, not bound: screens talk to it with intents and listen through ExperimentMonitor
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            // First thing: go to the foreground, so Android's few-second limit is met whatever happens next.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progressNotification(true),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            if (!running) {
                begin(intent);
            }
        } else if (ACTION_PAUSE.equals(action) && running) {
            gate.pause(PauseGate.Reason.USER);
            pushState();
        } else if (ACTION_RESUME.equals(action) && running) {
            gate.resume(PauseGate.Reason.USER);
            pushState();
        } else if (ACTION_CANCEL.equals(action) && running) {
            gate.cancel();
        } else if (!running) {
            stopSelf(startId);   // a stale notification button, or the system restarting us with no work to do
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15 limits dataSync services. End the run now, keeping the finished trials.
        timedOut = true;
        if (gate != null) {
            gate.cancel();
        }
    }

    @Override
    public void onDestroy() {
        if (batteryReceiver != null) {
            batteryReceiver.unregister(this);
            batteryReceiver = null;
        }
        super.onDestroy();
    }

    // ---- the run ------------------------------------------------------------------------

    private void begin(@Nullable Intent intent) {
        final String algorithmId = intent == null ? "karger" : intent.getStringExtra(EXTRA_ALGORITHM);
        final long graphId = intent == null ? 0 : intent.getLongExtra(EXTRA_GRAPH_ID, 0);
        final int trials = intent == null ? 0 : intent.getIntExtra(EXTRA_TRIALS, 0);
        final long seed = intent == null ? 0 : intent.getLongExtra(EXTRA_SEED, 0);
        final long pace = intent == null ? 0 : intent.getLongExtra(EXTRA_PACE_MS, 0);
        running = true;
        timedOut = false;
        gate = new PauseGate();
        done = 0;
        total = trials;
        latest = new Histogram();
        graphName = "";

        batteryReceiver = new BatteryStateReceiver(new BatteryStateReceiver.Listener() {
            @Override
            public void onBatteryLow() {
                if (running && !gate.isPaused()) {
                    gate.pause(PauseGate.Reason.BATTERY);
                    pushState();
                }
            }

            @Override
            public void onBatteryOkay() {
                if (running && gate.resume(PauseGate.Reason.BATTERY)) {
                    pushState();
                }
            }
        });
        batteryReceiver.register(this);

        Thread worker = new Thread(() -> work(algorithmId, graphId, trials, seed, pace), "graphlab-experiment");
        worker.start();
    }

    private void work(String algorithmId, long graphId, int trials, long seed, long pace) {
        long lastRunId = monitor.current().lastRunId;
        try {
            if (trials < TrialCount.MIN || trials > TrialCount.MAX) {
                throw new IllegalArgumentException(getString(R.string.experiment_error_trials));
            }
            final ExperimentAlgorithm algorithm = ExperimentAlgorithm.byId(algorithmId);
            final long savedGraphId = algorithm.needsGraph() ? graphId : 0;
            Trials engine;
            if (algorithm.needsGraph()) {
                GraphRecord record = container.executors().diskFuture(() -> container.repository().getGraph(graphId)).get();
                if (record == null) {
                    throw new IllegalArgumentException(getString(R.string.experiment_error_missing_graph));
                }
                graphName = record.name;
                try {
                    engine = algorithm.create(GraphJson.read(record.json).graph());
                } catch (IllegalArgumentException notSuitable) {
                    throw new IllegalArgumentException(getString(R.string.experiment_error_graph, notSuitable.getMessage()));
                }
            } else {
                graphName = algorithm.title();
                engine = algorithm.create(null);
            }
            pushState();

            ExperimentRunner.Outcome outcome = ExperimentRunner.run(engine, seed, trials, gate, TrialCount.REPORT_EVERY, pace,
                    (doneNow, totalNow, histogram) -> {
                        done = doneNow;
                        latest = histogram;
                        pushState();
                    });

            if (outcome.cancelled() && !timedOut || outcome.done() == 0) {
                // The user cancelled: nothing is saved.
                monitor.publish(monitor.idleAfter(lastRunId, null));
                finishService(null);
                return;
            }
            ExperimentRecord result = new ExperimentRecord(algorithm.id(), trials, outcome.done(), seed, outcome.histogram());
            final String summary = result.summary();
            final String detail = result.toJson();
            long runId = container.executors().diskFuture(() -> container.repository()
                    .addExperimentRun(savedGraphId, algorithm.id(), result.done(), summary, detail, System.currentTimeMillis())).get();
            getContentResolver().notifyChange(GraphContract.RUNS_URI, null);   // lets open Compare screens reload
            monitor.publish(monitor.idleAfter(runId, null));
            sendBroadcast(RunFinishedReceiver.buildBroadcast(this, runId, graphName, summary, result.partial()));
            finishService(finishedNotification(summary, result.partial()));
        } catch (Exception e) {
            String message = e.getMessage() == null ? getString(R.string.experiment_error_generic) : e.getMessage();
            if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null && e.getCause().getMessage() != null) {
                message = e.getCause().getMessage();
            }
            monitor.publish(monitor.idleAfter(lastRunId, message));
            finishService(null);
        } finally {
            running = false;
        }
    }

    /** Leaves the foreground. With a final notification it stays (no longer ongoing); without, the notification is removed. */
    private void finishService(@Nullable Notification finalNotification) {
        if (finalNotification != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH);
            notifyQuietly(finalNotification);
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
        stopSelf();
    }

    // ---- publishing state ---------------------------------------------------------------

    /** Sends the current progress to the screens and to the notification. */
    private void pushState() {
        PauseGate g = gate;
        boolean paused = g != null && g.isPaused();
        boolean byBattery = paused && g.reason() == PauseGate.Reason.BATTERY;
        monitor.publish(new ExperimentMonitor.Snapshot(paused ? ExperimentMonitor.State.PAUSED : ExperimentMonitor.State.RUNNING,
                graphName, done, total, latest, byBattery, monitor.current().lastRunId, null));
        notifyQuietly(progressNotification(false));
    }

    @SuppressWarnings("MissingPermission")   // checked through Notifications.areAllowed(), which covers Android 13's runtime permission
    private void notifyQuietly(@NonNull Notification notification) {
        if (!Notifications.areAllowed(this)) {
            return;   // the service still runs; Android shows it in the "active apps" list instead
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException permissionRemoved) {
            // Notifications were switched off a moment ago: ignore.
        }
    }

    // ---- notifications ------------------------------------------------------------------

    private Notification progressNotification(boolean starting) {
        PauseGate g = gate;
        boolean paused = !starting && g != null && g.isPaused();
        boolean byBattery = paused && g.reason() == PauseGate.Reason.BATTERY;
        String text;
        if (starting && total == 0) {
            text = getString(R.string.experiment_notification_starting);
        } else if (byBattery) {
            text = getString(R.string.experiment_notification_battery, done, total);
        } else if (paused) {
            text = getString(R.string.experiment_notification_paused, done, total);
        } else {
            text = getString(R.string.experiment_notification_progress, done, total);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notifications.CHANNEL_EXPERIMENTS)
                .setSmallIcon(R.drawable.ic_tab_lab)
                .setContentTitle(graphName.isEmpty() ? getString(R.string.experiment_notification_title)
                        : getString(R.string.experiment_notification_title_graph, graphName))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openLab())
                .setProgress(Math.max(total, 1), Math.min(done, Math.max(total, 1)), starting && total == 0);
        if (paused) {
            builder.addAction(0, getString(R.string.experiment_resume), actionIntent(ACTION_RESUME, 1));
        } else {
            builder.addAction(0, getString(R.string.experiment_pause), actionIntent(ACTION_PAUSE, 2));
        }
        builder.addAction(0, getString(R.string.experiment_cancel), actionIntent(ACTION_CANCEL, 3));
        return builder.build();
    }

    private Notification finishedNotification(String summary, boolean partial) {
        return new NotificationCompat.Builder(this, Notifications.CHANNEL_EXPERIMENTS)
                .setSmallIcon(R.drawable.ic_tab_lab)
                .setContentTitle(getString(partial ? R.string.experiment_notification_stopped : R.string.experiment_notification_done))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(openLab())
                .build();
    }

    private PendingIntent openLab() {
        Intent open = new Intent(this, LabActivity.class)
                .putExtra(LabActivity.EXTRA_OPEN_EXPERIMENT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, ExperimentService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}

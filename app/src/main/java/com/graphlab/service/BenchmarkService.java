package com.graphlab.service;

import android.app.Notification;
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
import com.graphlab.engine.bench.Benchmark;
import com.graphlab.work.Notifications;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the growth benchmark as a foreground service (MS15, G3), like the Karger experiment: timing is long and must not be
 * stopped when the app goes to the background. The same rules apply: {@code startForeground} first, type dataSync, and
 * {@link #onTimeout} (Android 15) ends the run and keeps the sizes measured so far. The finished points are saved in the
 * progress table under "benchmark" so the chart is still there next time.
 */
public class BenchmarkService extends Service {
    public static final String ACTION_START = "com.graphlab.action.BENCHMARK_START";
    public static final String ACTION_STOP = "com.graphlab.action.BENCHMARK_STOP";
    public static final String PROGRESS_KEY = "benchmark";
    private static final int NOTIFICATION_ID = 2002;
    private static final int FIRST_SIZE = 10_000;
    private static final int SIZES = 7;          // 10 000 ... 640 000 nodes
    private static final int WARMUPS = 2;
    private static final int REPEATS = 5;

    private volatile boolean running;
    private volatile boolean stop;

    public static void start(@NonNull Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, BenchmarkService.class).setAction(ACTION_START));
    }

    public static void stop(@NonNull Context context) {
        context.startService(new Intent(context, BenchmarkService.class).setAction(ACTION_STOP));
    }

    /** "n:ms,n:ms" for the progress table. */
    @NonNull
    public static String encode(@NonNull List<Integer> ns, @NonNull List<Double> ms) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ns.size(); i++) {
            out.append(i == 0 ? "" : ",").append(ns.get(i)).append(':').append(ms.get(i));
        }
        return out.toString();
    }

    /** The saved points, or an empty list when the text is missing or damaged. */
    @NonNull
    public static List<List<? extends Number>> decode(@Nullable String text) {
        List<Number> ns = new ArrayList<>();
        List<Number> ms = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            try {
                for (String part : text.split(",")) {
                    String[] pair = part.split(":");
                    ns.add(Integer.parseInt(pair[0]));
                    ms.add(Double.parseDouble(pair[1]));
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException damaged) {
                ns.clear();
                ms.clear();
            }
        }
        List<List<? extends Number>> out = new ArrayList<>();
        out.add(ns);
        out.add(ms);
        return out;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0, SIZES), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            if (!running) {
                begin();
            }
        } else if (ACTION_STOP.equals(action)) {
            stop = true;
        } else if (!running) {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        stop = true;   // Android 15 limits dataSync services: keep what is measured
    }

    private void begin() {
        running = true;
        stop = false;
        final BenchmarkMonitor monitor = BenchmarkMonitor.get();
        final List<Integer> sizes = Benchmark.doubling(FIRST_SIZE, SIZES);
        final List<Integer> ns = new ArrayList<>();
        final List<Double> ms = new ArrayList<>();
        monitor.publish(true, sizes.size(), ns, ms, null);
        new Thread(() -> {
            Benchmark.Result result = null;
            try {
                result = Benchmark.run(sizes, WARMUPS, REPEATS, Benchmark.kruskal(), System::nanoTime, System.nanoTime(), () -> stop,
                        (index, total, n, millis) -> {
                            ns.add(n);
                            ms.add(millis);
                            monitor.publish(true, total, ns, ms, null);
                            notifyQuietly(notification(index + 1, total));
                        });
            } catch (RuntimeException failed) {
                result = null;
            } finally {
                finish(monitor, sizes.size(), ns, ms, result);
            }
        }, "graphlab-benchmark").start();
    }

    private void finish(BenchmarkMonitor monitor, int total, List<Integer> ns, List<Double> ms, @Nullable Benchmark.Result result) {
        final AppContainer container = GraphLabApp.container(this);
        if (ns.size() >= 2) {
            final String saved = encode(ns, ms);
            container.executors().disk(() -> container.repository().putProgressValue(PROGRESS_KEY, saved, System.currentTimeMillis()));
        }
        monitor.publish(false, total, ns, ms, result != null ? result : ns.size() >= 2 ? Benchmark.fit(ns, ms, true) : null);
        running = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @SuppressWarnings("MissingPermission")   // checked through Notifications.areAllowed()
    private void notifyQuietly(@NonNull Notification notification) {
        if (!Notifications.areAllowed(this)) {
            return;
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException removed) {
            // notifications were switched off a moment ago
        }
    }

    private Notification notification(int done, int total) {
        return new NotificationCompat.Builder(this, Notifications.CHANNEL_EXPERIMENTS)
                .setSmallIcon(R.drawable.ic_tab_lab)
                .setContentTitle(getString(R.string.benchmark_notification_title))
                .setContentText(getString(R.string.benchmark_notification_text, Math.min(done + 1, total), total))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setProgress(Math.max(total, 1), Math.min(done, Math.max(total, 1)), false)
                .build();
    }
}

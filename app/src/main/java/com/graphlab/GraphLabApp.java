package com.graphlab;

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import com.graphlab.data.AppContainer;
import com.graphlab.data.cloud.FirebaseInit;
import com.graphlab.data.content.ContentSync;
import com.graphlab.data.prefs.ThemePrefs;
import com.graphlab.service.GraphLabMessagingService;
import com.graphlab.work.ContentScheduler;
import com.graphlab.work.ReminderScheduler;

public class GraphLabApp extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved System / Light / Dark choice before any Activity is created.
        AppCompatDelegate.setDefaultNightMode(ThemePrefs.load(this).nightMode);

        container = new AppContainer(this);
        registerActivityLifecycleCallbacks(container.lifecycleLogger());
        // A graph that was swiped away just before the app closed is gone for good now.
        container.executors().disk(() -> container.repository().purgeAllDeleted());

        // MS7. Neither of these may stop the app from starting: the cloud is optional, and a reminder is a convenience.
        try {
            FirebaseInit.init(this);
        } catch (RuntimeException ignored) {
            // The cloud screens will report that it is not available.
        }
        com.graphlab.data.Telemetry.apply(this);   // MS18: honours the opt-out switch in Me
        try {
            GraphLabMessagingService.subscribe();
        } catch (RuntimeException ignored) {
            // push messages are optional
        }
        // Hosted content: install the saved copy first (no network), then keep the daily refresh planned.
        container.executors().disk(() -> ContentSync.loadCached(this));
        try {
            ContentScheduler.restore(this);
        } catch (RuntimeException ignored) {
            // the bundled content is used until the next start
        }
        try {
            ReminderScheduler.restore(this);
        } catch (RuntimeException ignored) {
            // The reminder simply stays unscheduled until the user touches the setting.
        }
    }

    /** The shared objects (database, executors). */
    @NonNull
    public AppContainer container() {
        return container;
    }

    @NonNull
    public static AppContainer container(@NonNull Context context) {
        return ((GraphLabApp) context.getApplicationContext()).container();
    }
}

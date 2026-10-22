package com.graphlab.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Crash reports and usage statistics, behind one switch the learner controls in Me.
 * Collection is on by default and can be turned off at any time. No graph content or personal data is sent:
 * only event names such as "run_algorithm" with the algorithm id.
 */
public final class Telemetry {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY = "telemetry";

    private Telemetry() {
    }

    public static boolean enabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY, true);
    }

    public static void setEnabled(@NonNull Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY, on).apply();
        apply(context);
    }

    /** Called once at start-up and after the switch changes. Never throws: telemetry must not break the app. */
    public static void apply(@NonNull Context context) {
        try {
            boolean on = enabled(context);
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(on);
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(on);
        } catch (RuntimeException ignored) {
            // Firebase not configured in this build.
        }
    }

    public static void event(@NonNull Context context, @NonNull String name, String key, String value) {
        if (!enabled(context)) {
            return;
        }
        try {
            Bundle b = new Bundle();
            if (key != null) {
                b.putString(key, value);
            }
            FirebaseAnalytics.getInstance(context).logEvent(name, b);
        } catch (RuntimeException ignored) {
            // Firebase not configured in this build.
        }
    }

    /** Records a non-fatal test report so the learner can check the setup. */
    public static boolean sendTestReport(@NonNull Context context) {
        if (!enabled(context)) {
            return false;
        }
        try {
            FirebaseCrashlytics.getInstance().recordException(new RuntimeException("GraphLab test report"));
            FirebaseCrashlytics.getInstance().sendUnsentReports();
        } catch (RuntimeException ignored) {
            // Firebase not configured in this build.
        }
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/** Small flags that are not part of a graph. Shares the preferences file with {@link ThemePrefs}. */
public final class AppPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private AppPrefs() {
    }

    public static boolean isOnboardingDone(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingDone(Context context, boolean done) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.graphlab.engine.remind.ReminderSettings;

/** Saves the daily reminder settings as the text {@link ReminderSettings#encode()} makes. */
public final class ReminderPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY = "reminder";

    private ReminderPrefs() {
    }

    @NonNull
    public static ReminderSettings load(@NonNull Context context) {
        return ReminderSettings.decode(prefs(context).getString(KEY, null));
    }

    public static void save(@NonNull Context context, @NonNull ReminderSettings settings) {
        prefs(context).edit().putString(KEY, settings.encode()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.graphlab.engine.arena.ArenaProgress;

/** Saves the Arena stars and points as one string in the shared preferences file. */
public final class ArenaPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY_PROGRESS = "arena_progress";

    private ArenaPrefs() {
    }

    @NonNull
    public static ArenaProgress load(@NonNull Context context) {
        return ArenaProgress.decode(prefs(context).getString(KEY_PROGRESS, ""));
    }

    public static void save(@NonNull Context context, @NonNull ArenaProgress progress) {
        prefs(context).edit().putString(KEY_PROGRESS, progress.encode()).apply();
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_PROGRESS).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

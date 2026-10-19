package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.graphlab.engine.arena.SurpriseEngine;

/** The four weights of the Surprise Engine (MS12), as tuned on the debug screen. */
public final class SurprisePrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY = "surprise_weights";

    private SurprisePrefs() {
    }

    @NonNull
    public static SurpriseEngine.Weights load(@NonNull Context context) {
        String text = prefs(context).getString(KEY, null);
        return text == null ? SurpriseEngine.Weights.DEFAULT : SurpriseEngine.Weights.parse(text);
    }

    public static void save(@NonNull Context context, @NonNull SurpriseEngine.Weights weights) {
        prefs(context).edit().putString(KEY, weights.encode()).apply();
    }

    public static void reset(@NonNull Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

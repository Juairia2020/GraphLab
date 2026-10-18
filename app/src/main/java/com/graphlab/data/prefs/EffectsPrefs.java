package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.graphlab.engine.effects.EffectsSettings;

/** Stores the glow, sound, haptics and reduce-effects choices (as one string made by the engine). */
public final class EffectsPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY = "effects";

    private EffectsPrefs() {
    }

    @NonNull
    public static EffectsSettings load(@NonNull Context context) {
        return EffectsSettings.decode(prefs(context).getString(KEY, null));
    }

    public static void save(@NonNull Context context, @NonNull EffectsSettings settings) {
        prefs(context).edit().putString(KEY, settings.encode()).apply();
    }

    /** True when the system animation scale is set to off (the usual "remove animations" switch). */
    public static boolean systemReduceMotion(@NonNull Context context) {
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    public static boolean glow(@NonNull Context context) {
        return load(context).glowActive(systemReduceMotion(context));
    }

    public static boolean sound(@NonNull Context context) {
        return load(context).soundActive(systemReduceMotion(context));
    }

    public static boolean haptics(@NonNull Context context) {
        return load(context).hapticsActive(systemReduceMotion(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

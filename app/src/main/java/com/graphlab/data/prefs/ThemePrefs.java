package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

public final class ThemePrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY_THEME = "theme_mode";

    private ThemePrefs() {
    }

    public static ThemeMode load(Context context) {
        return ThemeMode.fromKey(prefs(context).getString(KEY_THEME, null));
    }

    public static void save(Context context, ThemeMode mode) {
        prefs(context).edit().putString(KEY_THEME, mode.key).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

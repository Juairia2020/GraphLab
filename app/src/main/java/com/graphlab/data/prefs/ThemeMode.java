package com.graphlab.data.prefs;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

/** The three choices in Me > Appearance. {@code key} is what gets stored, so never rename it. */
public enum ThemeMode {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    public static final ThemeMode DEFAULT = SYSTEM;

    public final String key;
    public final int nightMode;

    ThemeMode(String key, int nightMode) {
        this.key = key;
        this.nightMode = nightMode;
    }

    /** Unknown, empty or missing keys fall back to {@link #DEFAULT}. */
    public static ThemeMode fromKey(@Nullable String key) {
        for (ThemeMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return DEFAULT;
    }
}

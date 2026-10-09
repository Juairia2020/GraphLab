package com.graphlab;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.graphlab.data.prefs.ThemePrefs;

public class GraphLabApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved System / Light / Dark choice before any Activity is created.
        AppCompatDelegate.setDefaultNightMode(ThemePrefs.load(this).nightMode);
    }
}

package com.graphlab.ui.me;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import com.graphlab.BuildConfig;
import com.graphlab.R;
import com.graphlab.data.prefs.ThemeMode;
import com.graphlab.data.prefs.ThemePrefs;
import com.graphlab.databinding.ContentMeBinding;
import com.graphlab.engine.EngineInfo;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.Tab;

public class MeActivity extends BaseActivity {

    @Override
    protected Tab getTab() {
        return Tab.ME;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.content_me;
    }

    @Override
    protected void onContentInflated(View content, @Nullable Bundle savedInstanceState) {
        ContentMeBinding binding = ContentMeBinding.bind(content);

        // Show the saved choice first, then attach the listener, so this does not fire it.
        binding.themeToggle.check(buttonIdFor(ThemePrefs.load(this)));
        binding.themeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            ThemeMode mode = modeFor(checkedId);
            if (mode != ThemePrefs.load(this)) {
                ThemePrefs.save(this, mode);
                // AppCompat recreates every open Activity, so all four tabs recolour
                // without restarting the app.
                AppCompatDelegate.setDefaultNightMode(mode.nightMode);
            }
        });

        binding.versionValue.setText(BuildConfig.VERSION_NAME);
        binding.engineValue.setText(EngineInfo.VERSION);
    }

    private static int buttonIdFor(ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return R.id.theme_light;
            case DARK:
                return R.id.theme_dark;
            default:
                return R.id.theme_system;
        }
    }

    private static ThemeMode modeFor(int buttonId) {
        if (buttonId == R.id.theme_light) {
            return ThemeMode.LIGHT;
        }
        if (buttonId == R.id.theme_dark) {
            return ThemeMode.DARK;
        }
        return ThemeMode.SYSTEM;
    }
}

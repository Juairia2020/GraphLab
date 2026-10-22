package com.graphlab.ui.me;

import com.graphlab.ui.common.OverflowMenu;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import com.graphlab.BuildConfig;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.cloud.CloudConfig;
import com.graphlab.data.Telemetry;
import com.graphlab.data.prefs.ArenaPrefs;
import com.graphlab.data.prefs.EffectsPrefs;
import com.graphlab.engine.effects.EffectsSettings;
import com.graphlab.data.prefs.CloudPrefs;
import com.graphlab.data.prefs.ThemeMode;
import com.graphlab.data.prefs.ThemePrefs;
import com.graphlab.databinding.ContentMeBinding;
import com.graphlab.engine.EngineInfo;
import com.graphlab.engine.arena.ArenaProgress;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.Tab;
import com.graphlab.ui.onboarding.OnboardingActivity;
import com.graphlab.ui.online.GalleryActivity;
import com.graphlab.engine.net.GalleryPost;
import androidx.annotation.NonNull;

public class MeActivity extends BaseActivity {
    private ReminderController reminders;
    private ContentMeBinding meBinding;

    /** Asks the system for the notification permission. Registered here, before the Activity starts, as the API requires. */
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (reminders != null) {
                    reminders.onPermissionResult(granted);
                }
            });

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
        meBinding = binding;
        OverflowMenu.attach(binding.meMore, binding.meLifecycleLog, binding.meIntro, binding.meArenaReset, binding.meTestCrash);
        reminders = new ReminderController(this, binding, notificationPermission);
        binding.meCloudOpen.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        binding.meCloudName.setOnClickListener(v -> editDisplayName());

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

        bindEffects(binding);

        binding.versionValue.setText(BuildConfig.VERSION_NAME);
        binding.engineValue.setText(EngineInfo.VERSION);

        // Explicit intents: this tab opens two ordinary screens of the same app.
        binding.meLifecycleLog.setOnClickListener(v -> startActivity(new Intent(this, LifecycleLogActivity.class)));
        binding.meIntro.setOnClickListener(v -> startActivity(new Intent(this, OnboardingActivity.class)));
        binding.meArenaReset.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.me_arena_reset_title)
                .setMessage(R.string.me_arena_reset_body)
                .setNegativeButton(R.string.me_arena_reset_cancel, null)
                .setPositiveButton(R.string.me_arena_reset_confirm, (dialog, which) -> {
                    ArenaPrefs.clear(this);
                    showArenaSummary();
                    Snackbar.make(binding.getRoot(), R.string.me_arena_reset_done, Snackbar.LENGTH_SHORT).show();
                })
                .show());
    }

    /** Shows the saved choices first, then attaches the listeners, so setting them does not fire them. */
    private void bindEffects(ContentMeBinding b) {
        EffectsSettings s = EffectsPrefs.load(this);
        b.fxGlow.setChecked(s.glow());
        b.fxSound.setChecked(s.sound());
        b.fxHaptics.setChecked(s.haptics());
        b.fxReduce.setChecked(s.reduceEffects());
        b.fxTelemetry.setChecked(Telemetry.enabled(this));
        b.fxGlow.setOnCheckedChangeListener((v, on) -> EffectsPrefs.save(this, EffectsPrefs.load(this).withGlow(on)));
        b.fxSound.setOnCheckedChangeListener((v, on) -> EffectsPrefs.save(this, EffectsPrefs.load(this).withSound(on)));
        b.fxHaptics.setOnCheckedChangeListener((v, on) -> EffectsPrefs.save(this, EffectsPrefs.load(this).withHaptics(on)));
        b.fxReduce.setOnCheckedChangeListener((v, on) -> EffectsPrefs.save(this, EffectsPrefs.load(this).withReduceEffects(on)));
        b.fxTelemetry.setOnCheckedChangeListener((v, on) -> Telemetry.setEnabled(this, on));
        b.meTestCrash.setOnClickListener(v -> Snackbar.make(b.getRoot(),
                Telemetry.sendTestReport(this) ? R.string.me_test_crash_sent : R.string.me_test_crash_off,
                Snackbar.LENGTH_LONG).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showDataSummary();
        showArenaSummary();
        if (reminders != null) {
            reminders.render();   // the user may have changed the notification settings meanwhile
        }
        showCloudSummary();
    }

    /** "Cloud sharing is ready." or the reason it is not, and the name shown beside shared graphs. */
    private void showCloudSummary() {
        if (meBinding == null) {
            return;
        }
        meBinding.meCloudStatus.setText(CloudConfig.isConfigured() ? R.string.me_cloud_ready : R.string.me_cloud_not_set_up);
        meBinding.meCloudName.setText(getString(R.string.me_cloud_name, CloudPrefs.displayName(this)));
    }

    private void editDisplayName() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(CloudPrefs.displayName(this));
        input.setSelection(input.getText().length());
        input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(GalleryPost.MAX_AUTHOR)});
        FrameLayout holder = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.me_cloud_name_title)
                .setView(holder)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.me_cloud_name_save, (dialog, which) -> {
                    CloudPrefs.setDisplayName(this, input.getText().toString());
                    showCloudSummary();
                })
                .show();
    }

    /** "Arena: 12 stars · 340 points", read from the preferences. */
    private void showArenaSummary() {
        TextView summary = findViewById(R.id.me_arena_summary);
        if (summary != null) {
            ArenaProgress progress = ArenaPrefs.load(this);
            summary.setText(getString(R.string.me_arena_summary,
                    getResources().getQuantityString(R.plurals.arena_stars_count, progress.totalStars(), progress.totalStars()),
                    getResources().getQuantityString(R.plurals.arena_points_count, progress.totalPoints(), progress.totalPoints())));
        }
    }

    /** "3 saved graphs · 12 runs", read from the database off the main thread. */
    private void showDataSummary() {
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> new int[] {container.repository().graphCount(), container.repository().runCount()},
                new AppExecutors.Callback<int[]>() {
                    @Override
                    public void onSuccess(int[] counts) {
                        TextView summary = findViewById(R.id.me_data_summary);
                        if (summary != null && !isDestroyed()) {
                            summary.setText(getString(R.string.me_data_summary,
                                    getResources().getQuantityString(R.plurals.graphs_count, counts[0], counts[0]),
                                    getResources().getQuantityString(R.plurals.runs_count, counts[1], counts[1])));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                    }
                });
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

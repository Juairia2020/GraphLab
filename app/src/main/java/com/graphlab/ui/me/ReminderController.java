package com.graphlab.ui.me;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.data.prefs.ReminderPrefs;
import com.graphlab.databinding.ContentMeBinding;
import com.graphlab.engine.remind.ReminderSettings;
import com.graphlab.work.Notifications;
import com.graphlab.work.ReminderScheduler;
import java.util.Calendar;

/**
 * The "Daily reminder" section of the Me tab, including the runtime-permission conversation.
 *
 * <p>On Android 13 and newer an app may only show notifications if the user allowed
 * POST_NOTIFICATIONS. The flow, which follows the platform's advice:
 * <ol>
 *   <li>The user switches the reminder on.</li>
 *   <li>Without the permission, a dialog first explains <em>why</em> it is needed. Only after the user
 *       agrees is the system's own permission dialog shown (a rationale before asking).</li>
 *   <li>Allowed: the reminder is scheduled. Denied once: the switch goes back off and a message says
 *       there is no reminder without it. Denied for good (the system will no longer ask): a dialog
 *       offers to open the app's notification settings, since only the user can change that there.</li>
 * </ol>
 * Before Android 13 no permission is needed and the switch simply turns the reminder on.
 */
final class ReminderController {
    private final MeActivity activity;
    private final ContentMeBinding binding;
    private final ActivityResultLauncher<String> permissionLauncher;
    /** True while the code sets the switch itself, so that does not count as the user's tap. */
    private boolean updating;

    ReminderController(@NonNull MeActivity activity, @NonNull ContentMeBinding binding,
                       @NonNull ActivityResultLauncher<String> permissionLauncher) {
        this.activity = activity;
        this.binding = binding;
        this.permissionLauncher = permissionLauncher;
        binding.meReminderSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updating) {
                onToggled(checked);
            }
        });
        binding.meReminderTime.setOnClickListener(v -> pickTime());
        binding.meReminderStatus.setOnClickListener(v -> {
            if (blocked(ReminderPrefs.load(activity))) {
                openNotificationSettings();
            }
        });
        render();
    }

    /** Draws the section from what is saved. Called again when the screen returns (the user may have changed system settings). */
    void render() {
        ReminderSettings settings = ReminderPrefs.load(activity);
        updating = true;
        binding.meReminderSwitch.setChecked(settings.enabled());
        updating = false;
        binding.meReminderTime.setText(activity.getString(R.string.me_reminder_time, timeText(settings)));
        if (!settings.enabled()) {
            binding.meReminderStatus.setText(R.string.me_reminder_status_off);
        } else if (blocked(settings)) {
            binding.meReminderStatus.setText(R.string.me_reminder_status_blocked);
        } else {
            binding.meReminderStatus.setText(activity.getString(R.string.me_reminder_status_on, timeText(settings)));
        }
    }

    // ---- switching on and off ----

    private void onToggled(boolean checked) {
        if (!checked) {
            save(ReminderPrefs.load(activity).withEnabled(false));
            return;
        }
        if (!permissionMissing()) {
            enable();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.me_reminder_why_title)
                .setMessage(R.string.me_reminder_why_body)
                .setNegativeButton(R.string.me_reminder_why_no, (dialog, which) -> render())
                .setPositiveButton(R.string.me_reminder_why_yes,
                        (dialog, which) -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                .setOnCancelListener(dialog -> render())
                .show();
    }

    /** The result of the system's permission dialog. */
    void onPermissionResult(boolean granted) {
        if (granted) {
            enable();
            return;
        }
        render();   // puts the switch back off
        if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // The system will not ask again: only the settings screen can allow it now.
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.me_reminder_blocked_title)
                    .setMessage(R.string.me_reminder_blocked_body)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.me_reminder_open_settings, (dialog, which) -> openNotificationSettings())
                    .show();
        } else {
            Snackbar.make(binding.getRoot(), R.string.me_reminder_denied, Snackbar.LENGTH_LONG).show();
        }
    }

    private void enable() {
        ReminderSettings settings = ReminderPrefs.load(activity).withEnabled(true);
        save(settings);
        Snackbar.make(binding.getRoot(), activity.getString(R.string.me_reminder_set, timeText(settings)),
                Snackbar.LENGTH_SHORT).show();
    }

    private void save(ReminderSettings settings) {
        ReminderPrefs.save(activity, settings);
        ReminderScheduler.apply(activity, settings);
        render();
    }

    // ---- time ----

    private void pickTime() {
        ReminderSettings settings = ReminderPrefs.load(activity);
        new TimePickerDialog(activity, (view, hour, minute) -> save(settings.withTime(hour, minute)),
                settings.hour(), settings.minute(), DateFormat.is24HourFormat(activity)).show();
    }

    private String timeText(ReminderSettings settings) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, settings.hour());
        calendar.set(Calendar.MINUTE, settings.minute());
        return DateFormat.getTimeFormat(activity).format(calendar.getTime());
    }

    // ---- permission helpers ----

    private boolean permissionMissing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /** On, but the system will not show notifications. */
    private boolean blocked(ReminderSettings settings) {
        return settings.enabled() && !Notifications.areAllowed(activity);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        activity.startActivity(intent);
    }

    /** For the view's accessibility: the status line is a button only while notifications are blocked. */
    boolean statusIsActionable() {
        return binding.meReminderStatus.getVisibility() == View.VISIBLE && blocked(ReminderPrefs.load(activity));
    }
}

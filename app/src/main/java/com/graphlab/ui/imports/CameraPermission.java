package com.graphlab.ui.imports;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.R;

/**
 * Asks for the camera permission the way the reminder switch asks for notifications (M19): explain first, then show the
 * system dialog, and if the person has said "don't ask again", offer the settings screen instead of asking in vain.
 * Create it in {@code onCreate}, before the activity starts, because it registers an activity-result launcher.
 */
final class CameraPermission {
    private final AppCompatActivity activity;
    private final Runnable onGranted;
    private final ActivityResultLauncher<String> launcher;

    CameraPermission(@NonNull AppCompatActivity activity, @NonNull Runnable onGranted) {
        this.activity = activity;
        this.onGranted = onGranted;
        this.launcher = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onResult);
    }

    static boolean has(@NonNull AppCompatActivity activity) {
        return activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /** Runs {@code onGranted} now if the permission is held, or after the person allows it. */
    void request() {
        if (has(activity)) {
            onGranted.run();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.import_camera_why_title)
                .setMessage(R.string.import_camera_why_body)
                .setNegativeButton(R.string.import_camera_why_no, null)
                .setPositiveButton(R.string.import_camera_why_yes, (dialog, which) -> launcher.launch(Manifest.permission.CAMERA))
                .show();
    }

    private void onResult(boolean granted) {
        if (granted) {
            onGranted.run();
            return;
        }
        if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // The system will not ask again: only the settings screen can allow it now.
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.import_camera_blocked_title)
                    .setMessage(R.string.import_camera_blocked_body)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.import_camera_open_settings, (dialog, which) -> activity.startActivity(
                            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null))))
                    .show();
        } else {
            Toast.makeText(activity, R.string.import_camera_denied, Toast.LENGTH_LONG).show();
        }
    }
}

package com.graphlab.ui.imports;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.R;

/**
 * Runtime permission to read the photo list: READ_MEDIA_IMAGES on Android 13 and newer, READ_EXTERNAL_STORAGE before.
 * Explains first, then shows the system dialog. Create it in {@code onCreate}.
 */
final class MediaPermission {
    private final AppCompatActivity activity;
    private final Runnable onGranted;
    private final ActivityResultLauncher<String> launcher;

    MediaPermission(@NonNull AppCompatActivity activity, @NonNull Runnable onGranted) {
        this.activity = activity;
        this.onGranted = onGranted;
        this.launcher = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                onGranted.run();
            } else {
                Toast.makeText(activity, R.string.recent_denied, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String permission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean has() {
        return activity.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    void request() {
        if (has()) {
            onGranted.run();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.recent_why_title)
                .setMessage(R.string.recent_why_body)
                .setNegativeButton(R.string.import_camera_why_no, null)
                .setPositiveButton(R.string.import_camera_why_yes, (dialog, which) -> launcher.launch(permission()))
                .show();
    }
}

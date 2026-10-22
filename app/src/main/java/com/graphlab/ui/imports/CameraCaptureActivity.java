package com.graphlab.ui.imports;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.graphlab.R;
import com.graphlab.databinding.ActivityCameraCaptureBinding;
import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * A small CameraX screen (MS16, G10): live preview and one button. The photo is written to the app's cache folder and its
 * path goes back to {@link ImportActivity}. Nothing leaves the phone. If the permission is missing the screen closes.
 */
public class CameraCaptureActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";

    private ActivityCameraCaptureBinding binding;
    private ImageCapture capture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.cameraCancel.setOnClickListener(v -> finish());
        binding.cameraCapture.setOnClickListener(v -> takePhoto());
        binding.cameraCapture.setEnabled(false);
        if (!CameraPermission.has(this)) {
            finish();
            return;
        }
        start();
    }

    private void start() {
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
                capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture);
                binding.cameraCapture.setEnabled(true);
            } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
                Toast.makeText(this, R.string.camera_failed, Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (capture == null) {
            return;
        }
        binding.cameraCapture.setEnabled(false);
        File dir = new File(getCacheDir(), "import");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.camera_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final File file = new File(dir, "capture.jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(file).build();
        capture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_PATH, file.getAbsolutePath()));
                finish();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CameraCaptureActivity.this, R.string.camera_failed, Toast.LENGTH_LONG).show();
                binding.cameraCapture.setEnabled(true);
            }
        });
    }
}

package com.graphlab.ui.common;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.graphlab.R;
import com.graphlab.data.AppExecutors;
import com.graphlab.ui.lab.RunSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Builds the run report PDF off the main thread, then offers it in the system chooser (same FileProvider folder as shared graphs). */
public final class PdfShare {
    private PdfShare() {
    }

    public static void share(@NonNull Activity activity, @NonNull AppExecutors executors, @NonNull RunSession session) {
        File cache = activity.getCacheDir();
        String authority = activity.getPackageName() + ".fileprovider";
        String title = session.algorithm.name();
        List<String> summary = new ArrayList<>();
        summary.add("Algorithm: " + title);
        summary.add("Category: " + session.algorithm.category());
        executors.disk(() -> write(cache, title, summary, session), new AppExecutors.Callback<File>() {
            @Override
            public void onSuccess(File file) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                Uri uri = FileProvider.getUriForFile(activity, authority, file);
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, title)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri(title, uri));
                try {
                    activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_chooser_title)));
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(activity, R.string.share_no_app, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Toast.makeText(activity, R.string.export_pdf_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static File write(File cacheDir, String title, List<String> summary, RunSession session) throws IOException {
        File folder = new File(cacheDir, "shared");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        File file = new File(folder, title.replaceAll("[^A-Za-z0-9]+", "-") + "-run.pdf");
        try (FileOutputStream out = new FileOutputStream(file)) {
            RunPdf.write(out, title, summary, session.graph, session.trace);
        }
        return file;
    }
}

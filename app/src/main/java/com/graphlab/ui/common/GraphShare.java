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
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFiles;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Sends a graph to another app (messaging, email, a file manager). The graph is written to a file
 * in the app's cache folder, which is handed out through a {@code FileProvider} content URI with
 * temporary read permission, and then offered in the system chooser with {@code ACTION_SEND}.
 * That is an implicit intent: GraphLab does not choose who receives it.
 */
public final class GraphShare {
    private static final String FOLDER = "shared";
    private static final long KEEP_MILLIS = 24L * 60 * 60 * 1000;

    private GraphShare() {
    }

    /** Writes the file on the disk thread, then opens the chooser on the main thread. */
    public static void share(@NonNull Activity activity, @NonNull AppExecutors executors,
                             @NonNull GraphDocument doc, @NonNull GraphFiles.Format format) {
        File cache = activity.getCacheDir();
        String authority = activity.getPackageName() + ".fileprovider";
        executors.disk(() -> writeFile(cache, doc, format), new AppExecutors.Callback<File>() {
            @Override
            public void onSuccess(File file) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                send(activity, authority, file, doc.name(), format);
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Toast.makeText(activity, R.string.share_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static File writeFile(File cacheDir, GraphDocument doc, GraphFiles.Format format) throws IOException {
        File folder = new File(cacheDir, FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        long cutoff = System.currentTimeMillis() - KEEP_MILLIS;
        File[] old = folder.listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.lastModified() < cutoff) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        File file = new File(folder, GraphFiles.fileName(doc.name(), format));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(GraphFiles.write(doc, format).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static void send(Activity activity, String authority, File file, String title, GraphFiles.Format format) {
        Uri uri = FileProvider.getUriForFile(activity, authority, file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(format.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // setClipData returns nothing, so it cannot be chained. The clip carries the permission to the chooser's preview.
        send.setClipData(ClipData.newRawUri(title, uri));
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_chooser_title)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.share_no_app, Toast.LENGTH_LONG).show();
        }
    }
}

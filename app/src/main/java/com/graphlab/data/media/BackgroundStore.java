package com.graphlab.data.media;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.io.Background;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * The photos behind graphs (MS10 S6). A picked photo is copied into the app's own folder ({@code files/backgrounds}),
 * shrunk to at most {@value #MAX_SIDE} pixels on its longer side and saved as a JPEG. That is why the app never needs a
 * storage permission, why a huge camera photo cannot fill the phone's memory, and why the original's location data (EXIF)
 * is not kept. A graph stores only the file name. Every method does disk work, so call them off the main thread.
 */
public final class BackgroundStore {
    public static final int MAX_SIDE = 1600;
    private static final int JPEG_QUALITY = 85;
    private static final long SWEEP_GRACE_MILLIS = 60_000L;

    private final File folder;

    public BackgroundStore(@NonNull Context context) {
        this.folder = new File(context.getApplicationContext().getFilesDir(), "backgrounds");
    }

    /**
     * Copies the picked photo in and returns its new file name.
     *
     * @throws IOException if the picture cannot be read or written (a corrupt file, a full disk)
     */
    @NonNull
    public String copyIn(@NonNull ContentResolver resolver, @NonNull Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) {
                throw new IOException("The photo could not be opened.");
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("This file is not a picture.");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Background.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) {
                throw new IOException("The photo could not be opened.");
            }
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) {
            throw new IOException("This file is not a picture.");
        }
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("The photo folder could not be made.");
            }
            String name = "bg_" + System.currentTimeMillis() + ".jpg";
            File target = new File(folder, name);
            try (FileOutputStream out = new FileOutputStream(target)) {
                if (!decoded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw new IOException("The photo could not be saved.");
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw e;
            }
            return name;
        } finally {
            decoded.recycle();
        }
    }

    /** The saved photo, or null when the file is missing (an imported graph from another phone) or unreadable. */
    @Nullable
    public Bitmap load(@NonNull String fileName) {
        if (!Background.isSafeName(fileName)) {
            return null;
        }
        File file = new File(folder, fileName);
        if (!file.isFile()) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    /**
     * Deletes photos no saved graph uses any more. A file younger than a minute is kept, because the graph that will use it
     * may not be saved yet.
     */
    public void sweep(@NonNull Set<String> inUse) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (!inUse.contains(file.getName()) && now - file.lastModified() > SWEEP_GRACE_MILLIS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}

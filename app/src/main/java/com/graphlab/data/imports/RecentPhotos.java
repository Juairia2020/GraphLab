package com.graphlab.data.imports;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the newest pictures from the system media database with a Content Provider query
 * ({@code ContentResolver.query} on {@code MediaStore.Images.Media.EXTERNAL_CONTENT_URI}). This is shared device data
 * owned by another app (the Media Provider), so it needs the READ_MEDIA_IMAGES permission (READ_EXTERNAL_STORAGE before
 * Android 13). Call it off the main thread.
 */
public final class RecentPhotos {
    private RecentPhotos() {
    }

    /** One picture in the media database. */
    public static final class Photo {
        public final Uri uri;
        public final String name;

        Photo(@NonNull Uri uri, @NonNull String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    public static List<Photo> newest(@NonNull ContentResolver resolver, int limit) {
        final List<Photo> photos = new ArrayList<>();
        final String[] columns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
        try (Cursor cursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) {
                return photos;
            }
            final int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            final int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            while (photos.size() < limit && cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn));
                String name = cursor.getString(nameColumn);
                photos.add(new Photo(uri, name == null ? "" : name));
            }
        } catch (SecurityException | IllegalArgumentException denied) {
            // Permission missing or the column is not offered on this device: show nothing.
        }
        return photos;
    }

    /** A small preview of one picture, or null if it cannot be read. Call it off the main thread. */
    @Nullable
    public static Bitmap thumbnail(@NonNull ContentResolver resolver, @NonNull Uri uri, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return resolver.loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / sizePx);
            try (InputStream in = resolver.openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}

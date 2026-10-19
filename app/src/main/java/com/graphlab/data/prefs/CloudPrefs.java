package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.graphlab.engine.net.GalleryPost;

/** The name shown next to graphs the user shares. Shares the preferences file with {@link ThemePrefs}. */
public final class CloudPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY_NAME = "display_name";

    private CloudPrefs() {
    }

    @NonNull
    public static String displayName(@NonNull Context context) {
        return GalleryPost.cleanAuthor(prefs(context).getString(KEY_NAME, null));
    }

    public static void setDisplayName(@NonNull Context context, @NonNull String name) {
        prefs(context).edit().putString(KEY_NAME, GalleryPost.cleanAuthor(name)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

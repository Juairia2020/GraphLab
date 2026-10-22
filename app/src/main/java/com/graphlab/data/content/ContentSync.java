package com.graphlab.data.content;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.graphlab.BuildConfig;
import com.graphlab.data.net.HttpFetcher;
import com.graphlab.engine.content.CardBank;
import com.graphlab.engine.content.ContentException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Hosted content with an offline cache (MS7). The question cards ship inside the app, and a newer
 * copy can be hosted as a JSON file ({@code questions.json}) in the folder named by
 * {@code CONTENT_BASE_URL}.
 *
 * <ul>
 *   <li>{@link #refresh} downloads the file, checks it with the same parsers the unit tests use, and only then
 *       saves them. A bad or half-downloaded file never replaces a good one.</li>
 *   <li>{@link #loadCached} runs at app start, with no network. It installs the saved copies; if a saved file no longer
 *       parses (for example after an app update changed the format) it is deleted and the bundled copy stays.</li>
 * </ul>
 * Plain synchronous methods: call them off the main thread.
 */
public final class ContentSync {
    static final String DIR = "content";
    static final String QUESTIONS = "questions.json";
    private static final String PREFS = "graphlab_prefs";
    private static final String KEY_SYNCED_AT = "content_synced_at";

    private ContentSync() {
    }

    /** True when the build has a real address (the template still holds the YOUR_GITHUB_NAME placeholder). */
    public static boolean isConfigured() {
        String base = BuildConfig.CONTENT_BASE_URL;
        return !base.isEmpty() && !base.contains("YOUR_GITHUB_NAME");
    }

    /** Installs the saved copies, if any. Never throws; returns how many files were installed (0 or 1). */
    public static int loadCached(@NonNull Context context) {
        int installed = 0;
        File dir = new File(context.getFilesDir(), DIR);
        File questions = new File(dir, QUESTIONS);
        try {
            if (questions.isFile()) {
                try {
                    CardBank.install(CardBank.parse(read(questions)));
                    installed++;
                } catch (ContentException | IOException damaged) {
                    questions.delete();
                }
            }
        } catch (RuntimeException unexpected) {
            // the bundled content is always there, so a damaged cache must not stop the app
        }
        return installed;
    }

    /**
     * Downloads, checks, saves and installs the file.
     *
     * @return true when new content was installed
     * @throws IOException      the network failed (the caller may try again later)
     * @throws ContentException a file was downloaded but is not valid (trying again will not help)
     */
    public static boolean refresh(@NonNull Context context) throws IOException, ContentException {
        if (!isConfigured()) {
            return false;
        }
        String base = BuildConfig.CONTENT_BASE_URL.endsWith("/") ? BuildConfig.CONTENT_BASE_URL
                : BuildConfig.CONTENT_BASE_URL + "/";
        String questionsText = HttpFetcher.get(base + QUESTIONS);
        CardBank cards = CardBank.parse(questionsText);
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create the content folder.");
        }
        write(new File(dir, QUESTIONS), questionsText);
        CardBank.install(cards);
        prefs(context).edit().putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply();
        return true;
    }

    /** When the last successful refresh happened, or 0 if there has not been one. */
    public static long lastSyncedAt(@NonNull Context context) {
        return prefs(context).getLong(KEY_SYNCED_AT, 0L);
    }

    private static String read(File file) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Writes to a temporary file and renames it, so a crash in the middle leaves the old file whole. */
    private static void write(File target, String text) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Cannot save " + target.getName());
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

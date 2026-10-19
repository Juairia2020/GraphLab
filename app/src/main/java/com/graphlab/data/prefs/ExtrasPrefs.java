package com.graphlab.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import java.util.HashSet;
import java.util.Set;

/**
 * Small values of the MS17 Arena extras: the name shown on the daily leaderboard, the result of the daily puzzle the player
 * played (by date, so a second attempt on the same day does not count), and the activities the player has already been told
 * are unlocked.
 */
public final class ExtrasPrefs {
    private static final String FILE = "graphlab_prefs";
    private static final String KEY_NAME = "daily_name";
    private static final String KEY_DATE = "daily_date";
    private static final String KEY_SCORE = "daily_score";
    private static final String KEY_CORRECT = "daily_correct";
    private static final String KEY_SECONDS = "daily_seconds";
    private static final String KEY_SEEN = "unlock_seen";
    private static final String KEY_NUDGED = "daily_nudge_date";
    private static final String KEY_STARTED = "daily_started_date";
    private static final String KEY_FIRST = "daily_first_date";
    private static final String KEY_SPECS = "daily_specs_";
    private static final int SPEC_DAYS = 30;

    private ExtrasPrefs() {
    }

    /** The saved result of the last daily puzzle played. */
    public record Daily(String date, int score, int correct, int seconds) {
    }

    @NonNull
    public static String name(@NonNull Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static void saveName(@NonNull Context context, @NonNull String name) {
        prefs(context).edit().putString(KEY_NAME, name).apply();
    }

    /** The result for {@code date}, or null when the puzzle of that day has not been played on this device. */
    public static Daily daily(@NonNull Context context, @NonNull String date) {
        SharedPreferences p = prefs(context);
        if (!date.equals(p.getString(KEY_DATE, ""))) {
            return null;
        }
        return new Daily(date, p.getInt(KEY_SCORE, 0), p.getInt(KEY_CORRECT, 0), p.getInt(KEY_SECONDS, 0));
    }

    public static void saveDaily(@NonNull Context context, @NonNull Daily daily) {
        prefs(context).edit().putString(KEY_DATE, daily.date()).putInt(KEY_SCORE, daily.score())
                .putInt(KEY_CORRECT, daily.correct()).putInt(KEY_SECONDS, daily.seconds()).apply();
    }

    /** True once the puzzle of {@code date} was started on this device: leaving it half way still counts as the day's attempt. */
    public static boolean dailyStarted(@NonNull Context context, @NonNull String date) {
        return date.equals(prefs(context).getString(KEY_STARTED, ""));
    }

    public static void markDailyStarted(@NonNull Context context, @NonNull String date) {
        prefs(context).edit().putString(KEY_STARTED, date).apply();
    }

    /** The day the daily screen was first opened (yyyy-MM-dd); saved now if there is none yet. Past puzzles start from here. */
    @NonNull
    public static String firstDailyDate(@NonNull Context context, @NonNull String today) {
        String first = prefs(context).getString(KEY_FIRST, "");
        if (first.isEmpty()) {
            prefs(context).edit().putString(KEY_FIRST, today).apply();
            return today;
        }
        return first;
    }

    /** The questions saved for a day (QuestionSpec.encode text), or null. A day keeps the questions it was first built with. */
    public static String dailySpecs(@NonNull Context context, @NonNull String date) {
        return prefs(context).getString(KEY_SPECS + date, null);
    }

    /** Saves a day's questions and forgets the oldest days beyond {@link #SPEC_DAYS}. */
    public static void saveDailySpecs(@NonNull Context context, @NonNull String date, @NonNull String encoded) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor edit = p.edit().putString(KEY_SPECS + date, encoded);
        java.util.TreeSet<String> days = new java.util.TreeSet<>();
        for (String key : p.getAll().keySet()) {
            if (key.startsWith(KEY_SPECS)) {
                days.add(key.substring(KEY_SPECS.length()));
            }
        }
        days.add(date);
        while (days.size() > SPEC_DAYS) {
            edit.remove(KEY_SPECS + days.pollFirst());
        }
        edit.apply();
    }

    @NonNull
    public static Set<String> seenUnlocked(@NonNull Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_SEEN, new HashSet<>()));
    }

    public static void saveSeenUnlocked(@NonNull Context context, @NonNull Set<String> ids) {
        prefs(context).edit().putStringSet(KEY_SEEN, new HashSet<>(ids)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

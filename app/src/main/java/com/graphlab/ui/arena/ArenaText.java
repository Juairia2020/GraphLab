package com.graphlab.ui.arena;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.graphlab.R;
import com.graphlab.engine.arena.Level;

/** Small text helpers shared by the Arena screens. */
final class ArenaText {
    private ArenaText() {
    }

    @StringRes
    static int levelName(@NonNull Level level) {
        switch (level) {
            case MEDIUM:
                return R.string.level_medium;
            case HARD:
                return R.string.level_hard;
            default:
                return R.string.level_easy;
        }
    }

    /** The difficulty tag of an activity: Easy, Medium or Hard. */
    @NonNull
    static String difficultyTag(@NonNull Context context, @NonNull com.graphlab.engine.arena.QuestionKind kind) {
        switch (kind.difficulty()) {
            case HARD:
                return context.getString(R.string.arena_tag_hard);
            case MEDIUM:
                return context.getString(R.string.arena_tag_medium);
            default:
                return context.getString(R.string.arena_tag_easy);
        }
    }

    /** "★★☆" for 2 of 3 stars. */
    @NonNull
    static String stars(int earned, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < max; i++) {
            out.append(i < earned ? '\u2605' : '\u2606');
        }
        return out.toString();
    }

    @NonNull
    static String starsCount(@NonNull Context context, int n) {
        return context.getResources().getQuantityString(R.plurals.arena_stars_count, n, n);
    }

    @NonNull
    static String pointsCount(@NonNull Context context, int n) {
        return context.getResources().getQuantityString(R.plurals.arena_points_count, n, n);
    }

    @NonNull
    static String roundsCount(@NonNull Context context, int n) {
        return context.getResources().getQuantityString(R.plurals.arena_rounds_count, n, n);
    }
}

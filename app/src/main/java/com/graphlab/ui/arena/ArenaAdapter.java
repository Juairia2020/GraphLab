package com.graphlab.ui.arena;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.graphlab.R;
import com.graphlab.databinding.ItemArenaActivityBinding;
import com.graphlab.engine.arena.ArenaScoring;
import com.graphlab.engine.arena.QuestionKind;
import java.util.Objects;

/** The list of Arena activities: name, question, family and best stars (or a lock). */
final class ArenaAdapter {

    /** One activity in the list. */
    static final class Row {
        final QuestionKind kind;
        final int stars;
        final boolean unlocked;
        /** Why it is locked (shown when tapped and read by screen readers); empty when open. */
        final String lockReason;
        /** Questions of this activity the player has answered, and how many were right (from the attempts table). */
        final int answered;
        final int correct;

        Row(@NonNull QuestionKind kind, int stars, boolean unlocked, @NonNull String lockReason) {
            this(kind, stars, unlocked, lockReason, 0, 0);
        }

        Row(@NonNull QuestionKind kind, int stars, boolean unlocked, @NonNull String lockReason, int answered, int correct) {
            this.kind = kind;
            this.stars = stars;
            this.unlocked = unlocked;
            this.lockReason = lockReason;
            this.answered = answered;
            this.correct = correct;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Row)) {
                return false;
            }
            Row that = (Row) other;
            return kind == that.kind && stars == that.stars && unlocked == that.unlocked
                    && lockReason.equals(that.lockReason) && answered == that.answered && correct == that.correct;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, stars, unlocked, lockReason, answered, correct);
        }
    }

    /** What the screen does when a row is tapped. */
    interface Listener {
        void onTap(@NonNull Row row);
    }

    private final Listener listener;

    ArenaAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** Shows every row, in order, inside {@code parent}. All rows are created at once (at most about 33). */
    void show(@NonNull ViewGroup parent, @NonNull java.util.List<Row> rows) {
        parent.removeAllViews();
        for (Row row : rows) {
            Holder holder = new Holder(ItemArenaActivityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            holder.bind(row, listener);
            parent.addView(holder.binding.getRoot());
        }
    }

    static final class Holder {
        private final ItemArenaActivityBinding binding;
        private final android.view.View itemView;

        Holder(@NonNull ItemArenaActivityBinding binding) {
            this.binding = binding;
            this.itemView = binding.getRoot();
        }

        void bind(@NonNull Row row, @NonNull Listener listener) {
            QuestionKind kind = row.kind;
            binding.activityTitle.setText(kind.title());
            binding.activitySummary.setText(kind.summary());
            android.content.Context context = itemView.getContext();
            String what = kind.fromCards()
                    ? context.getString(R.string.arena_meta_cards)
                    : context.getString(R.string.arena_meta, kind.category().title(), kind.algorithm().name());
            String stats = row.answered == 0
                    ? context.getString(R.string.arena_activity_stats_none)
                    : context.getString(R.string.arena_activity_stats, row.answered,
                            Math.round(100f * row.correct / row.answered));
            binding.activityMeta.setText(context.getString(R.string.arena_meta, ArenaText.difficultyTag(context, kind), what)
                    + "\n" + stats);
            if (row.unlocked) {
                binding.activityStatus.setText(ArenaText.stars(row.stars, ArenaScoring.MAX_STARS));
                binding.activityStatus.setContentDescription(itemView.getContext().getResources().getQuantityString(
                        R.plurals.arena_stars_description, row.stars, kind.title(), row.stars));
                binding.getRoot().setContentDescription(null);
            } else {
                binding.activityStatus.setText(R.string.arena_locked);
                binding.activityStatus.setContentDescription(row.lockReason);
                binding.getRoot().setContentDescription(kind.title() + ". " + row.lockReason);
            }
            binding.getRoot().setAlpha(row.unlocked ? 1f : 0.55f);
            binding.getRoot().setOnClickListener(v -> listener.onTap(row));
        }
    }
}

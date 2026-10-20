package com.graphlab.ui.arena;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.R;
import com.graphlab.data.prefs.ArenaPrefs;
import com.graphlab.data.cloud.DailyLeaderboard;
import com.graphlab.data.prefs.ExtrasPrefs;
import com.graphlab.databinding.ActivityDailyBinding;
import com.graphlab.engine.arena.DailyPuzzle;
import com.graphlab.engine.arena.Leaderboard;
import com.graphlab.engine.arena.QuestionKind;
import com.graphlab.engine.arena.QuestionSpec;
import com.graphlab.engine.arena.UnlockGraph;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily puzzle (MS17). The date is the UTC date, so every phone shows the same puzzle for the same day. The player can play
 * it once a day; the result goes to the Firestore leaderboard and the best places are listed here. Without a connection the
 * puzzle still works and the score stays on the device. The questions come only from the activities the player has unlocked;
 * a day keeps the questions it was first built with. Starting today's puzzle uses the day's attempt. Earlier days can be played
 * again as often as wanted (practice: no leaderboard, no saved result).
 */
public class DailyPuzzleActivity extends AppCompatActivity {
    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, DailyPuzzleActivity.class);
    }

    private static final int PAST_DAYS = 14;

    private ActivityDailyBinding binding;
    private String date;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityDailyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        binding.dailyName.setText(ExtrasPrefs.name(this));
        binding.dailyPlay.setOnClickListener(v -> play());
        binding.dailyPast.setOnClickListener(v -> choosePast());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The date is read again every time the screen comes back, so a puzzle left open past midnight (UTC) moves on.
        date = DailyPuzzle.dateString(System.currentTimeMillis());
        binding.dailyDate.setText(getString(R.string.daily_date, date));
        ExtrasPrefs.Daily played = ExtrasPrefs.daily(this, date);
        boolean started = ExtrasPrefs.dailyStarted(this, date);
        binding.dailyPlay.setEnabled(played == null && !started);
        binding.dailyStatus.setText(played != null ? getString(R.string.daily_played, played.score(), played.correct(),
                played.seconds()) : started ? getString(R.string.daily_started) : "");
        ExtrasPrefs.firstDailyDate(this, date);
        binding.dailyPast.setEnabled(!pastDates().isEmpty());
        loadBoard();
    }

    /** The activities the player has unlocked so far, without the card activities. */
    private List<QuestionKind> openKinds() {
        List<QuestionKind> open = UnlockGraph.bundled().unlocked(UnlockGraph.clearedIn(ArenaPrefs.load(this)));
        List<QuestionKind> out = new ArrayList<>();
        for (QuestionKind k : open) {
            if (!k.usesCardBank()) {
                out.add(k);
            }
        }
        return out.isEmpty() ? DailyPuzzle.pool() : out;
    }

    /** The questions of a day. A day keeps the questions it was first built with, so a replay is the same puzzle. */
    private List<QuestionSpec> specsFor(String day) {
        String saved = ExtrasPrefs.dailySpecs(this, day);
        if (saved != null) {
            try {
                return QuestionSpec.decode(saved);
            } catch (IllegalArgumentException damaged) {
                // build it again below
            }
        }
        List<QuestionSpec> specs = DailyPuzzle.specs(day, openKinds());
        ExtrasPrefs.saveDailySpecs(this, day, QuestionSpec.encode(specs));
        return specs;
    }

    /** Today's puzzle counts once: starting it uses the attempt, so leaving half way does not give a second go. */
    private void play() {
        if (ExtrasPrefs.daily(this, date) != null || ExtrasPrefs.dailyStarted(this, date)) {
            return;
        }
        String name = String.valueOf(binding.dailyName.getText()).trim();
        ExtrasPrefs.saveName(this, name);
        List<QuestionSpec> specs = specsFor(date);
        ExtrasPrefs.markDailyStarted(this, date);
        binding.dailyPlay.setEnabled(false);
        startActivity(QuestionActivity.specsIntent(this, DailyPuzzle.LEVEL, DailyPuzzle.seed(date), specs, date));
    }

    /** Yesterday and back, newest first, from the day the player first opened this screen (at most PAST_DAYS). */
    private List<String> pastDates() {
        List<String> out = new ArrayList<>();
        LocalDate first = LocalDate.parse(ExtrasPrefs.firstDailyDate(this, date));
        LocalDate day = LocalDate.parse(date).minusDays(1);
        while (!day.isBefore(first) && out.size() < PAST_DAYS) {
            out.add(day.toString());
            day = day.minusDays(1);
        }
        return out;
    }

    /** Earlier puzzles can be played again and again. They do not touch the leaderboard or today's result. */
    private void choosePast() {
        final List<String> days = pastDates();
        if (days.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.daily_past_title)
                .setItems(days.toArray(new String[0]), (dialog, which) -> {
                    String day = days.get(which);
                    startActivity(QuestionActivity.specsIntent(this, DailyPuzzle.LEVEL, DailyPuzzle.seed(day), specsFor(day), null));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void loadBoard() {
        binding.dailyRank.setText("");
        if (!DailyLeaderboard.isAvailable()) {
            binding.dailyBoard.setText(R.string.daily_board_offline);
            return;
        }
        binding.dailyBoard.setText(R.string.daily_board_loading);
        final String shownDate = date;
        DailyLeaderboard.fetchTop(shownDate, new DailyLeaderboard.TopCallback() {
            @Override
            public void onTop(@NonNull List<Leaderboard.Entry> top, @NonNull String me, int myRank) {
                if (!shownDate.equals(date) || isFinishing()) {
                    return;
                }
                if (top.isEmpty()) {
                    binding.dailyBoard.setText(R.string.daily_board_empty);
                    return;
                }
                StringBuilder text = new StringBuilder();
                int place = 1;
                for (Leaderboard.Entry e : top) {
                    text.append(text.length() == 0 ? "" : "\n").append(getString(R.string.daily_board_row, place++, e.name(),
                            e.score(), e.seconds()));
                }
                binding.dailyBoard.setText(text);
                if (myRank > 0) {
                    binding.dailyRank.setText(getString(R.string.daily_board_me, myRank));
                }
            }

            @Override
            public void onFailure(@NonNull String message) {
                if (shownDate.equals(date) && !isFinishing()) {
                    binding.dailyBoard.setText(R.string.daily_board_offline);
                }
            }
        });
    }
}

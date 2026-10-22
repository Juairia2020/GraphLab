package com.graphlab.ui.arena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.GraphLabApp;
import com.graphlab.data.AppContainer;
import com.graphlab.data.prefs.ArenaPrefs;
import com.graphlab.databinding.ContentArenaBinding;
import com.graphlab.engine.arena.ArenaProgress;
import com.graphlab.engine.arena.ArenaScoring;
import com.graphlab.engine.arena.Level;
import com.graphlab.engine.arena.MistakeHeap;
import com.graphlab.engine.arena.MysteryCards;
import com.graphlab.engine.arena.QuestionSpec;
import com.graphlab.engine.arena.UnlockGraph;
import com.graphlab.data.prefs.ExtrasPrefs;
import com.graphlab.engine.arena.NeedModel;
import com.graphlab.engine.arena.QuestionKind;
import com.graphlab.engine.arena.SessionPlanner;
import com.graphlab.engine.arena.SurpriseEngine;
import com.graphlab.data.ProgressStore;
import com.graphlab.data.prefs.SurprisePrefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.Tab;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Arena tab: the list of activities that are open, and the next locked one. Tapping an open activity starts a
 * three-question round in {@link QuestionActivity}: one Easy, one Medium and one Hard question, made fresh at that moment
 * from a new random seed (MS20). Each row shows the best stars earned, or a lock that says what to clear first. Progress is
 * read again every time the tab comes back to the front.
 */
public class ArenaActivity extends BaseActivity implements ProgressStore.Listener {
    /**
     * Put on the intent of the daily-reminder notification (see {@code work.Notifications}). It is how
     * the background worker talks to this Activity: the tap on the notification starts it with this
     * extra, and it answers with a short welcome.
     */
    public static final String EXTRA_FROM_REMINDER = "com.graphlab.extra.FROM_REMINDER";

    private ContentArenaBinding binding;
    private ArenaAdapter adapter;
    private ArenaProgress progress = new ArenaProgress();
    private final Random random = new Random();
    /** What the player is weak at, from the attempts table; empty until it has loaded (then the round is a plain mix). */
    private String needs = "";
    /** Per activity id: {answered, correct}, from the attempts table. */
    private java.util.Map<String, int[]> activityStats = new java.util.HashMap<>();

    @Override
    protected Tab getTab() {
        return Tab.ARENA;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.content_arena;
    }

    @Override
    protected void onContentInflated(View content, @Nullable Bundle savedInstanceState) {
        binding = ContentArenaBinding.bind(content);
        adapter = new ArenaAdapter(this::onRowTapped);
        binding.debugButton.setOnClickListener(v -> startActivity(SurpriseDebugActivity.intent(this)));
        binding.planButton.setOnClickListener(v -> askForTime());
        binding.ghostButton.setOnClickListener(v -> startGhost());
        binding.mysteryButton.setOnClickListener(v -> showMystery());
        com.graphlab.ui.common.OverflowMenu.attach(binding.arenaMore, binding.planButton, binding.ghostButton,
                binding.mysteryButton, binding.debugButton);
        binding.dailyButton.setOnClickListener(v -> startActivity(DailyPuzzleActivity.intent(this)));
        binding.mixedButton.setOnClickListener(v -> startRound(null));

        // Opened from the reminder notification: say hello once (not again after a rotation).
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_FROM_REMINDER, false)) {
            getIntent().removeExtra(EXTRA_FROM_REMINDER);
            snack(getString(R.string.reminder_welcome));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        progress = ArenaPrefs.load(this);
        GraphLabApp.container(this).progress().addListener(this);
        render();
        loadNeeds();
    }

    @Override
    protected void onPause() {
        super.onPause();
        GraphLabApp.container(this).progress().removeListener(this);
    }

    @Override
    public void onProgressChanged() {
        render();
    }

    private void loadNeeds() {
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> {
            java.util.Map<String, int[]> byActivity = container.repository().activityStats();
            NeedModel model = new NeedModel(byActivity, container.repository().topicStats());
            return new Object[] {model.encode(), byActivity};
        }, new com.graphlab.data.AppExecutors.Callback<Object[]>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onSuccess(Object[] result) {
                needs = (String) result[0];
                activityStats = (java.util.Map<String, int[]>) result[1];
                render();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                needs = "";   // no statistics: the mixed round is then equally likely for every activity
            }
        });
    }

    private void render() {
        if (binding == null) {
            return;
        }
        binding.arenaStats.setText(getString(R.string.arena_stats,
                ArenaText.starsCount(this, progress.totalStars()),
                ArenaText.pointsCount(this, progress.totalPoints()),
                ArenaText.roundsCount(this, progress.totalPlays())));

        com.graphlab.engine.progress.PlayerProgress player = GraphLabApp.container(this).progress().player();
        binding.playerStatus.setText(getString(R.string.arena_player, player.level(), player.xp(), player.xpToNextLevel(),
                player.streakShown(java.time.Instant.now(), java.time.ZoneId.systemDefault()), player.gems()));

        binding.mixedButton.setEnabled(true);
        binding.mixedHint.setText(getString(R.string.arena_mixed_ready,
                ArenaText.stars(progress.bestMixedRound().stars(), ArenaScoring.MAX_STARS)));

        List<ArenaAdapter.Row> rows = new ArrayList<>();
        // MS17: the library is listed in unlock order (every activity after the ones it needs).
        // Only the activities already unlocked and the next locked one are shown, in portrait and in landscape.
        ArenaAdapter.Row nextLocked = null;
        int hidden = 0;
        for (QuestionKind kind : UNLOCKS.order()) {
            List<QuestionKind> missing = UNLOCKS.missing(kind, UnlockGraph.clearedIn(progress));
            boolean open = missing.isEmpty();
            int[] stats = activityStats.get(kind.id());
            int answered = stats == null ? 0 : stats[0];
            int correct = stats == null ? 0 : stats[1];
            if (open) {
                rows.add(new ArenaAdapter.Row(kind, progress.starsOf(kind), true, "", answered, correct));
            } else if (nextLocked == null) {
                nextLocked = new ArenaAdapter.Row(kind, progress.starsOf(kind), false,
                        getString(R.string.arena_locked_needs, titles(missing)), answered, correct);
            } else {
                hidden++;
            }
        }
        if (nextLocked != null) {
            rows.add(nextLocked);
        }
        adapter.show(binding.activityList, rows);
        binding.arenaHiddenNote.setVisibility(hidden > 0 ? View.VISIBLE : View.GONE);
        if (hidden > 0) {
            binding.arenaHiddenNote.setText(getResources().getQuantityString(R.plurals.arena_hidden_note, hidden, hidden));
        }
        announceUnlocks();
    }

    private static final UnlockGraph UNLOCKS = UnlockGraph.bundled();

    private static String titles(List<QuestionKind> kinds) {
        StringBuilder out = new StringBuilder();
        for (QuestionKind k : kinds) {
            out.append(out.length() == 0 ? "" : ", ").append(k.title());
        }
        return out.toString();
    }

    /** The activities that are open in the unlock graph. */
    private List<QuestionKind> openKinds() {
        return UNLOCKS.unlocked(UnlockGraph.clearedIn(progress));
    }

    /** Tells the player once about every activity that opened since the last time (the first run only records them). */
    private void announceUnlocks() {
        java.util.Set<String> seen = ExtrasPrefs.seenUnlocked(this);
        java.util.Set<String> now = new java.util.HashSet<>();
        List<QuestionKind> fresh = new ArrayList<>();
        for (QuestionKind k : openKinds()) {
            now.add(k.id());
            if (!seen.contains(k.id())) {
                fresh.add(k);
            }
        }
        if (now.equals(seen)) {
            return;
        }
        ExtrasPrefs.saveSeenUnlocked(this, now);
        if (!seen.isEmpty() && !fresh.isEmpty()) {
            snack(getString(R.string.arena_unlocked_now, titles(fresh)));
        }
    }

    // ---- Ghost round and Mystery cards (MS17) -------------------------------------------

    private static final int MISTAKES_READ = 200;

    private void startGhost() {
        final AppContainer container = GraphLabApp.container(this);
        final java.util.Set<QuestionKind> open = new java.util.HashSet<>(openKinds());
        final long now = System.currentTimeMillis();
        final long seed = random.nextLong();
        container.executors().disk(() -> MistakeHeap.ghost(container.repository().recentMistakes(MISTAKES_READ), now, seed, open),
                new com.graphlab.data.AppExecutors.Callback<List<QuestionSpec>>() {
                    @Override
                    public void onSuccess(List<QuestionSpec> specs) {
                        if (specs.isEmpty()) {
                            snack(getString(R.string.ghost_empty));
                        } else {
                            startActivity(QuestionActivity.specsIntent(ArenaActivity.this, Level.EASY, seed, specs, null));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        snack(getString(R.string.ghost_empty));
                    }
                });
    }

    private void showMystery() {
        List<QuestionKind> pool = new ArrayList<>();
        pool.addAll(openKinds());
        final List<QuestionKind> cards = MysteryCards.draw(pool, random.nextLong());
        if (cards.isEmpty()) {
            snack(getString(R.string.mystery_empty));
            return;
        }
        String[] labels = new String[cards.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(R.string.mystery_card, i + 1);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mystery_title)
                .setItems(labels, (dialog, which) -> startRound(cards.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void onRowTapped(@NonNull ArenaAdapter.Row row) {
        if (row.unlocked) {
            startRound(row.kind);
        } else {
            snack(row.lockReason);
        }
    }

    /**
     * Starts a round of one activity (three questions: Easy, Medium, Hard), or a mixed round when {@code kind} is null. Every
     * tap draws a new random seed, so the questions are made new each time; nothing about earlier questions is kept.
     */
    private void startRound(@Nullable QuestionKind kind) {
        Intent intent;
        if (kind == null) {
            // the Surprise Engine v2 chooses; everything it uses travels in the intent so a rotation rebuilds the same round
            intent = QuestionActivity.withAllowed(QuestionActivity.mixedIntent(this, random.nextLong(), needs,
                    GraphLabApp.container(this).progress().masteryCopy().encode(), SurprisePrefs.load(this).encode(),
                    System.currentTimeMillis()), openKinds());
        } else {
            intent = QuestionActivity.ladderIntent(this, kind, random.nextLong());
        }
        startActivity(intent);
    }

    // ---- planned sessions (MS12) ------------------------------------------------------

    private static final int[] PLAN_MINUTES = {3, 5, 10, 15};

    private void askForTime() {
        String[] labels = new String[PLAN_MINUTES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(R.string.plan_minutes, PLAN_MINUTES[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.plan_title)
                .setItems(labels, (dialog, which) -> showPlan(PLAN_MINUTES[which]))
                .show();
    }

    private void showPlan(int minutes) {
        SurpriseEngine engine = new SurpriseEngine(NeedModel.parse(needs),
                GraphLabApp.container(this).progress().masteryCopy(), SurprisePrefs.load(this), java.time.Instant.now());
        final SessionPlanner.Plan plan = SessionPlanner.plan(engine, minutes * 60);
        if (plan.entries().isEmpty()) {
            snack(getString(R.string.plan_empty));
            return;
        }
        final List<QuestionKind> playable = new ArrayList<>();
        for (QuestionKind k : plan.kinds()) {
            if (openKinds().contains(k)) {
                playable.add(k);   // MS17: a locked activity is left out of the session
            }
        }
        if (playable.isEmpty()) {
            snack(getString(R.string.plan_empty));
            return;
        }
        StringBuilder message = new StringBuilder(getString(R.string.plan_summary, plan.entries().size(),
                Math.max(1, Math.round(plan.seconds() / 60f))));
        int number = 1;
        for (SessionPlanner.Entry entry : plan.entries()) {
            message.append('\n').append(number++).append(". ").append(entry.kind().title());
        }
        if (plan.coverCount() > 0) {
            message.append("\n\n").append(getString(R.string.plan_cover, plan.coverCount(),
                    android.text.TextUtils.join(", ", plan.coveredTopics())));
        } else if (plan.coverDropped()) {
            message.append("\n\n").append(getString(R.string.plan_cover_dropped));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.plan_ready_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.plan_start, (dialog, which) ->
                        startActivity(QuestionActivity.plannedIntent(this, random.nextLong(), playable)))
                .show();
    }

    private void snack(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

package com.graphlab.ui.arena;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.R;
import com.graphlab.GraphLabApp;
import com.graphlab.data.AppContainer;
import com.graphlab.data.prefs.ArenaPrefs;
import com.graphlab.databinding.ActivityQuestionBinding;
import com.graphlab.engine.arena.ArenaProgress;
import com.graphlab.engine.arena.ArenaRound;
import com.graphlab.engine.arena.ArenaScoring;
import com.graphlab.engine.arena.Interaction;
import com.graphlab.engine.arena.Level;
import com.graphlab.engine.arena.PathBoard;
import com.graphlab.engine.arena.Task;
import com.graphlab.engine.arena.NeedModel;
import com.graphlab.engine.arena.Question;
import com.graphlab.engine.arena.QuestionKind;
import com.graphlab.engine.arena.SurpriseEngine;
import com.graphlab.engine.progress.Mastery;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.edit.Viewport;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.GraphInput;
import com.graphlab.engine.input.Input;
import com.graphlab.engine.input.Item;
import com.graphlab.engine.input.KnapsackInput;
import com.graphlab.engine.input.TextInput;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.play.FrameSnapshot;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.State;
import com.graphlab.ui.common.GraphView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One Arena round: five questions, one after the other. Each shows what to look at (a graph, a list of numbers or
 * the knapsack items), four answers, and after the player picks one the explanation. The round is decided by
 * its kind, level and seed, so rotating the phone rebuilds the same round and replays the answers
 * ({@link ArenaRound#restore}). A normal screen, not a tab; it is opened from {@link ArenaActivity} with an
 * explicit intent.
 */
public class QuestionActivity extends AppCompatActivity {
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_LEVEL = "level";
    private static final String EXTRA_SEED = "seed";
    private static final String EXTRA_NEEDS = "needs";
    private static final String EXTRA_MASTERY = "mastery";
    private static final String EXTRA_WEIGHTS = "weights";
    private static final String EXTRA_NOW = "now_millis";
    private static final String EXTRA_PLAN = "plan";
    private static final String EXTRA_SPECS = "specs";           // MS17: exact questions (Ghost round, daily puzzle)
    private static final String EXTRA_DAILY = "daily_date";      // MS17: the date of the daily puzzle being played
    private static final String EXTRA_ALLOWED = "allowed";       // MS17: ids of the unlocked activities for a mixed round
    private static final String KEY_CHOICES = "choices";
    private static final String KEY_INDEX = "index";
    private static final String KEY_RESULT = "result_shown";
    private static final String KEY_RECORDED = "recorded";
    private static final String KEY_DAILY_SCORE = "daily_score";
    private static final String KEY_DAILY_SECONDS = "daily_seconds";
    private static final String KEY_DAILY_RIGHT = "daily_right";

    /** The intent for an activity round (MS20): three questions of {@code kind}, Easy then Medium then Hard, made from {@code seed}. */
    @NonNull
    public static Intent ladderIntent(@NonNull Context context, @NonNull QuestionKind kind, long seed) {
        return intent(context, kind, Level.EASY, seed);
    }

    /** The intent that starts a round. {@code kind} null means a mixed round. */
    @NonNull
    public static Intent intent(@NonNull Context context, @Nullable QuestionKind kind, @NonNull Level level, long seed) {
        Intent intent = new Intent(context, QuestionActivity.class);
        if (kind != null) {
            intent.putExtra(EXTRA_KIND, kind.id());
        }
        intent.putExtra(EXTRA_LEVEL, level.name());
        intent.putExtra(EXTRA_SEED, seed);
        return intent;
    }

    /**
     * The intent for a mixed round chosen by need: {@code needs} is {@link NeedModel#encode()} text built from the
     * attempts table, so activities the player is weaker at come up more often.
     */
    @NonNull
    public static Intent intent(@NonNull Context context, @Nullable QuestionKind kind, @NonNull Level level, long seed,
            @Nullable String needs) {
        Intent intent = intent(context, kind, level, seed);
        if (needs != null) {
            intent.putExtra(EXTRA_NEEDS, needs);
        }
        return intent;
    }

    /**
     * The intent for a mixed round chosen by the Surprise Engine v2 (MS12). Everything the engine used is carried in the
     * intent (needs, mastery, weights and the moment), so a rotated screen builds exactly the same round again even though
     * the mastery changes as the player answers.
     */
    @NonNull
    public static Intent mixedIntent(@NonNull Context context, long seed, @NonNull String needs,
            @NonNull String mastery, @NonNull String weights, long nowMillis) {
        Intent intent = intent(context, null, Level.EASY, seed, needs);
        intent.putExtra(EXTRA_MASTERY, mastery);
        intent.putExtra(EXTRA_WEIGHTS, weights);
        intent.putExtra(EXTRA_NOW, nowMillis);
        return intent;
    }

    /** The intent for a planned session (MS12): one question for each activity in {@code kinds}, in that order. */
    @NonNull
    public static Intent plannedIntent(@NonNull Context context, long seed,
            @NonNull java.util.List<QuestionKind> kinds) {
        StringBuilder ids = new StringBuilder();
        for (QuestionKind kind : kinds) {
            ids.append(ids.length() == 0 ? "" : ",").append(kind.id());
        }
        return intent(context, null, Level.EASY, seed).putExtra(EXTRA_PLAN, ids.toString());
    }

    /**
     * The intent for a round of exact questions (MS17): the Ghost round or the daily puzzle. {@code dailyDate} is the UTC date
     * of the daily puzzle, or null for a Ghost round. Neither one counts toward the stars that unlock levels.
     */
    @NonNull
    public static Intent specsIntent(@NonNull Context context, @NonNull Level level, long seed,
            @NonNull java.util.List<com.graphlab.engine.arena.QuestionSpec> specs, @Nullable String dailyDate) {
        Intent intent = intent(context, null, level, seed).putExtra(EXTRA_SPECS,
                com.graphlab.engine.arena.QuestionSpec.encode(specs));
        if (dailyDate != null) {
            intent.putExtra(EXTRA_DAILY, dailyDate);
        }
        return intent;
    }

    /** Limits a mixed round to the activities that are unlocked (MS17). */
    @NonNull
    public static Intent withAllowed(@NonNull Intent intent, @NonNull java.util.List<QuestionKind> kinds) {
        StringBuilder ids = new StringBuilder();
        for (QuestionKind kind : kinds) {
            ids.append(ids.length() == 0 ? "" : ",").append(kind.id());
        }
        return intent.putExtra(EXTRA_ALLOWED, ids.toString());
    }

    private static final float SWIPE_DP = 96f;

    private ActivityQuestionBinding binding;
    private com.graphlab.ui.common.SoundEffects sounds;
    private MaterialButton[] options;
    private ArenaRound round;
    private ArenaProgress progress;
    private Viewport viewport = new Viewport();
    private boolean resultShown;
    private boolean recorded;
    private long shownAtMillis;      // when the current question appeared (daily puzzle timing)
    private int dailyScore;
    private int dailySeconds;
    private int dailyRight;
    private int colourRight;
    private int colourWrong;
    private int colourOutline;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        sounds = new com.graphlab.ui.common.SoundEffects(this);
        binding = ActivityQuestionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });

        round = buildRound();
        if (round == null) {
            finish();
            return;
        }
        progress = ArenaPrefs.load(this);
        colourRight = MaterialColors.getColor(this, R.attr.gl_visited, Color.GREEN);
        colourWrong = MaterialColors.getColor(this, R.attr.gl_fail, Color.RED);
        colourOutline = MaterialColors.getColor(this, R.attr.gl_outline, Color.GRAY);
        binding.questionProgress.setIndicatorColor(MaterialColors.getColor(this, R.attr.gl_primary, Color.BLUE));
        binding.questionProgress.setTrackColor(colourOutline);
        binding.questionProgress.setMax(round.size());

        options = new MaterialButton[] {binding.option0, binding.option1, binding.option2, binding.option3};
        for (int i = 0; i < options.length; i++) {
            final int choice = i;
            options[i].setOnClickListener(v -> onOption(choice));
        }
        attachSwipe();
        binding.nextButton.setOnClickListener(v -> onNext());
        sizeCanvas();
        // Tap the floating explanation to fold it down to the verdict, and again to open it.
        binding.feedbackCard.setOnClickListener(v -> {
            boolean open = binding.feedbackScroll.getVisibility() == View.VISIBLE;
            binding.feedbackScroll.setVisibility(open ? View.GONE : View.VISIBLE);
            reserveRoomForCard();
        });
        binding.questionQuit.setOnClickListener(v -> confirmLeave());
        binding.againButton.setOnClickListener(v -> playAgain());
        binding.doneButton.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        });

        if (savedInstanceState != null) {
            try {
                round.restore(savedInstanceState.getIntArray(KEY_CHOICES) == null
                        ? new int[0] : savedInstanceState.getIntArray(KEY_CHOICES),
                        savedInstanceState.getInt(KEY_INDEX, 0));
            } catch (IllegalArgumentException | IllegalStateException damaged) {
                round = buildRound();   // the saved answers do not fit: start the round again
            }
            resultShown = savedInstanceState.getBoolean(KEY_RESULT, false) && round.isFinished();
            recorded = savedInstanceState.getBoolean(KEY_RECORDED, false);
            dailyScore = savedInstanceState.getInt(KEY_DAILY_SCORE, 0);
            dailySeconds = savedInstanceState.getInt(KEY_DAILY_SECONDS, 0);
            dailyRight = savedInstanceState.getInt(KEY_DAILY_RIGHT, 0);
        }
        if (resultShown) {
            showResult();
        } else {
            showQuestion();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (round != null) {
            outState.putIntArray(KEY_CHOICES, round.choices());
            outState.putInt(KEY_INDEX, round.index());
        }
        outState.putBoolean(KEY_RESULT, resultShown);
        outState.putBoolean(KEY_RECORDED, recorded);
        outState.putInt(KEY_DAILY_SCORE, dailyScore);
        outState.putInt(KEY_DAILY_SECONDS, dailySeconds);
        outState.putInt(KEY_DAILY_RIGHT, dailyRight);
    }

    // ---- the round -------------------------------------------------------------------

    @Nullable
    private ArenaRound buildRound() {
        Intent intent = getIntent();
        try {
            Level level = Level.valueOf(intent.getStringExtra(EXTRA_LEVEL));
            long seed = intent.getLongExtra(EXTRA_SEED, 0L);
            String kindId = intent.getStringExtra(EXTRA_KIND);
            String plan = intent.getStringExtra(EXTRA_PLAN);
            String specs = intent.getStringExtra(EXTRA_SPECS);
            if (specs != null) {
                return ArenaRound.fromSpecs(level, seed, com.graphlab.engine.arena.QuestionSpec.decode(specs));
            }
            java.util.List<QuestionKind> allowed = allowedKinds();
            if (plan != null) {
                java.util.List<QuestionKind> kinds = new java.util.ArrayList<>();
                for (String id : plan.split(",")) {
                    kinds.add(QuestionKind.byId(id));
                }
                return ArenaRound.plannedClimbing(seed, kinds);
            }
            if (kindId != null) {
                return ArenaRound.ladder(QuestionKind.byId(kindId), seed);
            }
            if (intent.getStringExtra(EXTRA_MASTERY) != null) {
                SurpriseEngine engine = new SurpriseEngine(NeedModel.parse(intent.getStringExtra(EXTRA_NEEDS)),
                        Mastery.parse(intent.getStringExtra(EXTRA_MASTERY)),
                        SurpriseEngine.Weights.parse(String.valueOf(intent.getStringExtra(EXTRA_WEIGHTS))),
                        java.time.Instant.ofEpochMilli(intent.getLongExtra(EXTRA_NOW, 0L)));
                return ArenaRound.mixedClimbing(seed, engine, allowed);
            }
            return ArenaRound.mixedClimbing(seed, NeedModel.parse(intent.getStringExtra(EXTRA_NEEDS)), allowed);
        } catch (IllegalArgumentException | NullPointerException | IllegalStateException bad) {
            return null;
        }
    }

    /** The activities a mixed round may use: the unlocked ones from the intent, or all of them. */
    private java.util.List<QuestionKind> allowedKinds() {
        String ids = getIntent().getStringExtra(EXTRA_ALLOWED);
        java.util.List<QuestionKind> out = new java.util.ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            for (String id : ids.split(",")) {
                QuestionKind kind = QuestionKind.findById(id);
                if (kind != null) {
                    out.add(kind);
                }
            }
        }
        return out.isEmpty() ? java.util.List.of(QuestionKind.values()) : out;
    }

    private void playAgain() {
        Level level = round.level();
        Intent next = QuestionActivity.intent(this, round.kind(), level, new Random().nextLong(),
                getIntent().getStringExtra(EXTRA_NEEDS));
        for (String key : new String[] {EXTRA_MASTERY, EXTRA_WEIGHTS, EXTRA_PLAN, EXTRA_ALLOWED}) {
            if (getIntent().getStringExtra(key) != null) {
                next.putExtra(key, getIntent().getStringExtra(key));
            }
        }
        next.putExtra(EXTRA_NOW, getIntent().getLongExtra(EXTRA_NOW, 0L));
        startActivity(next);
        finish();
    }

    private void confirmLeave() {
        if (resultShown || round.answeredCount() == 0) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.question_leave_title)
                .setMessage(R.string.question_leave_body)
                .setNegativeButton(R.string.question_leave_stay, null)
                .setPositiveButton(R.string.question_leave_go, (dialog, which) -> finish())
                .show();
    }

    // ---- one question ----------------------------------------------------------------

    private void showQuestion() {
        resultShown = false;
        binding.resultBox.setVisibility(View.GONE);
        binding.questionBox.setVisibility(View.VISIBLE);
        if (binding.questionVisual != null) {
            binding.questionVisual.setVisibility(View.VISIBLE);
        }
        Question q = round.current();
        String levelName = getString(ArenaText.levelName(q.level()));
        binding.questionTitle.setText(round.isMixed()
                ? getString(R.string.question_mixed_title, q.kind().title(), levelName)
                : getString(R.string.question_title, q.kind().title(), levelName));
        binding.questionCounter.setText(getString(R.string.question_counter, round.index() + 1, round.size()));
        binding.questionProgress.setProgress(round.index() + (round.isAnswered() ? 1 : 0));
        binding.questionPrompt.setText(q.prompt());
        showVisual(q);
        bindTask(q);

        for (int i = 0; i < options.length; i++) {
            MaterialButton button = options[i];
            if (i < q.options().size()) {
                button.setVisibility(View.VISIBLE);
                button.setText(q.options().get(i));
                paintOption(button, colourOutline, 1);
            } else {
                button.setVisibility(View.GONE);
            }
        }
        binding.feedbackCard.setVisibility(View.GONE);
        binding.nextButton.setVisibility(View.GONE);
        binding.questionScroll.setPadding(0, 0, 0, 0);
        binding.questionScroll.scrollTo(0, 0);
        shownAtMillis = android.os.SystemClock.elapsedRealtime();
        resetSwipeCard();
        if (round.isAnswered()) {
            showFeedback(round.choice(round.index()));
        }
    }

    private void onOption(int choice) {
        answerWith(choice);
    }

    /** The one way an answer enters the round: a button position, or for a task the number of mistakes. */
    private void answerWith(int value) {
        if (round.isAnswered() || resultShown) {
            return;
        }
        Question q = round.current();
        round.answer(value);
        if (getIntent().getStringExtra(EXTRA_DAILY) != null) {
            int seconds = (int) Math.min(3600, Math.max(0, (android.os.SystemClock.elapsedRealtime() - shownAtMillis) / 1000));
            dailySeconds += seconds;
            dailyScore += com.graphlab.engine.arena.DailyScore.answer(q.isCorrect(value), seconds);
            dailyRight += q.isCorrect(value) ? 1 : 0;
        }
        recordAttempt(q, q.isCorrect(value));
        GraphLabApp.container(this).progress().recordAnswer(q.topic(), q.isCorrect(value));   // mastery (MS12)
        binding.questionProgress.setProgress(round.index() + 1);
        showFeedback(value);
        celebrate(value, q);
    }

    /** Writes one row to the attempts table (off the main thread). Failing to write must never stop the round. */
    private void recordAttempt(Question q, boolean correct) {
        final AppContainer container = GraphLabApp.container(this);
        final String activity = q.kind().id();
        final String topic = q.topic();
        final String level = q.level().name();
        final long now = System.currentTimeMillis();
        container.executors().disk(() -> {
            try {
                container.repository().addAttempt(activity, topic, level, correct, now);
            } catch (RuntimeException ignored) {
                // statistics are a bonus; the round goes on without them
            }
        });
    }

    // ---- feedback: haptics and animation ---------------------------------------------

    private void celebrate(int choice, Question q) {
        boolean right = q.isCorrect(choice);
        View chosen = q.task() != null ? binding.feedbackCard : options[choice];
        if (com.graphlab.data.prefs.EffectsPrefs.haptics(this)) {
            chosen.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? (right ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT)
                    : HapticFeedbackConstants.VIRTUAL_KEY);
        }
        sounds.playAnswer(right);
        if (right) {
            chosen.setScaleX(0.94f);
            chosen.setScaleY(0.94f);
            chosen.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new OvershootInterpolator()).start();
        } else {
            ObjectAnimator shake = ObjectAnimator.ofFloat(chosen, View.TRANSLATION_X, 0f, -16f, 16f, -10f, 10f, 0f);
            shake.setDuration(320);
            shake.start();
        }
    }

    // ---- true or false: swipe the card -----------------------------------------------

    private boolean swipeEnabled(Question q) {
        return q.kind() == QuestionKind.TRUE_FALSE && !round.isAnswered();
    }

    private void resetSwipeCard() {
        binding.questionItems.animate().cancel();
        binding.questionItems.setTranslationX(0f);
        binding.questionItems.setRotation(0f);
        binding.questionItems.setAlpha(1f);
    }

    /** Swipe right = the first answer (True), left = the second (False). The buttons stay for taps and screen readers. */
    @SuppressLint("ClickableViewAccessibility")
    private void attachSwipe() {
        final View card = binding.questionItems;
        final float threshold = SWIPE_DP * getResources().getDisplayMetrics().density;
        final float[] downX = new float[1];
        card.setOnTouchListener((v, event) -> {
            if (resultShown || round == null || !swipeEnabled(round.current())) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX[0];
                    v.setTranslationX(dx);
                    v.setRotation(dx / 40f);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float dx = event.getRawX() - downX[0];
                    if (Math.abs(dx) >= threshold) {
                        onOption(dx > 0 ? 0 : 1);
                        v.animate().translationX(0f).rotation(0f).setDuration(200).start();
                    } else {
                        v.animate().translationX(0f).rotation(0f).setDuration(160).start();
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    v.animate().translationX(0f).rotation(0f).setDuration(160).start();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void showFeedback(int choice) {
        Question q = round.current();
        boolean right = q.isCorrect(choice);
        for (int i = 0; i < q.options().size(); i++) {
            if (i == q.correctIndex()) {
                options[i].setText(getString(R.string.question_right_mark, q.options().get(i)));
                paintOption(options[i], colourRight, 3);
            } else if (i == choice) {
                options[i].setText(getString(R.string.question_wrong_mark, q.options().get(i)));
                paintOption(options[i], colourWrong, 3);
            }
        }
        String verdict;
        if (q.task() != null && right && choice > 0) {
            verdict = getString(R.string.task_verdict_close, getString(R.string.question_correct,
                    ArenaScoring.points(1, q.level())));
        } else if (right) {
            verdict = getString(R.string.question_correct, ArenaScoring.points(1, q.level()));
        } else if (q.task() != null) {
            verdict = getString(R.string.question_wrong, q.correctAnswer()) ;
        } else {
            verdict = getString(R.string.question_wrong, q.correctAnswer());
        }
        binding.feedbackVerdict.setText(verdict);
        binding.feedbackText.setText(q.explanation());
        if (q.task() != null) {
            lockTask(q);
        }
        binding.feedbackCard.setVisibility(View.VISIBLE);
        binding.nextButton.setText(round.hasNext() ? R.string.question_next : R.string.question_see_result);
        binding.nextButton.setVisibility(View.VISIBLE);
        binding.feedbackScroll.setVisibility(View.VISIBLE);
        binding.feedbackScroll.post(() -> {
            // The explanation scrolls inside the card when it is long, so the card never covers more than a third of the screen.
            int max = (int) (140 * getResources().getDisplayMetrics().density);
            android.view.ViewGroup.LayoutParams lp = binding.feedbackScroll.getLayoutParams();
            View inner = binding.feedbackScroll.getChildAt(0);
            lp.height = Math.min(inner.getHeight(), max);
            binding.feedbackScroll.setLayoutParams(lp);
            reserveRoomForCard();
        });
    }

    /** The graph gets about half of the screen height (at least 320 dp), because lots of nodes and weights need room. */
    private void sizeCanvas() {
        if (binding.questionVisual != null) {
            return;   // landscape: the graph fills its own pane (layout-land/activity_question.xml)
        }
        float density = getResources().getDisplayMetrics().density;
        int screen = getResources().getDisplayMetrics().heightPixels;
        int height = Math.max((int) (320 * density), (int) (screen * 0.5f));
        android.view.ViewGroup.LayoutParams lp = binding.questionGraph.getLayoutParams();
        lp.height = height;
        binding.questionGraph.setLayoutParams(lp);
    }

    /** Lets the question scroll far enough that the floating card never hides the last option. */
    private void reserveRoomForCard() {
        binding.feedbackCard.post(() -> {
            int room = binding.feedbackCard.getVisibility() == View.VISIBLE ? binding.feedbackCard.getHeight()
                    + (int) (16 * getResources().getDisplayMetrics().density) : 0;
            binding.questionScroll.setClipToPadding(false);
            binding.questionScroll.setPadding(0, 0, 0, room);
        });
    }

    // ---- the task activities (MS11) ----------------------------------------------------

    private OrderAdapter orderAdapter;
    private ItemTouchHelper orderHelper;
    private PathBoard pathBoard;
    private final java.util.List<EditText> tableCells = new java.util.ArrayList<>();

    @Override
    protected void onDestroy() {
        sounds.release();
        super.onDestroy();
    }

    private void hideTaskViews() {
        if (orderHelper != null) {
            orderHelper.attachToRecyclerView(null);
            orderHelper = null;
        }
        orderAdapter = null;
        pathBoard = null;
        tableCells.clear();
        binding.taskOrder.setAdapter(null);
        binding.taskMatch.removeAllViews();
        binding.taskOrder.setVisibility(View.GONE);
        binding.taskMatch.setVisibility(View.GONE);
        binding.taskNumberLayout.setVisibility(View.GONE);
        binding.taskTableScroll.setVisibility(View.GONE);
        binding.taskTable.removeAllViews();
        binding.taskMessage.setVisibility(View.GONE);
        binding.taskCheck.setVisibility(View.GONE);
        binding.taskCheck.setOnClickListener(null);
        binding.questionGraph.setListener(null);
    }

    private void bindTask(Question q) {
        hideTaskViews();
        Task task = q.task();
        if (task == null) {
            return;
        }
        boolean open = !round.isAnswered();
        switch (task.interaction()) {
            case ORDER:
                bindOrder(task);
                break;
            case MATCH:
                bindMatch(task);
                break;
            case NUMBER:
                bindNumber(task);
                break;
            case TABLE:
                bindTable(task);
                break;
            case PATH:
                bindPath(q, (GraphInput) q.input());
                break;
            default:
                break;
        }
        if (open) {
            binding.taskCheck.setVisibility(View.VISIBLE);
        }
    }

    private void taskMessage(@Nullable String text) {
        binding.taskMessage.setText(text);
        binding.taskMessage.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void bindOrder(Task task) {
        orderAdapter = new OrderAdapter(task.items(), () -> { });
        binding.taskOrder.setLayoutManager(new LinearLayoutManager(this));
        binding.taskOrder.setAdapter(orderAdapter);
        orderHelper = orderAdapter.attachDrag(binding.taskOrder);
        binding.taskOrder.setVisibility(View.VISIBLE);
        binding.taskCheck.setOnClickListener(v -> answerWith(task.mistakes(orderAdapter.order())));
    }

    /** A plain matching table: each term on the left has a drop-down of meanings on the right; Check scores the wrong rows. */
    private void bindMatch(Task task) {
        final List<String> items = task.items();
        final int pairs = items.size() / 2;
        List<String> meanings = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            meanings.add(items.get(2 * i + 1));
        }
        Collections.shuffle(meanings, new Random(round.seed() * 31 + round.index()));
        final List<String> options = new ArrayList<>();
        options.add(getString(R.string.task_match_choose));
        options.addAll(meanings);
        final float density = getResources().getDisplayMetrics().density;
        final Spinner[] spinners = new Spinner[pairs];
        binding.taskMatch.removeAllViews();
        for (int i = 0; i < pairs; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            TextView term = new TextView(this);
            term.setText(items.get(2 * i));
            term.setTextSize(15f);
            term.setPadding(0, 0, (int) (8 * density), 0);
            row.addView(term, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Spinner spinner = new Spinner(this);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
            spinner.setEnabled(!round.isAnswered());
            spinners[i] = spinner;
            row.addView(spinner, new LinearLayout.LayoutParams(0, (int) (48 * density), 1f));
            binding.taskMatch.addView(row);
        }
        binding.taskMatch.setVisibility(View.VISIBLE);
        taskMessage(getString(R.string.task_match_table_hint, task.tolerance()));
        binding.taskCheck.setOnClickListener(v -> {
            int wrong = 0;
            for (int i = 0; i < pairs; i++) {
                int chosen = spinners[i].getSelectedItemPosition();
                if (chosen <= 0) {
                    taskMessage(getString(R.string.task_match_incomplete));
                    return;
                }
                if (!options.get(chosen).equals(items.get(2 * i + 1))) {
                    wrong++;
                }
            }
            answerWith(task.mistakes(wrong));
        });
    }

    private void bindNumber(Task task) {
        binding.taskNumber.setText("");
        binding.taskNumber.setEnabled(!round.isAnswered());
        binding.taskNumberLayout.setVisibility(View.VISIBLE);
        binding.taskCheck.setOnClickListener(v -> {
            String text = String.valueOf(binding.taskNumber.getText()).trim();
            int value;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException missing) {
                taskMessage(getString(R.string.task_number_missing));
                return;
            }
            answerWith(task.mistakes(value));
        });
    }

    private void bindTable(Task task) {
        int columns = task.columns();
        float density = getResources().getDisplayMetrics().density;
        int cell = getResources().getDimensionPixelSize(R.dimen.table_cell_width);
        TableRow header = new TableRow(this);
        header.addView(tableText(getString(R.string.task_table_corner), cell));
        for (int w = 0; w < columns; w++) {
            header.addView(tableText(task.colHeaders().isEmpty() ? getString(R.string.task_table_column, w)
                    : task.colHeaders().get(w), cell));
        }
        binding.taskTable.addView(header);
        int rows = task.items().size() / columns;
        int blank = 0;
        for (int i = 0; i < rows; i++) {
            TableRow row = new TableRow(this);
            row.addView(tableText(task.rowHeaders().isEmpty() ? getString(R.string.task_table_row, i)
                    : task.rowHeaders().get(i), cell));
            for (int w = 0; w < columns; w++) {
                String value = task.items().get(i * columns + w);
                if (value.isEmpty()) {
                    EditText field = new EditText(this);
                    field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    field.setGravity(Gravity.CENTER);
                    field.setMinWidth(cell);
                    field.setTextSize(15f);
                    field.setMaxLines(1);
                    field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(5)});
                    field.setContentDescription(getString(R.string.task_table_blank, i, w));
                    field.setEnabled(!round.isAnswered());
                    tableCells.add(field);
                    row.addView(field);
                    blank++;
                } else {
                    row.addView(tableText(value, cell));
                }
            }
            binding.taskTable.addView(row);
        }
        binding.taskTableScroll.setVisibility(View.VISIBLE);
        binding.taskCheck.setOnClickListener(v -> {
            int[] given = new int[tableCells.size()];
            for (int i = 0; i < given.length; i++) {
                try {
                    given[i] = Integer.parseInt(String.valueOf(tableCells.get(i).getText()).trim());
                } catch (NumberFormatException missing) {
                    taskMessage(getString(R.string.task_table_missing));
                    return;
                }
            }
            answerWith(task.mistakes(given));
        });
    }

    private TextView tableText(String text, int width) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(width);
        view.setPadding(4, 12, 4, 12);
        view.setTextColor(MaterialColors.getColor(this, R.attr.gl_text, Color.BLACK));
        return view;
    }

    private void bindPath(Question q, GraphInput input) {
        final Graph graph = input.graph();
        pathBoard = new PathBoard(graph, q.source(), q.target());
        redrawPath(q, graph);
        binding.questionGraph.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int nodeId) {
                if (round.isAnswered() || pathBoard == null) {
                    return;
                }
                if (pathBoard.tap(nodeId) != PathBoard.Result.IGNORED) {
                    redrawPath(q, graph);
                }
            }

            @Override
            public void onConnectRequested(int fromId, int toId) {
            }

            @Override
            public void onEdgeWeightRequested(int edgeId) {
            }

            @Override
            public void onMessage(int message) {
            }

            @Override
            public void onGraphChanged() {
            }
        });
        binding.taskCheck.setOnClickListener(v -> {
            if (!pathBoard.isComplete()) {
                taskMessage(getString(R.string.task_path_incomplete));
                return;
            }
            answerWith(pathBoard.mistakes(q.task()));
        });
    }

    private void redrawPath(Question q, Graph graph) {
        GraphFrame.Builder paint = GraphFrame.builder(graph);
        for (int node : pathBoard.nodes()) {
            paint.node(node, State.VISITED);
        }
        for (int edge : pathBoard.edges()) {
            paint.edge(edge, State.PATH);
        }
        paint.node(q.source(), State.CHOSEN).label(q.source(), getString(R.string.question_label_start));
        paint.node(q.target(), State.PATH).label(q.target(), getString(R.string.question_label_end));
        binding.questionGraph.setFrame(FrameSnapshot.of(graph, paint.build()));
        StringBuilder route = new StringBuilder();
        for (int node : pathBoard.nodes()) {
            route.append(route.length() == 0 ? "" : " \u2192 ").append(graph.nodeName(node));
        }
        taskMessage(getString(R.string.task_path_status, route, WeightFormat.format(pathBoard.weight())));
    }

    /** After the answer: no more typing, dragging or tapping; a table shows its right values. */
    private void lockTask(Question q) {
        Task task = q.task();
        binding.taskCheck.setVisibility(View.GONE);
        if (orderAdapter != null) {
            orderAdapter.setEnabled(false);
        }
        for (int i = 0; i < binding.taskMatch.getChildCount(); i++) {
            View row = binding.taskMatch.getChildAt(i);
            if (row instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) row;
                for (int c = 0; c < group.getChildCount(); c++) {
                    group.getChildAt(c).setEnabled(false);
                }
            }
        }
        binding.taskNumber.setEnabled(false);
        if (task.interaction() == Interaction.TABLE) {
            for (int i = 0; i < tableCells.size(); i++) {
                tableCells.get(i).setEnabled(false);
                tableCells.get(i).setText(Integer.toString(task.solution().get(i)));
            }
        }
        taskMessage(getString(R.string.task_answer_was, q.correctAnswer()));
    }

    private void onNext() {
        if (!round.isAnswered()) {
            return;
        }
        if (round.hasNext()) {
            round.advance();
            showQuestion();
        } else {
            showResult();
        }
    }

    private static void paintOption(MaterialButton button, int strokeColour, int strokeDp) {
        button.setStrokeColor(ColorStateList.valueOf(strokeColour));
        button.setStrokeWidth(Math.round(strokeDp * button.getResources().getDisplayMetrics().density));
    }

    // ---- what to look at -------------------------------------------------------------

    private void showVisual(Question q) {
        binding.questionGraph.setVisibility(View.GONE);
        binding.questionArray.setVisibility(View.GONE);
        binding.questionItems.setVisibility(View.GONE);
        Input input = q.input();
        if (input instanceof GraphInput) {
            showGraph(q, ((GraphInput) input).graph());
        } else if (input instanceof ArrayInput) {
            java.util.List<Integer> values = ((ArrayInput) input).values();
            binding.questionArray.setFrame(new ArrayFrame(values, Collections.nCopies(values.size(), State.IDLE)));
            binding.questionArray.setContentDescription(values.toString());
            binding.questionArray.setVisibility(View.VISIBLE);
        } else if (input instanceof TextInput) {
            binding.questionItems.setText(((TextInput) input).body());
            binding.questionItems.setTextSize(18f);
            binding.questionItems.setVisibility(View.VISIBLE);
        } else if (input instanceof KnapsackInput) {
            binding.questionItems.setTextSize(14f);
            KnapsackInput bag = (KnapsackInput) input;
            StringBuilder text = new StringBuilder(getString(R.string.question_capacity, bag.capacity()));
            int number = 1;
            for (Item item : bag.items()) {
                text.append('\n').append(getString(R.string.question_item_line, number++, item.weight(), item.value()));
            }
            binding.questionItems.setText(text);
            binding.questionItems.setVisibility(View.VISIBLE);
        }
    }

    private void showGraph(Question q, Graph graph) {
        GraphView view = binding.questionGraph;
        GraphEditor editor = GraphEditor.from(graph, q.kind().showsWeights());
        viewport = new Viewport();
        view.bind(editor, viewport);
        view.setMode(GraphView.Mode.RUN);
        view.setNodeDragEnabled(true);
        view.setHighlightNode(-1);
        if (q.frame() != null) {
            view.setFrame(FrameSnapshot.of(graph, q.frame()));
        } else if (q.source() >= 0 || q.target() >= 0) {
            GraphFrame.Builder paint = GraphFrame.builder(graph);
            if (q.source() >= 0) {
                paint.node(q.source(), State.CHOSEN).label(q.source(), getString(startLabel(q.kind())));
            }
            if (q.target() >= 0) {
                paint.node(q.target(), State.PATH).label(q.target(), getString(endLabel(q.kind())));
            }
            view.setFrame(FrameSnapshot.of(graph, paint.build()));
        } else {
            view.setFrame(null);
        }
        view.setContentDescription(getString(R.string.question_graph_description, graph.nodeCount(), graph.edgeCount()));
        view.setVisibility(View.VISIBLE);
        viewport.requestFit();
        if (view.getWidth() > 0) {
            view.fitToGraph();
        }
    }

    private static int startLabel(QuestionKind kind) {
        switch (kind) {
            case MAX_FLOW:
            case CUT_NETWORK:
                return R.string.question_label_source;
            case FLOYD_ENTRY:
                return R.string.question_label_from;
            default:
                return R.string.question_label_start;
        }
    }

    private static int endLabel(QuestionKind kind) {
        switch (kind) {
            case MAX_FLOW:
            case CUT_NETWORK:
                return R.string.question_label_sink;
            case FLOYD_ENTRY:
                return R.string.question_label_to;
            default:
                return R.string.question_label_end;
        }
    }

    // ---- the result ------------------------------------------------------------------

    private void showResult() {
        resultShown = true;
        int correct = round.correctCount();
        int total = round.size();
        int stars = round.stars();
        ArenaProgress.Outcome outcome = null;
        final boolean firstShow = !recorded;
        if (!recorded) {
            if (getIntent().getStringExtra(EXTRA_PLAN) == null && getIntent().getStringExtra(EXTRA_SPECS) == null) {
                // a planned session has its own length, so it must not be a shortcut to the star unlocks
                outcome = round.isMixed()
                        ? progress.recordMixedRound(stars, round.points())
                        : progress.recordActivity(round.kind(), stars, round.points());
                ArenaPrefs.save(this, progress);
            }
            GraphLabApp.container(this).progress().awardRound(round.points(), stars);   // xp, gems and the streak (MS12)
            recorded = true;
            submitDaily();
        }
        if (firstShow && stars >= 2) {
            sounds.playWin();   // good marks: two stars or more
        }
        boolean exact = getIntent().getStringExtra(EXTRA_SPECS) != null;
        binding.againButton.setVisibility(exact ? View.GONE : View.VISIBLE);
        binding.questionBox.setVisibility(View.GONE);
        if (binding.questionVisual != null) {
            binding.questionVisual.setVisibility(View.GONE);
        }
        binding.resultBox.setVisibility(View.VISIBLE);
        binding.questionProgress.setProgress(total);
        binding.questionCounter.setText(getString(R.string.question_counter, total, total));
        binding.resultStars.setText(ArenaText.stars(stars, ArenaScoring.MAX_STARS));
        binding.resultStars.setContentDescription(getResources().getQuantityString(
                R.plurals.arena_stars_description, stars, getString(R.string.tab_arena), stars));
        binding.resultScore.setText(getString(R.string.result_score, correct, total,
                ArenaText.pointsCount(this, round.points())));

        String note;
        if (stars == ArenaScoring.MAX_STARS) {
            note = getString(R.string.result_perfect);
        } else if (outcome != null && outcome.newBest() && stars > 0) {
            note = getString(R.string.result_new_best);
        } else if (stars == 0) {
            note = getString(R.string.result_try_again);
        } else {
            note = getString(R.string.result_keep_going);
        }
        String date = getIntent().getStringExtra(EXTRA_DAILY);
        if (date != null) {
            note = getString(R.string.daily_result, dailyScore, dailyRight, round.size());
        }
        binding.resultNote.setText(note);
    }

    /** Saves the daily result on this device and sends it to the leaderboard. The first result of a day is the one kept. */
    private void submitDaily() {
        final String date = getIntent().getStringExtra(EXTRA_DAILY);
        if (date == null || com.graphlab.data.prefs.ExtrasPrefs.daily(this, date) != null) {
            return;
        }
        final int score = dailyScore;
        final int right = dailyRight;
        final int seconds = dailySeconds;
        com.graphlab.data.prefs.ExtrasPrefs.saveDaily(this, new com.graphlab.data.prefs.ExtrasPrefs.Daily(date, score, right, seconds));
        if (!com.graphlab.data.cloud.DailyLeaderboard.isAvailable()) {
            return;
        }
        String name = com.graphlab.data.prefs.ExtrasPrefs.name(this);
        com.graphlab.data.cloud.DailyLeaderboard.submit(date, name.isEmpty() ? getString(R.string.daily_default_name) : name,
                score, right, seconds, new com.graphlab.data.cloud.DailyLeaderboard.DoneCallback() {
                    @Override
                    public void onDone() {
                        // nothing to show: the daily screen reads the board when it opens
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                        // offline: the score stays on this device; the board simply will not list it
                    }
                });
    }
}

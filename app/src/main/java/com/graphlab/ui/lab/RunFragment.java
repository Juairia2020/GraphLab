package com.graphlab.ui.lab;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.databinding.FragmentRunBinding;
import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.algo.graph.BellmanFord;
import com.graphlab.engine.algo.graph.Bfs;
import com.graphlab.engine.algo.graph.Dfs;
import com.graphlab.engine.algo.graph.Dijkstra;
import com.graphlab.engine.algo.graph.GraphColouring;
import com.graphlab.engine.algo.graph.Prim;
import com.graphlab.engine.input.ArrayInput;
import com.graphlab.engine.input.InputKind;
import com.graphlab.engine.input.InputParser;
import com.graphlab.engine.insight.Insight;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.play.FrameSnapshot;
import com.graphlab.engine.play.StepSummary;
import com.graphlab.engine.play.StepSummary.NodeRow;
import com.graphlab.engine.trace.Algorithm;
import com.graphlab.engine.trace.Panel;
import com.graphlab.engine.trace.AlgorithmCategory;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Visual;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Step;
import com.graphlab.ui.common.GraphView;
import com.graphlab.ui.common.StateColors;
import android.text.Editable;
import android.text.TextWatcher;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lab, Run mode. Pick an algorithm and a start node, press Run, then replay the finished trace:
 * the canvas shows the states of the current step, the playback bar moves through the steps,
 * and two tabs show the step log (a row per step) and the State (counters, queue, pseudocode with
 * the current line highlighted, and a table of every node).
 *
 * <p>The run itself, its play head and the playback clock live in {@link LabViewModel}. This
 * fragment only draws them and forwards taps, so rotating while playing keeps position, speed
 * and play state.
 */
public class RunFragment extends Fragment implements LabViewModel.RunListener, GraphView.Listener {

    private FragmentRunBinding binding;
    private LabViewModel model;
    private com.graphlab.ui.common.SoundEffects sounds;
    private boolean wasPlaying;
    private StepLogAdapter stepAdapter;
    private NodeTableAdapter nodeAdapter;
    private int[] stateColors;

    /** The session the step log and seek range were built for (identity compare). */
    @Nullable
    private RunSession shownSession;
    /** The algorithm the pseudocode block was built for. */
    @Nullable
    private String shownPseudocodeFor;
    private final List<TextView> codeLines = new ArrayList<>();
    private int shownLine = -1;
    private String shownCounters = "";
    private String shownFrontier = "";

    /** The photo behind the graph (MS10), shown in Run too so a traced picture stays under the animation. */
    private final Runnable photoChanged = () -> {
        if (binding != null) {
            showPhoto();
        }
    };

    private void showPhoto() {
        com.graphlab.engine.io.Background bg = model.background();
        binding.canvas.setPhoto(model.photo(), bg == null ? 0f : (float) bg.opacity());
    }
    /** What the panels container shows now, so unchanged tables are not rebuilt on every step. */
    private String shownPanels = "";
    @Nullable
    private Dialog openDialog;
    /** Which node the next tap on the canvas sets: the start (default) or, for a flow, the end. */
    private boolean choosingTarget;
    @Nullable
    private String shownInsightFor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRunBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        sounds = new com.graphlab.ui.common.SoundEffects(requireContext());
        boolean stale = model.refreshRunValidity();
        stateColors = StateColors.resolve(requireContext());

        binding.canvas.bind(model.editor(), model.viewport());
        binding.canvas.setMode(GraphView.Mode.RUN);
        model.addPhotoListener(photoChanged);
        showPhoto();
        binding.canvas.setListener(this);

        stepAdapter = new StepLogAdapter(index -> model.seek(index));
        binding.stepsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.stepsList.setAdapter(stepAdapter);
        binding.stepsList.setItemAnimator(null);   // rows only change their highlight

        nodeAdapter = new NodeTableAdapter(stateColors);
        binding.nodeTable.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.nodeTable.setAdapter(nodeAdapter);
        binding.nodeTable.setNestedScrollingEnabled(false);
        binding.nodeTable.setItemAnimator(null);

        setUpControls();
        model.setRunListener(this);
        renderAll();
        if (stale) {
            snack(R.string.run_stale);
        }
    }

    @Override
    public void onStop() {
        // Leaving the screen pauses playback; rotating does not.
        if (!requireActivity().isChangingConfigurations()) {
            model.pausePlayback();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        if (openDialog != null && openDialog.isShowing()) {
            openDialog.dismiss();
        }
        openDialog = null;
        model.clearRunListener(this);
        model.removePhotoListener(photoChanged);
        binding.canvas.setListener(null);
        shownInsightFor = null;
        codeLines.clear();
        shownSession = null;
        shownPseudocodeFor = null;
        shownLine = -1;
        shownCounters = "";
        shownFrontier = "";
        binding = null;
        super.onDestroyView();
    }

    // ---- wiring ----------------------------------------------------------------------

    private void setUpControls() {
        binding.runAlgorithm.setOnClickListener(v -> chooseAlgorithm());
        binding.runSource.setOnClickListener(v -> {
            choosingTarget = false;
            snack(R.string.run_pick_start);
        });
        binding.runTarget.setOnClickListener(v -> {
            choosingTarget = true;
            snack(R.string.run_pick_end);
        });
        TextWatcher remember = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                rememberInputs();
                if (model.algorithm().inputKind() == InputKind.ARRAY && model.session() == null) {
                    renderPreview();
                }
            }
        };
        binding.inputText.addTextChangedListener(remember);
        binding.inputCapacity.addTextChangedListener(remember);
        binding.inputRandom.setOnClickListener(v -> fillRandomInput());
        binding.runGo.setOnClickListener(v -> onRunClicked());
        if (binding.runExportPdf != null) {   // a layout variant without the button must not crash the screen
        binding.runExportPdf.setOnClickListener(v -> {
            RunSession current = model.session();
            if (current == null) {
                snack(R.string.export_pdf_none);
                return;
            }
            com.graphlab.ui.common.PdfShare.share(requireActivity(),
                    com.graphlab.GraphLabApp.container(requireContext()).executors(), current);
        });
        }

        binding.btnFirst.setOnClickListener(v -> model.toFirst());
        binding.btnPrev.setOnClickListener(v -> model.stepPrevious());
        binding.btnPlay.setOnClickListener(v -> model.togglePlay());
        binding.btnNext.setOnClickListener(v -> model.stepNext());
        binding.btnLast.setOnClickListener(v -> model.toLast());

        binding.seek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                model.seek(Math.round(value));
            }
        });
        binding.speed.setValue((float) model.speed());
        binding.speed.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                model.setSpeed(value);
            }
            renderSpeed();
        });

        binding.panelToggle.check(panelButton(model.panel()));
        showPanel(model.panel());
        binding.panelToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int panel = checkedId == R.id.panel_state ? 1 : checkedId == R.id.panel_insight ? 2 : 0;
                model.setPanel(panel);
                showPanel(panel);
            }
        });
        setUpFolding();
    }

    // ---- landscape: the graph gets the screen, the rest folds away ------------------------

    /**
     * Landscape only (these views are not in the portrait layout). The playback tray (buttons, step and speed
     * sliders) and the side column (algorithm, Run, Steps / State / Insight) are folded over the graph and opened with
     * a button each. The side column starts open until the first run, because nothing can be run without it.
     */
    private void setUpFolding() {
        if (binding.runSide == null) {
            return;   // portrait
        }
        setSideOpen(model.session() == null);
        setTrayOpen(false);
        binding.runPanelToggle.setOnClickListener(v -> setSideOpen(binding.runSide.getVisibility() != View.VISIBLE));
        binding.runTrayToggle.setOnClickListener(v -> setTrayOpen(binding.runTray.getVisibility() != View.VISIBLE));
        // The message floats over the graph, so it must not leave an empty box when there is nothing to say.
        binding.currentMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                binding.currentMessage.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
        });
        binding.currentMessage.setVisibility(binding.currentMessage.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void setSideOpen(boolean open) {
        if (binding.runSide == null) {
            return;
        }
        binding.runSide.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.runPanelToggle.setContentDescription(getString(open ? R.string.run_panel_close : R.string.run_panel_open));
    }

    private void setTrayOpen(boolean open) {
        if (binding.runTray == null) {
            return;
        }
        binding.runTray.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.runTrayToggle.setContentDescription(getString(open ? R.string.run_tray_close : R.string.run_tray_open));
    }

    /** After Run: close the side column so the graph is clear, and open the tray so Play is within reach. */
    private void foldAfterRun() {
        setSideOpen(false);
        setTrayOpen(true);
    }

    private static int panelButton(int panel) {
        return panel == 1 ? R.id.panel_state : panel == 2 ? R.id.panel_insight : R.id.panel_steps;
    }

    private void showPanel(int panel) {
        binding.stepsList.setVisibility(panel == 0 ? View.VISIBLE : View.GONE);
        binding.stateContent.setVisibility(panel == 1 ? View.VISIBLE : View.GONE);
        binding.insightContent.setVisibility(panel == 2 ? View.VISIBLE : View.GONE);
        updateEmptyHint();
    }

    /** "Run an algorithm to see its steps here" covers only the empty Steps tab; the State tab shows pseudocode before a run. */
    private void updateEmptyHint() {
        boolean noRun = model.session() == null;
        binding.panelEmpty.setVisibility(noRun && model.panel() == 0 ? View.VISIBLE : View.GONE);
    }

    private void chooseAlgorithm() {
        List<Algorithm> all = Algorithms.all();
        String[] names = new String[all.size()];
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).category().title() + " \u00b7 " + all.get(i).name();
            if (all.get(i).id().equals(model.algorithm().id())) {
                checked = i;
            }
        }
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.run_choose_algorithm)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    model.setAlgorithm(all.get(which).id());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void onRunClicked() {
        Algorithm algorithm = model.algorithm();
        com.graphlab.data.Telemetry.event(requireContext(), "run_algorithm", "algorithm", algorithm.id());
        if (algorithm.inputKind() != InputKind.GRAPH) {
            rememberInputs();   // parsing happens in the ViewModel, which reports a clear message
            model.startRun();
            foldAfterRun();
            return;
        }
        if (model.editor().isEmpty()) {
            snack(R.string.run_empty_graph);
            return;
        }
        if (algorithm.needsSource() && model.editor().findNode(model.sourceNodeId()) == null) {
            snack(R.string.run_need_source);
            return;
        }
        if (algorithm.needsTarget() && (model.editor().findNode(model.targetNodeId()) == null
                || model.targetNodeId() == model.sourceNodeId())) {
            snack(R.string.run_end_needed);
            return;
        }
        model.startRun();
        foldAfterRun();
    }

    private void rememberInputs() {
        storeTypedText();
    }

    /** Typed text goes to the box that belongs to the current algorithm. */
    private void storeTypedText() {
        if (binding == null) {
            return;
        }
        InputKind kind = model.algorithm().inputKind();
        String typed = String.valueOf(binding.inputText.getText());
        if (kind == InputKind.ARRAY) {
            model.setInputTexts(typed, model.itemsText(), model.capacityText());
        } else if (kind == InputKind.KNAPSACK) {
            model.setInputTexts(model.arrayText(), typed, String.valueOf(binding.inputCapacity.getText()));
        } else if (kind == InputKind.COINS) {
            model.setCoinTexts(typed, String.valueOf(binding.inputCapacity.getText()));
        } else if (kind == InputKind.STRINGS) {
            model.setStringTexts(typed, String.valueOf(binding.inputCapacity.getText()));
        }
    }

    private void fillRandomInput() {
        long seed = System.nanoTime();
        InputKind randomKind = model.algorithm().inputKind();
        if (randomKind == InputKind.ARRAY) {
            binding.inputText.setText(InputParser.format(InputParser.randomArray(seed, 8)));
        } else if (randomKind == InputKind.COINS) {
            com.graphlab.engine.input.CoinInput c = InputParser.randomCoins(seed);
            binding.inputText.setText(InputParser.formatCoins(c));
            binding.inputCapacity.setText(Integer.toString(c.amount()));
        } else if (randomKind == InputKind.STRINGS) {
            com.graphlab.engine.input.StringsInput s = InputParser.randomStrings(seed);
            binding.inputText.setText(s.first());
            binding.inputCapacity.setText(s.second());
        } else {
            com.graphlab.engine.input.KnapsackInput k = InputParser.randomKnapsack(seed, 5);
            binding.inputText.setText(InputParser.formatItems(k));
            binding.inputCapacity.setText(Integer.toString(k.capacity()));
        }
        storeTypedText();
    }

    // ---- RunListener -----------------------------------------------------------------

    @Override
    public void onRunChanged() {
        if (binding == null) {
            return;
        }
        String error = model.consumeError();
        if (error != null) {
            Snackbar.make(binding.canvas, error, Snackbar.LENGTH_LONG).show();
        }
        renderAll();
    }

    @Override
    public void onPositionChanged() {
        if (binding != null) {
            renderPosition();
        }
    }

    @Override
    public void onPlayingChanged() {
        if (binding != null) {
            renderPlaying();
        }
    }

    // ---- GraphView.Listener (RUN mode only uses onNodeTapped) ------------------------

    @Override
    public void onNodeTapped(int nodeId) {
        if (model.algorithm().needsTarget() && choosingTarget) {
            choosingTarget = false;
            model.setTarget(nodeId);
            Node end = model.editor().findNode(nodeId);
            if (end != null && binding != null) {
                Snackbar.make(binding.canvas, getString(R.string.run_end_set, end.label()), Snackbar.LENGTH_SHORT).show();
            }
            return;
        }
        if (!model.algorithm().needsSource()) {
            snack(R.string.run_no_source_needed);
            return;
        }
        boolean hadRun = model.session() != null;
        model.setSource(nodeId);
        Node node = model.editor().findNode(nodeId);
        if (hadRun && node != null && binding != null) {
            Snackbar.make(binding.canvas, getString(R.string.run_source_set, node.label()), Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectRequested(int fromId, int toId) {
    }

    @Override
    public void onEdgeWeightRequested(int edgeId) {
    }

    @Override
    public void onMessage(@StringRes int message) {
    }

    @Override
    public void onGraphChanged() {
    }

    // ---- rendering -------------------------------------------------------------------

    private void renderAll() {
        renderSelection();
        renderRunning();
        renderSession();
        renderPosition();
        renderPlaying();
    }

    private void renderSelection() {
        Algorithm algorithm = model.algorithm();
        binding.runAlgorithm.setText(getString(R.string.run_algorithm_button, algorithm.name()));
        boolean needsSource = algorithm.needsSource();
        binding.runSource.setVisibility(needsSource ? View.VISIBLE : View.GONE);
        Node source = model.editor().findNode(model.sourceNodeId());
        binding.runSource.setText(source == null
                ? getString(R.string.run_source_none)
                : getString(R.string.run_source_button, source.label()));
        binding.canvas.setHighlightNode(needsSource && source != null ? source.id() : -1);
        boolean needsTarget = algorithm.needsTarget();
        binding.runTarget.setVisibility(needsTarget ? View.VISIBLE : View.GONE);
        Node target = model.editor().findNode(model.targetNodeId());
        binding.runTarget.setText(target == null
                ? getString(R.string.run_target_none)
                : getString(R.string.run_target_button, target.label()));
        binding.valuesTitle.setText(valuesTitle(algorithm.id()));
        buildPseudocode(algorithm);
        renderVisualChoice(algorithm);
        renderInputPanel(algorithm);
        renderInsight(algorithm);
    }

    /** Shows the canvas, the bars or the table, whichever this algorithm draws. */
    private void renderVisualChoice(Algorithm algorithm) {
        Visual visual = algorithm.visual();
        binding.canvas.setVisibility(visual == Visual.GRAPH ? View.VISIBLE : View.GONE);
        binding.arrayView.setVisibility(visual == Visual.ARRAY ? View.VISIBLE : View.GONE);
        binding.gridView.setVisibility(visual == Visual.GRID ? View.VISIBLE : View.GONE);
        boolean graphLists = visual == Visual.GRAPH;
        binding.valuesTitle.setVisibility(graphLists ? View.VISIBLE : View.GONE);
        binding.nodeTable.setVisibility(graphLists ? View.VISIBLE : View.GONE);
        binding.frontierTitle.setVisibility(graphLists ? View.VISIBLE : View.GONE);
        binding.frontierChips.setVisibility(graphLists ? View.VISIBLE : View.GONE);
    }

    /** The number or item boxes, for the algorithms that do not work on the canvas graph. */
    private void renderInputPanel(Algorithm algorithm) {
        InputKind kind = algorithm.inputKind();
        binding.inputPanel.setVisibility(kind == InputKind.GRAPH ? View.GONE : View.VISIBLE);
        boolean second = kind == InputKind.KNAPSACK || kind == InputKind.COINS || kind == InputKind.STRINGS;
        binding.capacityLayout.setVisibility(second ? View.VISIBLE : View.GONE);
        if (kind == InputKind.GRAPH) {
            return;
        }
        int firstHint;
        String wanted;
        switch (kind) {
            case ARRAY:
                firstHint = algorithm.id().equals("lis") ? R.string.input_sequence_hint : R.string.input_numbers_hint;
                wanted = model.arrayText();
                break;
            case COINS:
                firstHint = R.string.input_coins_hint;
                wanted = model.coinsText();
                break;
            case STRINGS:
                firstHint = R.string.input_first_text_hint;
                wanted = model.firstText();
                break;
            default:
                firstHint = R.string.input_items_hint;
                wanted = model.itemsText();
                break;
        }
        binding.inputLayout.setHint(getString(firstHint));
        if (!wanted.contentEquals(binding.inputText.getText())) {
            binding.inputText.setText(wanted);
        }
        if (second) {
            int secondHint = kind == InputKind.COINS ? R.string.input_amount_hint
                    : kind == InputKind.STRINGS ? R.string.input_second_text_hint : R.string.input_capacity_hint;
            binding.capacityLayout.setHint(getString(secondHint));
            binding.inputCapacity.setInputType(kind == InputKind.STRINGS
                    ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    : android.text.InputType.TYPE_CLASS_NUMBER);
            String secondText = kind == InputKind.COINS ? model.amountText()
                    : kind == InputKind.STRINGS ? model.secondText() : model.capacityText();
            if (!secondText.contentEquals(binding.inputCapacity.getText())) {
                binding.inputCapacity.setText(secondText);
            }
        }
    }

    /** The teaching page for the algorithm, read from assets/insight/{id}.json. */
    private void renderInsight(Algorithm algorithm) {
        if (algorithm.id().equals(shownInsightFor)) {
            return;
        }
        shownInsightFor = algorithm.id();
        binding.insightContent.removeAllViews();
        Insight insight = null;
        try (java.io.InputStream in = requireContext().getAssets().open("insight/" + algorithm.id() + ".json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            insight = Insight.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (java.io.IOException | com.graphlab.engine.io.GraphFormatException e) {
            insight = null;
        }
        if (insight == null) {
            addInsightText(getString(R.string.insight_missing), false);
            return;
        }
        addInsightText(getString(R.string.insight_category, algorithm.category().title()), false);
        addInsightText(getString(R.string.insight_idea), true);
        addInsightText(insight.idea(), false);
        addInsightText(getString(R.string.insight_cost), true);
        addInsightText(getString(R.string.insight_time, insight.time()), false);
        addInsightText(getString(R.string.insight_space, insight.space()), false);
        addInsightText(getString(R.string.insight_use), true);
        for (String line : insight.useWhen()) {
            addInsightText("\u2022 " + line, false);
        }
        addInsightText(getString(R.string.insight_avoid), true);
        for (String line : insight.avoidWhen()) {
            addInsightText("\u2022 " + line, false);
        }
    }

    private void addInsightText(String text, boolean heading) {
        TextView view = new TextView(requireContext(), null, 0, heading
                ? R.style.TextAppearance_GraphLab_Section : R.style.TextAppearance_GraphLab_Body);
        view.setTextAppearance(heading ? R.style.TextAppearance_GraphLab_Section : R.style.TextAppearance_GraphLab_Body);
        view.setText(text);
        int pad = Math.round(getResources().getDisplayMetrics().density * (heading ? 10 : 3));
        view.setPadding(0, pad, 0, 0);
        binding.insightContent.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Before a run: the typed numbers as idle bars, so the list can be seen. */
    private void renderPreview() {
        if (binding == null || model.algorithm().visual() != Visual.ARRAY || model.algorithm().inputKind() != InputKind.ARRAY) {
            return;
        }
        try {
            ArrayInput input = InputParser.parseArray(model.arrayText());
            binding.arrayView.setFrame(ArrayFrame.builder(input.values()).build());
        } catch (IllegalArgumentException e) {
            binding.arrayView.setFrame(null);
        }
    }

    private void renderRunning() {
        boolean running = model.isRunning();
        binding.runProgress.setVisibility(running ? View.VISIBLE : View.GONE);
        binding.runGo.setEnabled(!running);
    }

    /** Things that change only when a run starts or is dropped: the log, the seek range, enabled controls. */
    private void renderSession() {
        RunSession session = model.session();
        boolean has = session != null;
        if (binding.runExportPdf != null) {
            binding.runExportPdf.setVisibility(has ? View.VISIBLE : View.GONE);
        }
        binding.btnFirst.setEnabled(has);
        binding.btnPrev.setEnabled(has);
        binding.btnPlay.setEnabled(has);
        binding.btnNext.setEnabled(has);
        binding.btnLast.setEnabled(has);
        binding.speed.setEnabled(has);
        binding.seek.setEnabled(has && session.trace.size() > 1);
        updateEmptyHint();

        if (session == shownSession) {
            return;
        }
        shownSession = session;
        shownCounters = "";
        shownFrontier = "";
        shownPanels = "";
        if (has) {
            int last = Math.max(1, session.trace.size() - 1);
            binding.seek.setValue(0f);
            binding.seek.setValueTo(last);
            stepAdapter.submitList(StepLogAdapter.rowsOf(session.trace), this::scrollLogToCurrent);
        } else {
            binding.seek.setValue(0f);
            binding.seek.setValueTo(1f);
            stepAdapter.submitList(Collections.emptyList());
            binding.countersChips.removeAllViews();
            binding.frontierChips.removeAllViews();
            binding.panelsContainer.removeAllViews();
            nodeAdapter.submitList(Collections.emptyList());
        }
    }

    /** Things that change every step: the canvas, the message, the highlighted rows, the tables. */
    private void renderPosition() {
        RunSession session = model.session();
        if (session == null) {
            binding.canvas.setFrame(null);
            binding.gridView.setFrame(null);
            binding.arrayView.setFrame(null);
            renderPreview();
            binding.currentMessage.setText(model.algorithm().inputKind() != InputKind.GRAPH ? R.string.run_hint_input
                    : model.editor().isEmpty() ? R.string.run_hint_empty : R.string.run_hint_idle);
            binding.stepLabel.setText(R.string.step_none);
            binding.canvas.setContentDescription(getString(R.string.canvas_run_idle_description));
            highlightLine(-1);
            return;
        }
        int index = session.playback.index();
        int total = session.trace.size();
        Step step = session.trace.step(index);
        String description = getString(R.string.canvas_run_description, index + 1, total, step.message());
        binding.currentMessage.setText(getString(R.string.step_message, index + 1, step.message()));
        binding.stepLabel.setText(getString(R.string.step_label, index + 1, total));
        binding.seek.setValue((float) index);
        stepAdapter.setCurrent(index);
        scrollLogToCurrent();
        highlightLine(step.line());

        if (step.frame() instanceof GraphFrame) {
            GraphFrame frame = (GraphFrame) step.frame();
            binding.canvas.setFrame(FrameSnapshot.of(session.graph, frame));
            binding.canvas.setContentDescription(description);
            List<NodeRow> rows = StepSummary.nodeRows(session.graph, frame);
            nodeAdapter.submitList(rows);
            renderChips(step, rows);
        } else {
            if (step.frame() instanceof ArrayFrame) {
                binding.arrayView.setFrame((ArrayFrame) step.frame());
                binding.arrayView.setContentDescription(description);
            } else if (step.frame() instanceof GridFrame) {
                binding.gridView.setFrame((GridFrame) step.frame());
                binding.gridView.setContentDescription(description);
            }
            renderChips(step, java.util.Collections.<NodeRow>emptyList());   // counters only
        }
    }

    private void renderPlaying() {
        RunSession session = model.session();
        boolean playing = session != null && session.playback.isPlaying();
        if (wasPlaying && !playing && session != null && session.playback.atEnd() && sounds != null) {
            sounds.playDone();   // playback ran to the last step
        }
        wasPlaying = playing;
        binding.btnPlay.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.btnPlay.setContentDescription(getString(playing ? R.string.playback_pause : R.string.playback_play));
        renderSpeed();
    }

    private void renderSpeed() {
        float speed = (float) model.speed();
        if (binding.speed.getValue() != speed) {
            binding.speed.setValue(speed);
        }
        binding.speedLabel.setText(getString(R.string.speed_label, speed));
    }

    private void renderChips(Step step, List<NodeRow> rows) {
        renderPanels(step);
        List<String> counters = StepSummary.counters(step);
        String countersKey = TextUtils.join("|", counters);
        if (!countersKey.equals(shownCounters)) {
            shownCounters = countersKey;
            binding.countersChips.removeAllViews();
            for (String text : counters) {
                binding.countersChips.addView(makeChip(text, stateColors[State.IDLE.ordinal()]));
            }
        }
        List<NodeRow> waiting = StepSummary.frontier(rows);
        List<String> texts = new ArrayList<>(waiting.size());
        for (NodeRow row : waiting) {
            texts.add(StepSummary.chipText(row));
        }
        String frontierKey = TextUtils.join("|", texts);
        if (!frontierKey.equals(shownFrontier)) {
            shownFrontier = frontierKey;
            binding.frontierChips.removeAllViews();
            if (texts.isEmpty()) {
                binding.frontierChips.addView(makeChip(getString(R.string.frontier_empty), stateColors[State.IDLE.ordinal()]));
            }
            for (String text : texts) {
                binding.frontierChips.addView(makeChip(text, stateColors[State.FRONTIER.ordinal()]));
            }
        }
    }

    /** Draws the tables the algorithm put in the frame (queue, stack, union-find parents), one titled row of chips each. */
    private void renderPanels(Step step) {
        List<Panel> panels = step.frame() instanceof GraphFrame ? ((GraphFrame) step.frame()).panels() : Collections.<Panel>emptyList();
        StringBuilder key = new StringBuilder();
        for (Panel panel : panels) {
            key.append(panel.title()).append('=').append(TextUtils.join(",", panel.items())).append('|');
        }
        if (key.toString().equals(shownPanels)) {
            return;
        }
        shownPanels = key.toString();
        binding.panelsContainer.removeAllViews();
        for (Panel panel : panels) {
            TextView title = new TextView(requireContext());
            title.setTextAppearance(R.style.TextAppearance_GraphLab_Section);
            title.setText(panel.title());
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = getResources().getDimensionPixelSize(R.dimen.gap_small);
            binding.panelsContainer.addView(title, titleParams);

            ChipGroup group = new ChipGroup(requireContext());
            if (panel.items().isEmpty()) {
                group.addView(makeChip(getString(R.string.panel_empty_chip), stateColors[State.IDLE.ordinal()]));
            }
            for (String item : panel.items()) {
                group.addView(makeChip(item, stateColors[State.FRONTIER.ordinal()]));
            }
            binding.panelsContainer.addView(group);
        }
    }

    @NonNull
    private Chip makeChip(@NonNull String text, int strokeColor) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setFocusable(false);
        chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor));
        chip.setChipStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        return chip;
    }

    // ---- pseudocode ------------------------------------------------------------------

    private void buildPseudocode(@NonNull Algorithm algorithm) {
        if (algorithm.id().equals(shownPseudocodeFor)) {
            return;
        }
        shownPseudocodeFor = algorithm.id();
        shownLine = -1;
        codeLines.clear();
        binding.pseudocodeContainer.removeAllViews();
        List<String> lines = algorithm.pseudocode();
        for (int i = 0; i < lines.size(); i++) {
            TextView view = new TextView(requireContext(), null, 0, R.style.Widget_GraphLab_CodeLine);
            view.setText(String.format(Locale.ROOT, "%2d  %s", i + 1, lines.get(i)));
            binding.pseudocodeContainer.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            codeLines.add(view);
        }
    }

    /** Marks pseudocode line {@code line} (counting from 1) as the current one; -1 clears it. */
    private void highlightLine(int line) {
        if (line == shownLine) {
            return;
        }
        shownLine = line;
        for (int i = 0; i < codeLines.size(); i++) {
            codeLines.get(i).setSelected(i + 1 == line);
        }
    }

    // ---- small helpers ---------------------------------------------------------------

    private void scrollLogToCurrent() {
        RunSession session = model.session();
        if (binding == null || session == null || stepAdapter.getItemCount() == 0) {
            return;
        }
        int index = Math.min(session.playback.index(), stepAdapter.getItemCount() - 1);
        LinearLayoutManager manager = (LinearLayoutManager) binding.stepsList.getLayoutManager();
        if (manager != null) {
            // Keep the current row a little below the top so the steps before it stay visible.
            manager.scrollToPositionWithOffset(index, binding.stepsList.getHeight() / 3);
        }
    }

    @StringRes
    private static int valuesTitle(@NonNull String algorithmId) {
        if (algorithmId.equals(Dijkstra.ID) || algorithmId.equals(BellmanFord.ID)) {
            return R.string.values_distance;
        }
        if (algorithmId.equals(Bfs.ID)) {
            return R.string.values_hops;
        }
        if (algorithmId.equals(Dfs.ID)) {
            return R.string.values_time;
        }
        if (algorithmId.equals(Prim.ID)) {
            return R.string.values_link;
        }
        if (algorithmId.equals(GraphColouring.ID)) {
            return R.string.values_colour;
        }
        return R.string.values_generic;
    }

    private void snack(@StringRes int message) {
        if (binding != null) {
            Snackbar.make(binding.canvas, message, Snackbar.LENGTH_SHORT).show();
        }
    }
}

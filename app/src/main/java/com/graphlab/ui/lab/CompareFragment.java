package com.graphlab.ui.lab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.databinding.FragmentCompareBinding;
import com.graphlab.engine.compare.Comparison;
import com.graphlab.engine.compare.Race;
import com.graphlab.engine.edit.Viewport;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.play.FrameSnapshot;
import com.graphlab.engine.play.Playback;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.Step;
import com.graphlab.ui.common.GraphView;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.trace.Algorithm;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lab, Compare mode (MS10): choose two algorithms, run both on the graph in the editor from the same start node, and read
 * their numbers side by side (steps, nodes reached, tree weight, their own counters). Two algorithms of one family also get
 * a verdict on whether their answers match. If one refuses the graph (Dijkstra with a negative edge) the reason is shown
 * and the other still runs. "Open in Run" replays either one step by step. The choice and the answer live in
 * {@link LabViewModel}, so rotation keeps them; editing the graph removes the answer, since it no longer fits.
 */
public class CompareFragment extends Fragment {

    private FragmentCompareBinding binding;
    private LabViewModel model;
    private GraphEditor editor;
    private List<Algorithm> candidates;
    private boolean updatingUi;
    /** Own view windows for the race canvases, so panning one does not move the editor's. */
    private final Viewport viewportA = new Viewport();
    private final Viewport viewportB = new Viewport();
    private boolean raceBound;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCompareBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        editor = model.editor();
        candidates = Comparison.comparable();   // only algorithms whose answers can be judged against each other
        binding.compareDynamic.setOnClickListener(v -> openDynamic());

        List<String> names = new ArrayList<>();
        for (Algorithm algorithm : candidates) {
            names.add(algorithm.name());
        }
        setAdapter(binding.compareSpinnerA, names);
        setAdapter(binding.compareSpinnerB, names);
        AdapterView.OnItemSelectedListener choiceListener = new SimpleListener(() -> {
            if (!updatingUi) {
                model.setCompareChoice(idAt(binding.compareSpinnerA), idAt(binding.compareSpinnerB));
                renderResults();
            }
        });
        binding.compareSpinnerA.setOnItemSelectedListener(choiceListener);
        binding.compareSpinnerB.setOnItemSelectedListener(choiceListener);
        binding.compareSpinnerSource.setOnItemSelectedListener(new SimpleListener(() -> {
            if (!updatingUi && editor.nodeCount() > 0) {
                int at = binding.compareSpinnerSource.getSelectedItemPosition();
                if (at >= 0 && at < editor.nodeCount()) {
                    model.setSource(editor.nodeAt(at).id());
                }
            }
        }));
        binding.compareRun.setOnClickListener(v -> onCompare());
        binding.sideAOpen.setOnClickListener(v -> openInRun(model.compareA()));
        binding.sideBOpen.setOnClickListener(v -> openInRun(model.compareB()));

        binding.raceFirst.setOnClickListener(v -> model.raceStep(0));
        binding.racePrev.setOnClickListener(v -> model.raceStep(-1));
        binding.racePlay.setOnClickListener(v -> model.raceTogglePlay());
        binding.raceNext.setOnClickListener(v -> model.raceStep(1));
        binding.raceSeek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                model.raceSeek(Math.round(value));
            }
        });
        binding.raceCanvasA.setMode(GraphView.Mode.RUN);
        binding.raceCanvasB.setMode(GraphView.Mode.RUN);
        model.setRaceListener(this::renderRace);
        render();
    }

    @Override
    public void onStop() {
        model.racePause();   // a race must not keep ticking while the screen is hidden
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        model.setRaceListener(null);
        binding.raceCanvasA.setListener(null);
        binding.raceCanvasB.setListener(null);
        binding = null;
        super.onDestroyView();
    }

    // ---- drawing ---------------------------------------------------------------------

    private void render() {
        boolean empty = editor.nodeCount() == 0;
        binding.compareGraphInfo.setText(getString(R.string.compare_mode_graph,
                model.graphName().isEmpty() ? getString(R.string.untitled_hint) : model.graphName(),
                getResources().getQuantityString(R.plurals.nodes, editor.nodeCount(), editor.nodeCount()),
                getResources().getQuantityString(R.plurals.edges, editor.edgeCount(), editor.edgeCount())));
        binding.compareEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.compareForm.setVisibility(empty ? View.GONE : View.VISIBLE);

        updatingUi = true;
        binding.compareSpinnerA.setSelection(indexOf(model.compareA()));
        binding.compareSpinnerB.setSelection(indexOf(model.compareB()));
        List<String> nodeNames = new ArrayList<>();
        for (int i = 0; i < editor.nodeCount(); i++) {
            nodeNames.add(editor.nodeAt(i).label());
        }
        setAdapter(binding.compareSpinnerSource, nodeNames);
        int sourceIndex = model.sourceNodeId() >= 0 ? editor.indexOfNode(model.sourceNodeId()) : 0;
        if (nodeNames.size() > 0) {
            binding.compareSpinnerSource.setSelection(Math.max(0, sourceIndex));
        }
        updatingUi = false;
        renderResults();
    }

    private void renderResults() {
        Comparison.Report report = model.compareReport();
        binding.compareResults.setVisibility(report == null ? View.GONE : View.VISIBLE);
        if (report == null) {
            return;
        }
        binding.sideATitle.setText(report.a().name());
        binding.sideBTitle.setText(report.b().name());
        binding.sideAText.setText(describe(report.a()));
        binding.sideBText.setText(describe(report.b()));
        binding.sideAOpen.setEnabled(report.a().ran());
        binding.sideBOpen.setEnabled(report.b().ran());

        switch (report.agreement()) {
            case SAME_ANSWER:
                binding.compareVerdict.setText(R.string.compare_same);
                break;
            case DIFFERENT_ANSWER:
                binding.compareVerdict.setText(R.string.compare_different);
                break;
            default:
                binding.compareVerdict.setText(report.a().ran() && report.b().ran()
                        ? R.string.compare_not_comparable : R.string.compare_one_failed);
                break;
        }
        Comparison.Side faster = report.fewerSteps();
        binding.compareVerdictDetail.setText(faster == null
                ? getString(R.string.compare_steps_tie)
                : getString(R.string.compare_fewer_steps, faster.name(), Math.min(report.a().steps(), report.b().steps()),
                        Math.max(report.a().steps(), report.b().steps())));
        raceBound = false;
        renderRace();
    }

    /** Draws the race at the current step: both canvases, their counters, the play button and the slider. */
    private void renderRace() {
        if (binding == null) {
            return;
        }
        Race race = model.race();
        Playback playback = model.racePlayback();
        Graph graph = model.raceGraph();
        if (race == null || playback == null || graph == null) {
            return;
        }
        if (!raceBound) {
            raceBound = true;
            binding.raceCanvasA.bind(model.editor(), viewportA);
            binding.raceCanvasB.bind(model.editor(), viewportB);
            viewportA.requestFit();   // fits when the canvas has its size, so it never fits to a 0-pixel view
            viewportB.requestFit();
            if (binding.raceCanvasA.getWidth() > 0) {
                binding.raceCanvasA.fitToGraph();
            }
            if (binding.raceCanvasB.getWidth() > 0) {
                binding.raceCanvasB.fitToGraph();
            }
            binding.raceSeek.setValue(0f);
            binding.raceSeek.setValueTo(Math.max(1, race.length() - 1));
        }
        int index = playback.index();
        Step a = race.stepA(index);
        Step b = race.stepB(index);
        binding.raceCanvasA.setFrame(a != null && a.frame() instanceof GraphFrame
                ? FrameSnapshot.of(graph, (GraphFrame) a.frame()) : null);
        binding.raceCanvasB.setFrame(b != null && b.frame() instanceof GraphFrame
                ? FrameSnapshot.of(graph, (GraphFrame) b.frame()) : null);
        Comparison.Report report = model.compareReport();
        binding.raceCountersA.setText(raceText(report.a(), a, index, race.length(), race.doneA(index)));
        binding.raceCountersB.setText(raceText(report.b(), b, index, race.length(), race.doneB(index)));

        float seek = Math.min(index, binding.raceSeek.getValueTo());
        if (binding.raceSeek.getValue() != seek) {
            binding.raceSeek.setValue(seek);
        }
        binding.raceStepLabel.setText(getString(R.string.race_step_label, index, race.length() - 1));
        boolean playing = playback.isPlaying();
        binding.racePlay.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.racePlay.setContentDescription(getString(playing ? R.string.playback_pause : R.string.playback_play));
    }

    private String raceText(Comparison.Side side, Step step, int index, int length, boolean done) {
        if (step == null) {
            return getString(R.string.race_not_run);
        }
        StringBuilder out = new StringBuilder();
        out.append(getString(R.string.race_counters, Math.min(index, side.steps() - 1), side.steps() - 1,
                done ? getString(R.string.race_done) : ""));
        for (Map.Entry<String, Long> counter : step.counters().entrySet()) {
            out.append('\n').append(counter.getKey()).append(": ").append(counter.getValue());
        }
        out.append('\n').append(getString(R.string.race_time, Race.formatNanos(side.nanos())));
        return out.toString();
    }

    /** One short block of numbers (or the reason it could not run) for a card. */
    private String describe(Comparison.Side side) {
        if (!side.ran()) {
            return getString(R.string.compare_side_failed, side.error());
        }
        StringBuilder out = new StringBuilder();
        out.append(getString(R.string.compare_side_steps, side.steps()));
        if (side.reached() > 0) {
            out.append('\n').append(getString(R.string.compare_side_reached, side.reached()));
        }
        if (side.treeEdges() > 0) {
            out.append('\n').append(getString(R.string.compare_side_tree, side.treeEdges(), trim(side.totalWeight())));
        }
        for (Map.Entry<String, Long> counter : side.counters().entrySet()) {
            out.append('\n').append(counter.getKey()).append(": ").append(counter.getValue());
        }
        return out.toString();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    // ---- actions ---------------------------------------------------------------------

    private void onCompare() {
        if (editor.nodeCount() == 0) {
            return;
        }
        String a = idAt(binding.compareSpinnerA);
        String b = idAt(binding.compareSpinnerB);
        if (a.equals(b)) {
            Snackbar.make(binding.getRoot(), R.string.compare_same_choice, Snackbar.LENGTH_SHORT).show();
            return;
        }
        int at = Math.max(0, binding.compareSpinnerSource.getSelectedItemPosition());
        int source = editor.nodeAt(Math.min(at, editor.nodeCount() - 1)).id();
        model.setSource(source);
        model.setCompareChoice(a, b);
        try {
            model.runComparison(source);
        } catch (IllegalArgumentException e) {
            Snackbar.make(binding.getRoot(), e.getMessage() == null ? getString(R.string.compare_failed_generic) : e.getMessage(),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        renderResults();
    }

    private void openInRun(@NonNull String algorithmId) {
        model.setAlgorithm(algorithmId);
        if (editor.nodeCount() > 0 && model.sourceNodeId() < 0) {
            model.setSource(editor.nodeAt(0).id());
        }
        ((LabHost) requireActivity()).showMode(LabMode.RUN);
    }

    // ---- helpers ---------------------------------------------------------------------

    /** Break It and Dynamic graph read the graph from the library, so it must be saved first. */
    private void openDynamic() {
        model.saveIfDirty();
        long id = model.graphId();
        if (id == 0) {
            Snackbar.make(binding.getRoot(), R.string.need_saved_graph, Snackbar.LENGTH_LONG).show();
            return;
        }
        startActivity(DynamicGraphActivity.intent(requireContext(), id));
    }

    private void setAdapter(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String idAt(Spinner spinner) {
        int at = Math.max(0, Math.min(candidates.size() - 1, spinner.getSelectedItemPosition()));
        return candidates.get(at).id();
    }

    private int indexOf(String id) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** An item-selected listener that only needs to know that something was chosen. */
    private static final class SimpleListener implements AdapterView.OnItemSelectedListener {
        private final Runnable action;

        SimpleListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            action.run();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}

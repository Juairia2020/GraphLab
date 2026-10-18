package com.graphlab.ui.lab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.ExperimentRunRecord;
import com.graphlab.data.db.GraphSummary;
import com.graphlab.databinding.FragmentExperimentBinding;
import com.graphlab.engine.experiment.ExperimentAlgorithm;
import com.graphlab.engine.experiment.ExperimentRecord;
import com.graphlab.engine.experiment.Histogram;
import com.graphlab.engine.experiment.TrialCount;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.receiver.ConnectivityReceiver;
import com.graphlab.service.ExperimentMonitor;
import com.graphlab.service.ExperimentService;
import com.graphlab.ui.compare.CompareRunsActivity;
import com.graphlab.work.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lab, Experiment mode (MS8). Choose a saved undirected graph and a number of trials and press Start: the work is
 * done by {@link ExperimentService}, so it carries on when the app is in the background. This screen only
 * <em>shows</em> what {@link ExperimentMonitor} reports (progress, pause, the histogram so far) and sends Pause, Resume and
 * Cancel; it holds no experiment state of its own, so rotating the phone or coming back later shows the same run.
 *
 * <p>A runtime {@link ConnectivityReceiver} keeps the "online/offline" line current while the screen is visible.
 */
public class ExperimentFragment extends Fragment {
    private static final String KEY_GRAPH = "graph";
    private static final String KEY_ALGORITHM = "algorithm";
    private static final String KEY_SLOW = "slow";
    /** Slow motion adds this wait after every trial, so a short experiment can be watched and left running in the background. */
    private static final long SLOW_MS = 20;

    private FragmentExperimentBinding binding;
    private AppContainer container;
    private LabViewModel model;
    private ExperimentMonitor monitor;
    private final List<GraphSummary> choices = new ArrayList<>();
    private long selectedGraphId;
    private ExperimentAlgorithm algorithm = ExperimentAlgorithm.KARGER;
    @Nullable
    private androidx.appcompat.app.AlertDialog openDialog;
    private long shownRunId = -1;
    @Nullable
    private String shownError;
    private boolean online = true;
    private ConnectivityReceiver connectivityReceiver;
    private final ExperimentMonitor.Listener listener = this::render;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> beginRun());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        binding = FragmentExperimentBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        container = GraphLabApp.container(requireContext());
        monitor = container.monitor();
        model = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        model.attach(container);
        selectedGraphId = savedInstanceState != null ? savedInstanceState.getLong(KEY_GRAPH, 0) : model.graphId();
        if (savedInstanceState != null) {
            binding.experimentSlow.setChecked(savedInstanceState.getBoolean(KEY_SLOW));
            algorithm = ExperimentAlgorithm.byId(savedInstanceState.getString(KEY_ALGORITHM, algorithm.id()));
        }
        setUpAlgorithmChoice();
        if (binding.experimentTrials.getText() == null || binding.experimentTrials.getText().length() == 0) {
            binding.experimentTrials.setText(String.valueOf(TrialCount.DEFAULT));
        }

        binding.experimentStart.setOnClickListener(v -> onStartPressed());
        binding.experimentBenchmark.setOnClickListener(v -> startActivity(new android.content.Intent(requireContext(), BenchmarkActivity.class)));
        binding.experimentRandom.setOnClickListener(v -> openDialog = RandomGraphDialog.show(requireContext(), this::onRandomGraph));
        binding.experimentPause.setOnClickListener(v -> {
            if (monitor.current().state == ExperimentMonitor.State.PAUSED) {
                ExperimentService.resume(requireContext());
            } else {
                ExperimentService.pause(requireContext());
            }
        });
        binding.experimentCancel.setOnClickListener(v -> ExperimentService.cancel(requireContext()));
        binding.experimentCompare.setOnClickListener(v -> startActivity(new Intent(requireContext(), CompareRunsActivity.class)));
        binding.experimentGraph.setOnItemClickListener((parent, item, position, id) -> {
            if (position >= 0 && position < choices.size()) {
                selectedGraphId = choices.get(position).id;
            }
        });
        connectivityReceiver = new ConnectivityReceiver(isOnline -> {
            online = isOnline;
            showNetwork();
        });
        loadChoices();
    }

    @Override
    public void onStart() {
        super.onStart();
        monitor.addListener(listener);
        connectivityReceiver.register(requireContext());
        render(monitor.current());   // the experiment may have moved on, or finished, while we were away
        loadLatest();
    }

    @Override
    public void onStop() {
        monitor.removeListener(listener);
        connectivityReceiver.unregister(requireContext());
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_GRAPH, selectedGraphId);
        outState.putString(KEY_ALGORITHM, algorithm.id());
        if (binding != null) {
            outState.putBoolean(KEY_SLOW, binding.experimentSlow.isChecked());
        }
    }

    @Override
    public void onDestroyView() {
        if (openDialog != null) {
            openDialog.dismiss();   // a dialog must not outlive its fragment
            openDialog = null;
        }
        binding = null;
        super.onDestroyView();
    }

    // ---- choosing the algorithm and the graph --------------------------------------------

    private void setUpAlgorithmChoice() {
        ExperimentAlgorithm[] all = ExperimentAlgorithm.values();
        List<String> titles = new ArrayList<>();
        for (ExperimentAlgorithm a : all) {
            titles.add(a.title());
        }
        binding.experimentAlgorithm.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, titles));
        binding.experimentAlgorithm.setOnItemClickListener((parent, item, position, id) -> {
            if (position >= 0 && position < all.length) {
                algorithm = all[position];
                showAlgorithm();
            }
        });
        showAlgorithm();
    }

    /** Shows the chosen algorithm: its lesson, its chart heading, and the graph choice only when it needs one. */
    private void showAlgorithm() {
        if (binding == null) {
            return;
        }
        binding.experimentAlgorithm.setText(algorithm.title(), false);
        binding.experimentLesson.setText(algorithm.lesson());
        binding.experimentChartTitle.setText(algorithm.chartTitle());
        int graphVisibility = algorithm.needsGraph() ? View.VISIBLE : View.GONE;
        binding.experimentGraphLayout.setVisibility(graphVisibility);
        binding.experimentRandom.setVisibility(graphVisibility);
    }

    // ---- choosing the graph ------------------------------------------------------------

    /** Lists the saved graphs Karger can use: undirected with at least two nodes. Reads the library off the main thread. */
    private void loadChoices() {
        container.executors().disk(() -> {
            List<GraphSummary> usable = new ArrayList<>();
            for (GraphSummary summary : container.repository().listGraphs()) {
                try {
                    if (summary.nodeCount >= 2 && !GraphJson.read(summary.json).graph().isDirected()) {
                        usable.add(summary);
                    }
                } catch (Exception unreadable) {
                    // A graph that cannot be read cannot be run either; leave it out of the list.
                }
            }
            return usable;
        }, new AppExecutors.Callback<List<GraphSummary>>() {
            @Override
            public void onSuccess(List<GraphSummary> usable) {
                if (binding == null) {
                    return;
                }
                choices.clear();
                choices.addAll(usable);
                List<String> names = new ArrayList<>();
                int selectedIndex = -1;
                for (int i = 0; i < choices.size(); i++) {
                    names.add(choices.get(i).name);
                    if (choices.get(i).id == selectedGraphId) {
                        selectedIndex = i;
                    }
                }
                binding.experimentGraph.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
                if (selectedIndex < 0 && !choices.isEmpty()) {
                    selectedIndex = 0;
                    selectedGraphId = choices.get(0).id;
                }
                binding.experimentGraph.setText(selectedIndex >= 0 ? names.get(selectedIndex) : "", false);
                binding.experimentGraphLayout.setError(choices.isEmpty() ? getString(R.string.experiment_no_graphs) : null);
                render(monitor.current());
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.graphs_load_failed));
            }
        });
    }

    /** A random graph from the dialog is saved in the library under a free name, and chosen for the experiment. */
    private void onRandomGraph(@NonNull GraphDocument document) {
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            String name = GraphNames.unique(document.name(), repository.graphNames());
            GraphDocument named = document.withName(name);
            long id = repository.insertGraph(name, GraphJson.write(named), document.graph().nodeCount(),
                    document.graph().edgeCount(), System.currentTimeMillis());
            return new long[] {id};
        }, new AppExecutors.Callback<long[]>() {
            @Override
            public void onSuccess(long[] result) {
                if (binding == null) {
                    return;
                }
                selectedGraphId = result[0];
                snack(getString(R.string.experiment_random_saved, document.name()));
                loadChoices();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                snack(getString(R.string.experiment_random_failed));
            }
        });
    }

    // ---- starting ----------------------------------------------------------------------

    private void onStartPressed() {
        if (monitor.current().active()) {
            snack(getString(R.string.experiment_already_running));
            return;
        }
        if (algorithm.needsGraph() && (selectedGraphId == 0 || choices.isEmpty())) {
            binding.experimentGraphLayout.setError(getString(R.string.experiment_no_graphs));
            return;
        }
        try {
            TrialCount.parse(String.valueOf(binding.experimentTrials.getText()));
            binding.experimentTrialsLayout.setError(null);
        } catch (IllegalArgumentException invalid) {
            binding.experimentTrialsLayout.setError(invalid.getMessage());
            return;
        }
        // The service's notification is its main screen. Ask for the permission now (Android 13 and newer); the run
        // starts either way, because a foreground service works without it, only its notification is hidden.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            beginRun();
        }
    }

    private void beginRun() {
        if (binding == null || monitor.current().active()) {
            return;
        }
        int trials;
        try {
            trials = TrialCount.parse(String.valueOf(binding.experimentTrials.getText()));
        } catch (IllegalArgumentException invalid) {
            binding.experimentTrialsLayout.setError(invalid.getMessage());
            return;
        }
        long seed = new Random().nextInt(1_000_000_000);
        ExperimentService.start(requireContext(), algorithm.id(), selectedGraphId, trials, seed, binding.experimentSlow.isChecked() ? SLOW_MS : 0);
        shownRunId = monitor.current().lastRunId;   // a new result will have a different id
        showNotificationNotice();
    }

    // ---- showing the experiment ----------------------------------------------------------

    private void render(@NonNull ExperimentMonitor.Snapshot snapshot) {
        if (binding == null) {
            return;
        }
        boolean active = snapshot.active();
        binding.experimentProgressGroup.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.experimentStart.setEnabled(!active && (!algorithm.needsGraph() || !choices.isEmpty()));
        binding.experimentAlgorithm.setEnabled(!active);
        binding.experimentAlgorithmLayout.setEnabled(!active);
        binding.experimentGraph.setEnabled(!active);
        binding.experimentGraphLayout.setEnabled(!active);
        binding.experimentTrials.setEnabled(!active);
        binding.experimentSlow.setEnabled(!active);

        if (active) {
            boolean paused = snapshot.state == ExperimentMonitor.State.PAUSED;
            binding.experimentProgress.setMax(Math.max(1, snapshot.total));
            binding.experimentProgress.setProgress(snapshot.done);
            binding.experimentProgressText.setText(getString(paused ? R.string.experiment_progress_paused : R.string.experiment_progress,
                    snapshot.done, snapshot.total, snapshot.graphName));
            binding.experimentPause.setText(paused ? R.string.experiment_resume : R.string.experiment_pause);
            binding.experimentHistogram.setHistogram(snapshot.histogram);
            binding.experimentResult.setText(R.string.experiment_running_hint);
            binding.experimentBanner.setVisibility(paused && snapshot.pausedByBattery ? View.VISIBLE : View.GONE);
            binding.experimentBanner.setText(R.string.experiment_banner_battery);
        } else {
            binding.experimentBanner.setVisibility(View.GONE);
            if (snapshot.error != null && !snapshot.error.equals(shownError)) {
                shownError = snapshot.error;   // say it once, not on every redraw
                snack(getString(R.string.experiment_failed, snapshot.error));
            }
            if (snapshot.lastRunId != shownRunId) {
                shownRunId = snapshot.lastRunId;
                loadLatest();
            }
        }
        showNetwork();
        showNotificationNotice();
    }

    /** Shows the newest saved experiment, whoever ran it and whenever it finished. */
    private void loadLatest() {
        container.executors().disk(() -> container.repository().latestExperiment(),
                new AppExecutors.Callback<ExperimentRunRecord>() {
                    @Override
                    public void onSuccess(@Nullable ExperimentRunRecord latest) {
                        if (binding == null || monitor.current().active()) {
                            return;
                        }
                        if (latest == null) {
                            binding.experimentHistogram.setHistogram(null);
                            binding.experimentResult.setText(R.string.experiment_no_result);
                            return;
                        }
                        try {
                            ExperimentRecord record = ExperimentRecord.parse(latest.detail);
                            Histogram histogram = record.histogram();
                            ExperimentAlgorithm shown = ExperimentAlgorithm.byId(record.algorithmId());
                            binding.experimentHistogram.setHistogram(histogram);
                            binding.experimentChartTitle.setText(shown.chartTitle());
                            binding.experimentResult.setText(getString(R.string.experiment_result, !shown.needsGraph()
                                    ? shown.title() : latest.graphName.isEmpty()
                                    ? getString(R.string.experiment_graph_deleted) : latest.graphName, record.summary(),
                                    DateUtils.formatDateTime(requireContext(), latest.createdAt,
                                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL)));
                        } catch (Exception unreadable) {
                            binding.experimentHistogram.setHistogram(null);
                            binding.experimentResult.setText(latest.summary);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        // The chart simply stays as it was.
                    }
                });
    }

    private void showNetwork() {
        if (binding != null) {
            binding.experimentNetwork.setText(online ? R.string.experiment_network_online : R.string.experiment_network_offline);
        }
    }

    private void showNotificationNotice() {
        if (binding == null) {
            return;
        }
        boolean hidden = !Notifications.areAllowed(requireContext());
        binding.experimentNotice.setVisibility(hidden ? View.VISIBLE : View.GONE);
        binding.experimentNotice.setText(R.string.experiment_notifications_off);
    }

    private void snack(@NonNull String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

package com.graphlab.ui.lab;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.graphlab.GraphLabApp;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.receiver.RunFinishedReceiver;
import com.graphlab.databinding.ContentLabBinding;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.Tab;

/**
 * The Lab tab: a mode bar (Graphs, Build, Run, Compare, Experiment) above one fragment per mode.
 * The fragments share one {@link LabViewModel}, so the graph, the camera and the run survive
 * switching modes and rotating the phone. Compare (MS10) runs two algorithms on the working graph and sets them side
 * by side; Experiment (MS8) runs long Karger experiments in a foreground service.
 *
 * <p>The fragment on screen is the single source of truth for the mode: after a rotation the
 * FragmentManager brings it back by itself, and the mode bar is set from it.
 *
 * <p>This Activity also overrides the lifecycle callbacks and logs each one (Logcat tag
 * "GraphLab.Lab"), as the coursework checklist asks. The same events, for every Activity, are
 * written to the database by {@code LifecycleLogger} and listed in Me.
 */
public class LabActivity extends BaseActivity implements LabHost {
    private static final String TAG = "GraphLab.Lab";
    /** Set by the experiment notification: open the Lab on its Experiment mode. */
    public static final String EXTRA_OPEN_EXPERIMENT = "open_experiment";
    /** MS14: a saved graph to open in Run mode (the map's "Replay in Lab"). */
    public static final String EXTRA_OPEN_GRAPH_ID = "open_graph_id";

    private ContentLabBinding binding;
    private LabViewModel model;
    private boolean updatingUi;
    private com.graphlab.ui.common.SoundEffects sounds;
    /** Hears RUN_FINISHED while the Lab is on screen (registered in onStart, removed in onStop). */
    private final RunFinishedReceiver runFinishedReceiver = new RunFinishedReceiver(
            (runId, graphName, summary, partial) -> onExperimentFinished(summary));

    @Override
    protected Tab getTab() {
        return Tab.LAB;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.content_lab;
    }

    @Override
    protected void onContentInflated(View content, @Nullable Bundle savedInstanceState) {
        binding = ContentLabBinding.bind(content);
        model = new ViewModelProvider(this).get(LabViewModel.class);
        model.attach(GraphLabApp.container(this));
        sounds = new com.graphlab.ui.common.SoundEffects(this);

        boolean modelWasInUse = model.markInitialized();
        final boolean fromMap = savedInstanceState == null && getIntent().getLongExtra(EXTRA_OPEN_GRAPH_ID, 0L) != 0L;
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_OPEN_EXPERIMENT, false)) {
            model.setMode(LabMode.EXPERIMENT);   // opened from the experiment notification
        }
        Fragment shown = getSupportFragmentManager().findFragmentById(R.id.lab_container);
        if (savedInstanceState != null && !modelWasInUse && (shown instanceof BuildFragment || shown instanceof RunFragment
                || shown instanceof CompareFragment)) {
            // The system killed the process while Build or Run was open: the editor's memory is gone
            // (unsaved work was filed in the library when the screen stopped), so start from the library.
            model.setMode(LabMode.GRAPHS);
            showFragment(LabMode.GRAPHS);
        } else if (shown instanceof RunFragment) {
            model.setMode(LabMode.RUN);
        } else if (shown instanceof BuildFragment) {
            model.setMode(LabMode.BUILD);
        } else if (shown instanceof ExperimentFragment) {
            model.setMode(LabMode.EXPERIMENT);
        } else if (shown instanceof CompareFragment) {
            model.setMode(LabMode.COMPARE);
        } else if (shown instanceof GraphsFragment) {
            model.setMode(LabMode.GRAPHS);
        } else {
            showFragment(model.mode());   // first start
        }

        checkToggle(model.mode());
        if (isLandscape()) {
            binding.modeToggle.setVisibility(View.GONE);   // shown with the bottom bar (see onLandscapeBarsToggled)
        }
        if (fromMap) {
            openGraphFromIntent(getIntent());
        }
        binding.modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (updatingUi || !isChecked) {
                return;
            }
            LabMode mode = modeFor(checkedId);
            if (mode != model.mode()) {
                showMode(mode);
            }
        });
    }

    /** Landscape: the mode bar and the bottom bar are hidden together and shown together by the menu button. */
    @Override
    protected void onLandscapeBarsToggled(boolean shown) {
        if (binding != null) {
            binding.modeToggle.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_EXPERIMENT, false)) {
            showMode(LabMode.EXPERIMENT);
        }
        if (intent.getLongExtra(EXTRA_OPEN_GRAPH_ID, 0L) != 0L) {
            openGraphFromIntent(intent);
        }
    }

    /** Opens the saved graph the intent names and shows Run mode, so the map's graph can be replayed. */
    private void openGraphFromIntent(@NonNull Intent intent) {
        long id = intent.getLongExtra(EXTRA_OPEN_GRAPH_ID, 0L);
        intent.removeExtra(EXTRA_OPEN_GRAPH_ID);
        model.openGraph(id, new LabViewModel.OpenListener() {
            @Override
            public void onOpened() {
                showMode(LabMode.RUN);
            }

            @Override
            public void onFailed(@NonNull String message) {
                showMode(LabMode.GRAPHS);
            }
        });
    }

    /** The experiment saved a result: tell the person, whichever Lab mode is open, and offer to look. */
    private void onExperimentFinished(@NonNull String summary) {
        if (sounds != null) {
            sounds.playDone();
        }
        if (binding == null || model.mode() == LabMode.EXPERIMENT) {
            return;   // the Experiment screen is already showing it
        }
        Snackbar.make(binding.getRoot(), getString(R.string.experiment_finished_banner, summary), Snackbar.LENGTH_LONG)
                .setAction(R.string.experiment_view, v -> showMode(LabMode.EXPERIMENT))
                .show();
    }

    @Override
    public void showMode(@NonNull LabMode mode) {
        if (mode == model.mode() && getSupportFragmentManager().findFragmentById(R.id.lab_container) != null) {
            checkToggle(mode);
            return;
        }
        if (model.mode() == LabMode.BUILD) {
            model.saveIfDirty();   // leaving Build keeps your work, and a run can then be filed under its graph
        }
        model.setMode(mode);
        checkToggle(mode);
        showFragment(mode);
    }

    private void showFragment(@NonNull LabMode mode) {
        Fragment fragment;
        switch (mode) {
            case RUN:
                fragment = new RunFragment();
                break;
            case BUILD:
                fragment = new BuildFragment();
                break;
            case EXPERIMENT:
                fragment = new ExperimentFragment();
                break;
            case COMPARE:
                fragment = new CompareFragment();
                break;
            default:
                fragment = new GraphsFragment();
                break;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.lab_container, fragment)
                .commit();
    }

    /** Sets the mode bar without triggering its listener. */
    private void checkToggle(@NonNull LabMode mode) {
        updatingUi = true;
        binding.modeToggle.check(buttonIdFor(mode));
        updatingUi = false;
    }

    private static int buttonIdFor(LabMode mode) {
        switch (mode) {
            case BUILD:
                return R.id.mode_build;
            case RUN:
                return R.id.mode_run;
            case EXPERIMENT:
                return R.id.mode_experiment;
            case COMPARE:
                return R.id.mode_compare;
            default:
                return R.id.mode_graphs;
        }
    }

    private static LabMode modeFor(int buttonId) {
        if (buttonId == R.id.mode_build) {
            return LabMode.BUILD;
        }
        if (buttonId == R.id.mode_run) {
            return LabMode.RUN;
        }
        if (buttonId == R.id.mode_experiment) {
            return LabMode.EXPERIMENT;
        }
        if (buttonId == R.id.mode_compare) {
            return LabMode.COMPARE;
        }
        return LabMode.GRAPHS;
    }

    // ---- lifecycle callbacks, logged for the checklist ---------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        runFinishedReceiver.register(this);
        Log.d(TAG, "onStart: the Lab is visible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: the Lab is in front and can be touched");
        model.returnToScreen(this);   // a run that kept playing in the background comes back to this screen
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: another screen is taking over");
        if (!isChangingConfigurations()) {
            // Done here, while the app is still in front: Android only lets a foreground service start from a visible app.
            model.leaveScreen(this);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        runFinishedReceiver.unregister(this);
        Log.d(TAG, "onStop: the Lab is hidden, rotating=" + isChangingConfigurations());
        if (!isChangingConfigurations()) {
            // Leaving the screen (another tab, Home, Back): file any unsaved work in the library.
            model.saveIfDirty();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: the system may kill this process next");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: finishing=" + isFinishing() + ", rotating=" + isChangingConfigurations());
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        super.onDestroy();
    }
}

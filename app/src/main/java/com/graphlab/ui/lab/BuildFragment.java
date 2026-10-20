package com.graphlab.ui.lab;

import com.graphlab.ui.common.OverflowMenu;
import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.databinding.FragmentBuildBinding;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFiles;
import com.graphlab.engine.io.GraphNames;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.ui.common.EditorTool;
import com.graphlab.ui.common.GraphShare;
import com.graphlab.ui.common.GraphView;

/**
 * Lab, Build mode: the graph editor from MS2, now a fragment so the Lab can switch between
 * modes. The graph, the camera and the tool live in {@link LabViewModel}, so rotating the
 * phone, or switching to Run and back, keeps all of them.
 */
public class BuildFragment extends Fragment implements GraphView.Listener, LabViewModel.WorkingListener {

    /** Positions in SampleGraph run 0 to 100; this spreads them to fit 44 dp nodes. */
    private static final double SAMPLE_SCALE = 3.0;

    private FragmentBuildBinding binding;
    private LabViewModel model;
    private GraphEditor editor;
    private boolean updatingUi;
    /** The dialog on screen, if any. Dismissed with the view so rotation does not leak its window. */
    @Nullable
    private Dialog openDialog;

    /** The system photo picker: it needs no storage permission, and the app copies the picked photo into its own folder. */
    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null || binding == null) {
                    return;   // the person closed the picker
                }
                model.setPhotoFrom(requireContext().getContentResolver(), uri, new LabViewModel.PhotoListener() {
                    @Override
                    public void onDone() {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(), R.string.photo_added, Snackbar.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailed(@NonNull String message) {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
            });

    private final Runnable photoChanged = () -> {
        if (binding != null) {
            showPhoto();
        }
    };

    private void showPhoto() {
        com.graphlab.engine.io.Background bg = model.background();
        binding.canvas.setPhoto(model.photo(), bg == null ? 0f : (float) bg.opacity());
        renderStatus();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBuildBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        model.attach(GraphLabApp.container(requireContext()));
        editor = model.editor();

        binding.canvas.bind(editor, model.viewport());
        binding.canvas.setMode(GraphView.Mode.EDIT);
        binding.canvas.setFrame(null);
        binding.canvas.setListener(this);
        binding.canvas.setTool(model.tool());

        setUpToolButtons();
        setUpSwitches();
        setUpActions();
        setUpHeader();
        OverflowMenu.attach(binding.actionMore, binding.actionSave, binding.actionShare, binding.actionFit,
                binding.actionPhoto, binding.actionSample, binding.actionClear);
        model.setWorkingListener(this);
        model.addPhotoListener(photoChanged);
        showPhoto();
        syncControls();
    }

    @Override
    public void onDestroyView() {
        if (openDialog != null && openDialog.isShowing()) {
            openDialog.dismiss();
        }
        openDialog = null;
        binding.canvas.setListener(null);
        model.removePhotoListener(photoChanged);
        model.clearWorkingListener(this);
        binding = null;
        super.onDestroyView();
    }

    // ---- controls --------------------------------------------------------------------

    private void setUpToolButtons() {
        binding.toolToggle.check(buttonIdFor(model.tool()));
        binding.toolToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            EditorTool tool = toolFor(checkedId);
            model.setTool(tool);
            binding.canvas.setTool(tool);
            renderHint();
        });
    }

    private void setUpSwitches() {
        binding.switchWeighted.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) {
                return;
            }
            editor.setWeighted(checked);
            edited();
            binding.canvas.refresh();
            renderStatus();
        });
        binding.switchDirected.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) {
                return;
            }
            onDirectedToggled(checked);
        });
    }

    private void setUpActions() {
        binding.actionFit.setOnClickListener(v -> binding.canvas.fitToGraph());
        binding.actionPhoto.setOnClickListener(v -> openDialog = PhotoDialog.show(requireContext(), model,
                () -> photoPicker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build())));
        binding.actionSample.setOnClickListener(v -> loadSample());
        binding.actionClear.setOnClickListener(v -> confirmClear());
        binding.actionUndo.setOnClickListener(v -> {
            if (model.undo()) {
                syncControls();
            }
        });
        binding.actionRedo.setOnClickListener(v -> {
            if (model.redo()) {
                syncControls();
            }
        });
    }

    /** Records the edit that just finished (if any) and updates the Undo and Redo buttons. */
    private void edited() {
        model.checkpoint();
        renderHistory();
    }

    private void renderHistory() {
        if (binding != null) {
            binding.actionUndo.setEnabled(model.canUndo());
            binding.actionRedo.setEnabled(model.canRedo());
        }
    }

    // ---- header: name, saved state, Save, Share --------------------------------------

    private void setUpHeader() {
        binding.graphName.setOnClickListener(v -> openDialog = NameDialog.show(requireContext(),
                R.string.rename_title, model.graphName(), model::renameWorking));
        binding.actionSave.setOnClickListener(v -> onSaveClicked());
        binding.actionShare.setOnClickListener(v -> onShareClicked());
    }

    private void renderHeader() {
        String name = model.graphName();
        binding.graphName.setText(name.isEmpty() ? getString(R.string.untitled_hint) : name);
        int status;
        if (model.graphId() == 0) {
            status = editor.isEmpty() ? R.string.status_new : R.string.status_unsaved_new;
        } else {
            status = model.isDirty() ? R.string.status_unsaved : R.string.status_saved;
        }
        binding.graphStatus.setText(status);
    }

    @Override
    public void onWorkingChanged() {
        if (binding != null) {
            renderHeader();
            renderStatus();
        }
    }

    private void onSaveClicked() {
        if (model.graphId() == 0 && model.graphName().isEmpty()) {
            if (editor.isEmpty()) {
                onMessage(R.string.save_nothing);
                return;
            }
            // A new graph needs a name before it goes into the library.
            openDialog = NameDialog.show(requireContext(), R.string.save_as_title,
                    getString(R.string.untitled_default), this::doSave);
        } else {
            doSave(null);
        }
    }

    private void doSave(@Nullable String name) {
        model.save(name, new LabViewModel.SaveListener() {
            @Override
            public void onSaved(@NonNull String savedName) {
                if (binding != null) {
                    Snackbar.make(binding.canvas, getString(R.string.saved_as, savedName), Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailed(@NonNull String message) {
                if (binding != null) {
                    Snackbar.make(binding.canvas, message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void onShareClicked() {
        if (editor.isEmpty()) {
            onMessage(R.string.share_empty);
            return;
        }
        String[] formats = {getString(R.string.share_format_json), getString(R.string.share_format_graphml)};
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.share_title)
                .setItems(formats, (dialog, which) -> shareCurrent(which == 0
                        ? GraphFiles.Format.JSON : GraphFiles.Format.GRAPHML))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void shareCurrent(GraphFiles.Format format) {
        String name = model.graphName().isEmpty() ? GraphNames.UNTITLED : model.graphName();
        // The graph as it is on screen, including edits that are not saved yet.
        GraphDocument doc = new GraphDocument(name, editor.isWeighted(), editor.toStoredGraph());
        GraphShare.share(requireActivity(), GraphLabApp.container(requireContext()).executors(), doc, format);
    }

    private void onDirectedToggled(boolean directed) {
        if (directed) {
            editor.setDirected(true);
            edited();
            binding.canvas.refresh();
            renderStatus();
            return;
        }
        int pairs = editor.reciprocalPairCount();
        if (pairs == 0) {
            editor.setDirected(false);
            edited();
            binding.canvas.refresh();
            renderStatus();
            return;
        }
        // Undirected graphs cannot hold both A to B and B to A, so tell the user what is lost.
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.merge_title)
                .setMessage(getResources().getQuantityString(R.plurals.merge_message, pairs, pairs))
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> syncControls())
                .setPositiveButton(R.string.action_merge, (dialog, which) -> {
                    editor.setDirected(false);
                    edited();
                    binding.canvas.refresh();
                    syncControls();
                })
                .setOnCancelListener(dialog -> syncControls())
                .show();
    }

    private void loadSample() {
        if (editor.isEmpty()) {
            applySample();
            return;
        }
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.replace_title)
                .setMessage(R.string.replace_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_replace, (dialog, which) -> applySample())
                .show();
    }

    private void applySample() {
        editor.load(SampleGraph.sixNodeUndirected(), true);
        editor.scalePositions(SAMPLE_SCALE);
        edited();
        binding.canvas.fitToGraph();
        syncControls();
    }

    private void confirmClear() {
        if (editor.isEmpty()) {
            return;
        }
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear, (dialog, which) -> {
                    editor.clear();
                    edited();
                    binding.canvas.fitToGraph();
                    syncControls();
                })
                .show();
    }

    /** Brings every control in line with the model (after a load, a clear or a rotation). */
    private void syncControls() {
        if (binding == null) {
            return;
        }
        updatingUi = true;
        binding.switchDirected.setChecked(editor.isDirected());
        binding.switchWeighted.setChecked(editor.isWeighted());
        updatingUi = false;
        binding.canvas.refresh();
        renderStatus();
        renderHint();
        renderHeader();
        renderHistory();
    }

    private void renderStatus() {
        Resources res = getResources();
        String status = getString(R.string.lab_status,
                res.getQuantityString(R.plurals.nodes, editor.nodeCount(), editor.nodeCount()),
                res.getQuantityString(R.plurals.edges, editor.edgeCount(), editor.edgeCount()),
                getString(editor.isDirected() ? R.string.directed : R.string.undirected),
                getString(editor.isWeighted() ? R.string.weighted : R.string.unweighted));
        binding.status.setText(status);
        binding.canvas.setContentDescription(getString(R.string.canvas_description, status));
    }

    private void renderHint() {
        binding.hint.setText(hintFor(model.tool()));
    }

    // ---- GraphView.Listener ----------------------------------------------------------

    @Override
    public void onConnectRequested(int fromId, int toId) {
        GraphEditor.ConnectCheck check = editor.checkConnect(fromId, toId);
        if (check == GraphEditor.ConnectCheck.EXISTS) {
            onMessage(R.string.msg_already_connected);
            return;
        }
        if (check != GraphEditor.ConnectCheck.OK) {
            return;
        }
        if (!editor.isWeighted()) {
            editor.addEdge(fromId, toId, GraphEditor.DEFAULT_WEIGHT);
            onGraphChanged();
            return;
        }
        openDialog = WeightDialog.show(requireContext(),
                edgeTitle(R.string.new_edge_title, R.string.new_edge_title_directed, fromId, toId),
                GraphEditor.DEFAULT_WEIGHT, false, new WeightDialog.Callback() {
                    @Override
                    public void onWeight(double weight) {
                        // The graph cannot have changed while the dialog was open, but check anyway.
                        if (binding != null && editor.checkConnect(fromId, toId) == GraphEditor.ConnectCheck.OK) {
                            editor.addEdge(fromId, toId, weight);
                            onGraphChanged();
                        }
                    }

                    @Override
                    public void onDelete() {
                    }
                });
    }

    @Override
    public void onEdgeWeightRequested(int edgeId) {
        Edge edge = editor.findEdge(edgeId);
        if (edge == null) {
            return;
        }
        String title = edgeTitle(R.string.edit_edge_title, R.string.edit_edge_title_directed, edge.from(), edge.to());
        openDialog = WeightDialog.show(requireContext(), title, edge.weight(), true, new WeightDialog.Callback() {
            @Override
            public void onWeight(double weight) {
                editor.setEdgeWeight(edgeId, weight);
                onGraphChanged();
            }

            @Override
            public void onDelete() {
                editor.removeEdge(edgeId);
                onGraphChanged();
            }
        });
    }

    @Override
    public void onMessage(@StringRes int message) {
        if (binding != null) {
            Snackbar.make(binding.canvas, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onGraphChanged() {
        if (binding == null) {
            return;
        }
        edited();
        binding.canvas.refresh();
        renderStatus();
        renderHeader();
    }

    // ---- small helpers ---------------------------------------------------------------

    @NonNull
    private String edgeTitle(@StringRes int undirected, @StringRes int directed, int fromId, int toId) {
        return getString(editor.isDirected() ? directed : undirected, nameOf(fromId), nameOf(toId));
    }

    @NonNull
    private String nameOf(int nodeId) {
        Node node = editor.findNode(nodeId);
        return node == null ? "#" + nodeId : node.label();
    }

    private static int buttonIdFor(EditorTool tool) {
        switch (tool) {
            case EDGE:
                return R.id.tool_edge;
            case MOVE:
                return R.id.tool_move;
            case DELETE:
                return R.id.tool_delete;
            default:
                return R.id.tool_node;
        }
    }

    private static EditorTool toolFor(int buttonId) {
        if (buttonId == R.id.tool_edge) {
            return EditorTool.EDGE;
        }
        if (buttonId == R.id.tool_move) {
            return EditorTool.MOVE;
        }
        if (buttonId == R.id.tool_delete) {
            return EditorTool.DELETE;
        }
        return EditorTool.NODE;
    }

    @StringRes
    private static int hintFor(EditorTool tool) {
        switch (tool) {
            case EDGE:
                return R.string.hint_edge;
            case MOVE:
                return R.string.hint_move;
            case DELETE:
                return R.string.hint_delete;
            default:
                return R.string.hint_node;
        }
    }
}

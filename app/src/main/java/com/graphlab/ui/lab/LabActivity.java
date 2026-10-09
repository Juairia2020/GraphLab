package com.graphlab.ui.lab;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.R;
import com.graphlab.databinding.ContentLabBinding;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.ui.common.BaseActivity;
import com.graphlab.ui.common.EditorTool;
import com.graphlab.ui.common.GraphView;
import com.graphlab.ui.common.Tab;

/**
 * The Lab tab. In MS2 it is the graph editor (Build mode): a canvas plus the controls that
 * change what a touch does. The graph and the camera live in {@link LabViewModel}, so
 * rotating the phone keeps both. MS3 adds the mode bar and the Run mode around this screen.
 */
public class LabActivity extends BaseActivity implements GraphView.Listener {

    /** Positions in SampleGraph run 0 to 100; this spreads them to fit 44 dp nodes. */
    private static final double SAMPLE_SCALE = 3.0;

    private ContentLabBinding binding;
    private LabViewModel model;
    private GraphEditor editor;
    private boolean updatingUi;
    /** The dialog on screen, if any. Dismissed in onDestroy so rotation does not leak its window. */
    @Nullable
    private Dialog openDialog;

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
        editor = model.editor();

        binding.canvas.bind(editor, model.viewport());
        binding.canvas.setListener(this);
        binding.canvas.setTool(model.tool());

        setUpToolButtons();
        setUpSwitches();
        setUpActions();
        syncControls();
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
        binding.actionSample.setOnClickListener(v -> loadSample());
        binding.actionClear.setOnClickListener(v -> confirmClear());
    }

    private void onDirectedToggled(boolean directed) {
        if (directed) {
            editor.setDirected(true);
            binding.canvas.refresh();
            renderStatus();
            return;
        }
        int pairs = editor.reciprocalPairCount();
        if (pairs == 0) {
            editor.setDirected(false);
            binding.canvas.refresh();
            renderStatus();
            return;
        }
        // Undirected graphs cannot hold both A to B and B to A, so tell the user what is lost.
        openDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.merge_title)
                .setMessage(getResources().getQuantityString(R.plurals.merge_message, pairs, pairs))
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> syncControls())
                .setPositiveButton(R.string.action_merge, (dialog, which) -> {
                    editor.setDirected(false);
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
        openDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.replace_title)
                .setMessage(R.string.replace_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_replace, (dialog, which) -> applySample())
                .show();
    }

    private void applySample() {
        editor.load(SampleGraph.sixNodeUndirected(), true);
        editor.scalePositions(SAMPLE_SCALE);
        binding.canvas.fitToGraph();
        syncControls();
    }

    private void confirmClear() {
        if (editor.isEmpty()) {
            return;
        }
        openDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear, (dialog, which) -> {
                    editor.clear();
                    binding.canvas.fitToGraph();
                    syncControls();
                })
                .show();
    }

    /** Brings every control in line with the model (after a load, a clear or a rotation). */
    private void syncControls() {
        updatingUi = true;
        binding.switchDirected.setChecked(editor.isDirected());
        binding.switchWeighted.setChecked(editor.isWeighted());
        updatingUi = false;
        binding.canvas.refresh();
        renderStatus();
        renderHint();
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
        openDialog = WeightDialog.show(this, edgeTitle(R.string.new_edge_title, R.string.new_edge_title_directed, fromId, toId),
                GraphEditor.DEFAULT_WEIGHT, false, new WeightDialog.Callback() {
                    @Override
                    public void onWeight(double weight) {
                        // The graph cannot have changed while the dialog was open, but check anyway.
                        if (editor.checkConnect(fromId, toId) == GraphEditor.ConnectCheck.OK) {
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
        openDialog = WeightDialog.show(this, title, edge.weight(), true, new WeightDialog.Callback() {
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
        Snackbar.make(binding.canvas, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onGraphChanged() {
        binding.canvas.refresh();
        renderStatus();
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null && openDialog.isShowing()) {
            openDialog.dismiss();
        }
        openDialog = null;
        super.onDestroy();
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

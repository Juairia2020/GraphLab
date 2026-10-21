package com.graphlab.ui.imports;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.data.imports.PhotoImporter;
import com.graphlab.databinding.ActivityImportBinding;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.imageimport.DetectedGraph;
import com.graphlab.engine.imageimport.FakeDetections;
import com.graphlab.engine.imageimport.ReviewSession;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.GraphNames;
import com.graphlab.ui.lab.LabActivity;
import java.io.File;
import java.util.List;

/**
 * Image import (MS16, G10 and G11). The person picks or takes a photo of a graph drawing, of an adjacency matrix or of an
 * edge list. The screen reads it, then shows the result for review: every item the reader was unsure of is amber and listed
 * with a Fix button, and nothing is saved until the person confirms. The graph is then saved and opened in Build.
 *
 * <p>The review step does not depend on a detector: "Try the review screen" fills it with made-up results.
 */
public class ImportActivity extends AppCompatActivity {
    private ActivityImportBinding binding;
    private AppContainer container;
    private ReviewSession session;
    private CameraPermission cameraPermission;
    private FlagAdapter adapter;
    private boolean updatingSwitches;
    private boolean working;

    private final ActivityResultLauncher<PickVisualMediaRequest> gallery = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    read(uri, null);
                }
            });

    private final ActivityResultLauncher<android.content.Intent> camera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra(CameraCaptureActivity.EXTRA_PATH);
                    if (path != null) {
                        read(null, new File(path));
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        container = GraphLabApp.container(this);
        cameraPermission = new CameraPermission(this, this::openCamera);
        adapter = new FlagAdapter(this::fix);
        binding.importFlags.setLayoutManager(new LinearLayoutManager(this));
        binding.importFlags.setAdapter(adapter);

        binding.importBack.setOnClickListener(v -> finish());
        binding.importCamera.setOnClickListener(v -> cameraPermission.request());
        binding.importGallery.setOnClickListener(v -> gallery.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        binding.importDemo.setOnClickListener(v -> {
            session = new ReviewSession(FakeDetections.demo());
            showReview(null);
        });
        binding.importCanvas.setListener(new ReviewCanvasView.Listener() {
            @Override
            public void onNode(int nodeId) {
                editNode(nodeId);
            }

            @Override
            public void onEdge(int edgeId) {
                editEdge(edgeId);
            }
        });
        binding.importDirected.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitches && session != null) {
                session.setDirected(checked);
                refresh();
            }
        });
        binding.importWeighted.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitches && session != null) {
                session.setWeighted(checked);
                refresh();
            }
        });
        binding.importAddEdge.setOnClickListener(v -> addEdge());
        binding.importAcceptAll.setOnClickListener(v -> {
            session.confirmAll();
            refresh();
        });
        binding.importConfirm.setOnClickListener(v -> confirm());
        if (getIntent().getBooleanExtra(EXTRA_DEMO, false)) {
            session = new ReviewSession(FakeDetections.demo());
            showReview(null);
        }
    }

    public static final String EXTRA_DEMO = "demo";

    // ---- reading a photo ----

    private void openCamera() {
        camera.launch(new android.content.Intent(this, CameraCaptureActivity.class));
    }

    private void read(@Nullable Uri uri, @Nullable File file) {
        if (working) {
            return;
        }
        working = true;
        final boolean text = binding.importKind.getCheckedButtonId() == R.id.import_kind_text;
        final boolean arrows = binding.importArrows.isChecked();
        showWorking(getString(R.string.import_working));
        container.executors().disk(() -> {
            PhotoImporter.Result r = PhotoImporter.run(getApplicationContext(), uri, file, text, arrows,
                    message -> runOnUiThread(() -> showWorking(message)));
            return r;
        }, new AppExecutors.Callback<PhotoImporter.Result>() {
            @Override
            public void onSuccess(PhotoImporter.Result result) {
                working = false;
                if (isFinishing()) {
                    return;
                }
                session = new ReviewSession(result.graph);
                showReview(result.thumbnail);
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                working = false;
                if (isFinishing()) {
                    return;
                }
                showSource();
                new MaterialAlertDialogBuilder(ImportActivity.this)
                        .setTitle(R.string.import_failed_title)
                        .setMessage(error instanceof PhotoImporter.ImportException ? error.getMessage()
                                : getString(R.string.import_failed_body))
                        .setPositiveButton(R.string.import_ok, null)
                        .show();
            }
        });
    }

    private void showSource() {
        binding.importSource.setVisibility(View.VISIBLE);
        binding.importWorking.setVisibility(View.GONE);
        binding.importReview.setVisibility(View.GONE);
    }

    private void showWorking(String message) {
        binding.importSource.setVisibility(View.GONE);
        binding.importWorking.setVisibility(View.VISIBLE);
        binding.importReview.setVisibility(View.GONE);
        binding.importWorkingText.setText(message);
    }

    private void showReview(@Nullable Bitmap thumbnail) {
        binding.importSource.setVisibility(View.GONE);
        binding.importWorking.setVisibility(View.GONE);
        binding.importReview.setVisibility(View.VISIBLE);
        binding.importPhoto.setVisibility(thumbnail == null ? View.GONE : View.VISIBLE);
        binding.importPhoto.setImageBitmap(thumbnail);
        refresh();
    }

    // ---- the review screen ----

    private void refresh() {
        binding.importCanvas.show(session);
        List<ReviewSession.Flag> flags = session.flags();
        adapter.submit(flags);
        updatingSwitches = true;
        binding.importDirected.setChecked(session.directed());
        binding.importWeighted.setChecked(session.weighted());
        updatingSwitches = false;
        int n = session.nodes().size();
        int e = session.edges().size();
        String counts = getString(R.string.import_summary, getResources().getQuantityString(R.plurals.nodes, n, n),
                getResources().getQuantityString(R.plurals.edges, e, e));
        binding.importSummary.setText(flags.isEmpty() ? counts
                : counts + " " + getResources().getQuantityString(R.plurals.import_to_check, flags.size(), flags.size()));
        binding.importAllClear.setVisibility(flags.isEmpty() ? View.VISIBLE : View.GONE);
        binding.importFlags.setVisibility(flags.isEmpty() ? View.GONE : View.VISIBLE);
        binding.importAcceptAll.setEnabled(!flags.isEmpty());
    }

    /** The Fix button of one amber row. */
    private void fix(@NonNull ReviewSession.Flag flag) {
        switch (flag.kind()) {
            case NODE:
                editNode(flag.id());
                break;
            case EDGE:
                editEdge(flag.id());
                break;
            case WEIGHT:
                editWeight(flag.id());
                break;
            case DIRECTION:
                chooseDirection();
                break;
            default:
                new MaterialAlertDialogBuilder(this)
                        .setMessage(flag.message())
                        .setPositiveButton(R.string.import_got_it, (d, w) -> {
                            session.dismissWarning(flag.id());
                            refresh();
                        })
                        .show();
                break;
        }
    }

    private void editNode(int id) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(session.labelOf(id));
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_node_title)
                .setView(padded(input))
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    session.renameNode(id, input.getText().toString());
                    refresh();
                })
                .setNeutralButton(R.string.import_looks_right, (d, w) -> {
                    session.confirmNode(id);
                    refresh();
                })
                .setNegativeButton(R.string.import_delete_node, (d, w) -> {
                    session.deleteNode(id);
                    refresh();
                })
                .show();
    }

    private void editEdge(int id) {
        final boolean weighted = session.weighted();
        final String[] items = weighted
                ? new String[] {getString(R.string.import_edit_weight), getString(R.string.import_looks_right),
                        getString(R.string.import_reverse), getString(R.string.import_delete_edge)}
                : new String[] {getString(R.string.import_looks_right), getString(R.string.import_reverse),
                        getString(R.string.import_delete_edge)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_edge_title)
                .setItems(items, (d, which) -> {
                    int pick = weighted ? which : which + 1;
                    switch (pick) {
                        case 0:
                            editWeight(id);
                            return;
                        case 1:
                            session.confirmEdge(id);
                            break;
                        case 2:
                            try {
                                session.reverseEdge(id);
                                session.confirmEdge(id);
                            } catch (IllegalArgumentException ex) {
                                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                            break;
                        default:
                            session.deleteEdge(id);
                            break;
                    }
                    refresh();
                })
                .show();
    }

    private void editWeight(int edgeId) {
        DetectedGraph.Edge edge = null;
        for (DetectedGraph.Edge e : session.edges()) {
            if (e.id() == edgeId) {
                edge = e;
            }
        }
        if (edge == null) {
            return;
        }
        final DetectedGraph.Edge target = edge;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSingleLine(true);
        if (target.weight() != null) {
            input.setText(WeightFormat.format(target.weight()));
            input.setSelection(input.getText().length());
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.import_weight_title, session.labelOf(target.from()), session.labelOf(target.to())))
                .setView(padded(input))
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    try {
                        session.setWeight(edgeId, Double.parseDouble(input.getText().toString().trim().replace(',', '.')));
                        refresh();
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, R.string.import_weight_bad, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null);
        if (target.weight() != null) {
            builder.setNeutralButton(R.string.import_looks_right, (d, w) -> {
                session.confirmWeight(edgeId);
                refresh();
            });
        }
        builder.show();
    }

    private void chooseDirection() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_direction_title)
                .setItems(new String[] {getString(R.string.import_direction_undirected), getString(R.string.import_direction_directed)},
                        (d, which) -> {
                            session.setDirected(which == 1);
                            refresh();
                        })
                .show();
    }

    private void addEdge() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText from = field(getString(R.string.import_add_from), InputType.TYPE_CLASS_TEXT);
        final EditText to = field(getString(R.string.import_add_to), InputType.TYPE_CLASS_TEXT);
        final EditText weight = field(getString(R.string.import_add_weight),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(from);
        box.addView(to);
        if (session.weighted()) {
            box.addView(weight);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_add_edge)
                .setView(padded(box))
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    int a = nodeByLabel(from.getText().toString().trim());
                    int b = nodeByLabel(to.getText().toString().trim());
                    if (a < 0 || b < 0) {
                        Toast.makeText(this, R.string.import_add_unknown, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Double value = null;
                    if (session.weighted()) {
                        try {
                            value = Double.parseDouble(weight.getText().toString().trim().replace(',', '.'));
                        } catch (NumberFormatException ex) {
                            Toast.makeText(this, R.string.import_weight_bad, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    try {
                        session.addEdge(a, b, value);
                        refresh();
                    } catch (IllegalArgumentException ex) {
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private int nodeByLabel(String label) {
        for (DetectedGraph.Node n : session.nodes()) {
            if (n.label().equals(label)) {
                return n.id();
            }
        }
        return -1;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        return e;
    }

    private View padded(View inner) {
        LinearLayout wrap = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(inner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    // ---- finishing ----

    private void confirm() {
        List<String> problems = session.problems();
        if (!problems.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.import_cannot_title)
                    .setMessage(String.join("\n", problems))
                    .setPositiveButton(R.string.import_ok, null)
                    .show();
            return;
        }
        final GraphDocument draft;
        try {
            draft = session.build(GraphNames.UNTITLED);
        } catch (GraphFormatException e) {
            new MaterialAlertDialogBuilder(this).setMessage(e.getMessage()).setPositiveButton(R.string.import_ok, null).show();
            return;
        }
        final GraphRepository repository = container.repository();
        container.executors().disk(() -> {
            String name = GraphNames.unique(getString(R.string.import_graph_name), repository.graphNames());
            GraphDocument named = draft.withName(name);
            return repository.insertGraph(name, GraphJson.write(named), named.graph().nodeCount(), named.graph().edgeCount(),
                    System.currentTimeMillis());
        }, new AppExecutors.Callback<Long>() {
            @Override
            public void onSuccess(Long id) {
                // The Lab is below this screen: bring it forward and let it open the new graph in Build.
                startActivity(new android.content.Intent(ImportActivity.this, LabActivity.class)
                        .putExtra(LabActivity.EXTRA_OPEN_GRAPH_ID, id.longValue())
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Toast.makeText(ImportActivity.this, R.string.import_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }
}

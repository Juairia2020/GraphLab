package com.graphlab.ui.lab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.GraphRecord;
import com.graphlab.databinding.ActivityDynamicBinding;
import com.graphlab.engine.dynamic.DynamicEvent;
import com.graphlab.engine.dynamic.DynamicGraph;
import com.graphlab.engine.dynamic.Timeline;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.preset.PresetResult;
import com.graphlab.engine.preset.PresetTool;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic graphs (MS15, G5). The saved graph's nodes stay fixed; its edges become the first events, and every change after
 * that is another event saved in the events table (t, type, payload). The slider shows the graph after the first k events,
 * with the minimum spanning forest highlighted, the connectivity, and the counters that compare the work of updating with
 * the work of recomputing. New changes can only be added at the end of the timeline.
 */
public class DynamicGraphActivity extends AppCompatActivity {
    private static final String EXTRA_GRAPH_ID = "graph_id";
    private static final String KEY_POSITION = "position";

    @NonNull
    public static Intent intent(@NonNull Context context, long graphId) {
        return new Intent(context, DynamicGraphActivity.class).putExtra(EXTRA_GRAPH_ID, graphId);
    }

    private ActivityDynamicBinding binding;
    private AppContainer container;
    private long graphId;
    private List<Node> nodes = new ArrayList<>();        // positions, index = node number in the timeline
    private final List<DynamicEvent> events = new ArrayList<>();
    private int position = -1;                            // events shown; -1 = not chosen yet (the end)
    private boolean updatingSlider;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityDynamicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        container = GraphLabApp.container(this);
        graphId = getIntent().getLongExtra(EXTRA_GRAPH_ID, 0L);
        if (savedInstanceState != null) {
            position = savedInstanceState.getInt(KEY_POSITION, -1);
        }
        binding.dynBack.setOnClickListener(v -> finish());
        binding.dynSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!updatingSlider && fromUser) {
                    position = progress;
                    render();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.dynAdd.setOnClickListener(v -> addEdgeDialog());
        binding.dynRemove.setOnClickListener(v -> pickEdge(false));
        binding.dynWeight.setOnClickListener(v -> pickEdge(true));
        binding.dynReset.setOnClickListener(v -> reset());
        binding.dynSlider.setEnabled(false);
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_POSITION, position);
    }

    // ---- loading and saving ------------------------------------------------------------------

    private void load() {
        container.executors().disk(() -> {
            GraphRecord record = container.repository().getGraph(graphId);
            if (record == null) {
                throw new IllegalStateException(getString(R.string.need_saved_graph));
            }
            Graph g = GraphJson.read(record.json).graph();
            List<DynamicEvent> saved = container.repository().events(graphId);
            if (saved.isEmpty()) {
                saved = seed(g);
                for (DynamicEvent e : saved) {
                    container.repository().addEvent(graphId, e);
                }
            }
            return new Object[] {g.nodes(), saved};
        }, new AppExecutors.Callback<Object[]>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onSuccess(Object[] result) {
                nodes = (List<Node>) result[0];
                events.clear();
                events.addAll((List<DynamicEvent>) result[1]);
                binding.dynSlider.setEnabled(true);
                render();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                binding.dynInfo.setText(String.valueOf(error.getMessage()));
            }
        });
    }

    /** The graph's own edges become the first events, one per time step. */
    private static List<DynamicEvent> seed(Graph g) {
        List<DynamicEvent> out = new ArrayList<>();
        int t = 0;
        for (Edge e : g.edges()) {
            int u = indexOf(g, e.from());
            int v = indexOf(g, e.to());
            if (u != v) {
                out.add(DynamicEvent.add(t++, u, v, Math.max(0, e.weight())));
            }
        }
        return out;
    }

    private static int indexOf(Graph g, int nodeId) {
        for (int i = 0; i < g.nodes().size(); i++) {
            if (g.nodes().get(i).id() == nodeId) {
                return i;
            }
        }
        return 0;
    }

    private void append(DynamicEvent event) {
        events.add(event);
        position = events.size();
        final DynamicEvent saved = event;
        container.executors().disk(() -> container.repository().addEvent(graphId, saved));
        render();
    }

    private void reset() {
        container.executors().disk(() -> {
            container.repository().clearEvents(graphId);
            GraphRecord record = container.repository().getGraph(graphId);
            List<DynamicEvent> fresh = seed(GraphJson.read(record.json).graph());
            for (DynamicEvent e : fresh) {
                container.repository().addEvent(graphId, e);
            }
            return fresh;
        }, new AppExecutors.Callback<List<DynamicEvent>>() {
            @Override
            public void onSuccess(List<DynamicEvent> fresh) {
                events.clear();
                events.addAll(fresh);
                position = events.size();
                render();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Toast.makeText(DynamicGraphActivity.this, String.valueOf(error.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- drawing -------------------------------------------------------------------------------------

    private void render() {
        int shown = position < 0 || position > events.size() ? events.size() : position;
        position = shown;
        updatingSlider = true;
        binding.dynSlider.setMax(Math.max(1, events.size()));
        binding.dynSlider.setProgress(shown);
        updatingSlider = false;
        binding.dynEventLabel.setText(getString(R.string.dynamic_event_label, shown, events.size()));
        DynamicGraph g;
        try {
            g = new Timeline(nodes.size(), events).at(shown);
        } catch (IllegalArgumentException damaged) {
            binding.dynInfo.setText(damaged.getMessage());
            return;
        }
        List<Node> drawn = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            drawn.add(new Node(i, nodes.get(i).x(), nodes.get(i).y(), nodes.get(i).label()));
        }
        List<Edge> edges = new ArrayList<>();
        for (DynamicGraph.Edge e : g.liveEdges()) {
            edges.add(new Edge(e.id(), e.u(), e.v(), e.weight()));
        }
        Graph picture = new Graph(false, drawn, edges);
        binding.dynCanvas.show(picture, -1, -1, new PresetResult(PresetTool.MST, g.mstWeight(), true, g.mstEdgeIds(),
                List.of(), List.of(), "kruskal"));
        DynamicGraph.Counters c = g.counters();
        binding.dynInfo.setText(getString(R.string.dynamic_info, WeightFormat.format(g.mstWeight()), g.components()));
        binding.dynWork.setText(getString(R.string.dynamic_work, c.mstUpdateWork(), c.mstRecomputeWork(), c.distUpdateWork(),
                c.distRecomputeWork()));
    }

    // ---- changes -------------------------------------------------------------------------------------

    private boolean atEnd() {
        if (position != events.size()) {
            Toast.makeText(this, R.string.dynamic_slider_end, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private int nextTime() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).t() + 1;
    }

    private String[] nodeNames() {
        String[] names = new String[nodes.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = nodes.get(i).label();
        }
        return names;
    }

    private void addEdgeDialog() {
        if (!atEnd()) {
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        form.setPadding(pad, pad / 2, pad, 0);
        final Spinner from = new Spinner(this);
        final Spinner to = new Spinner(this);
        from.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nodeNames()));
        to.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nodeNames()));
        if (nodes.size() > 1) {
            to.setSelection(1);
        }
        final EditText weight = new EditText(this);
        weight.setHint(R.string.dynamic_weight_hint);
        weight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(from);
        form.addView(to);
        form.addView(weight);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.dynamic_add).setView(form)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.dynamic_ok, (d, w) -> {
                    int u = from.getSelectedItemPosition();
                    int v = to.getSelectedItemPosition();
                    double value;
                    try {
                        value = Double.parseDouble(weight.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        value = -1;
                    }
                    if (u == v || value < 0) {
                        Toast.makeText(this, R.string.dynamic_bad_input, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    append(DynamicEvent.add(nextTime(), u, v, value));
                }).show();
    }

    private void pickEdge(final boolean changeWeight) {
        if (!atEnd()) {
            return;
        }
        final DynamicGraph now = new Timeline(nodes.size(), events).at(events.size());
        final List<DynamicGraph.Edge> live = now.liveEdges();
        if (live.isEmpty()) {
            Toast.makeText(this, R.string.dynamic_no_edges, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[live.size()];
        for (int i = 0; i < items.length; i++) {
            DynamicGraph.Edge e = live.get(i);
            items[i] = getString(R.string.dynamic_edge_item, e.id(), nodes.get(e.u()).label(), nodes.get(e.v()).label(),
                    WeightFormat.format(e.weight()));
        }
        new MaterialAlertDialogBuilder(this).setTitle(changeWeight ? R.string.dynamic_weight : R.string.dynamic_remove)
                .setItems(items, (d, which) -> {
                    int id = live.get(which).id();
                    if (!changeWeight) {
                        append(DynamicEvent.remove(nextTime(), id));
                        return;
                    }
                    final EditText weight = new EditText(this);
                    weight.setHint(R.string.dynamic_weight_hint);
                    weight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.dynamic_weight).setView(weight)
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.dynamic_ok, (d2, w) -> {
                                try {
                                    double value = Double.parseDouble(weight.getText().toString().trim());
                                    if (value < 0) {
                                        throw new NumberFormatException();
                                    }
                                    append(DynamicEvent.setWeight(nextTime(), id, value));
                                } catch (NumberFormatException e) {
                                    Toast.makeText(this, R.string.dynamic_bad_input, Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                }).show();
    }
}

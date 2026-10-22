package com.graphlab.ui.online;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.GraphImporter;
import com.graphlab.data.AppExecutors;
import com.graphlab.databinding.ActivitySampleLibraryBinding;
import com.graphlab.engine.net.SampleLibrary;
import java.util.ArrayList;
import java.util.List;

/**
 * The sample library: nine ready-made graphs (road network, flow network, a graph that needs three
 * colours ...). It is a normal screen opened from Lab > Graphs with an explicit intent; Back
 * returns there. Adding a sample copies it into the user's saved graphs.
 */
public class SampleLibraryActivity extends AppCompatActivity implements PostAdapter.Listener, SampleLibraryViewModel.Listener {
    private ActivitySampleLibraryBinding binding;
    private SampleLibraryViewModel model;
    private PostAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySampleLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });

        model = new ViewModelProvider(this).get(SampleLibraryViewModel.class);
        adapter = new PostAdapter(this);
        binding.samplesList.setLayoutManager(new LinearLayoutManager(this));
        binding.samplesList.setAdapter(adapter);
        binding.samplesList.setItemAnimator(null);
        binding.samplesBack.setOnClickListener(v -> finish());
        binding.samplesRefresh.setOnClickListener(v -> model.load(this, true));
    }

    @Override
    protected void onStart() {
        super.onStart();
        model.setListener(this);
        model.load(this, false);
        render();
    }

    @Override
    protected void onStop() {
        model.setListener(null);
        super.onStop();
    }

    @Override
    public void onChanged() {
        render();
    }

    private void render() {
        SampleLibraryViewModel.State state = model.state();
        boolean loading = state == SampleLibraryViewModel.State.LOADING;
        binding.samplesProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.samplesRefresh.setEnabled(!loading);
        binding.samplesNote.setText(model.isOnline() ? getString(R.string.samples_note_online) : model.note());
        binding.samplesNote.setVisibility(state == SampleLibraryViewModel.State.READY ? View.VISIBLE : View.GONE);

        List<PostRow> rows = new ArrayList<>();
        for (SampleLibrary.Entry entry : model.entries()) {
            int nodes = entry.document().graph().nodeCount();
            int edges = entry.document().graph().edgeCount();
            rows.add(new PostRow(entry.id(), entry.title(),
                    getResources().getQuantityString(R.plurals.nodes, nodes, nodes) + " · "
                            + getResources().getQuantityString(R.plurals.edges, edges, edges),
                    entry.description(), entry.document(), false));
        }
        adapter.submitList(rows);
        String message = state == SampleLibraryViewModel.State.ERROR ? model.error() : "";
        binding.samplesMessage.setText(message);
        binding.samplesMessage.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onAdd(@NonNull PostRow row) {
        GraphImporter.add(GraphLabApp.container(this), row.document, row.title, new AppExecutors.Callback<String>() {
            @Override
            public void onSuccess(String name) {
                Snackbar.make(binding.getRoot(), getString(R.string.samples_added, name), Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                Snackbar.make(binding.getRoot(), R.string.samples_add_failed, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDelete(@NonNull PostRow row) {
        // The sample library has nothing to delete; rows are never marked as the user's own.
    }
}

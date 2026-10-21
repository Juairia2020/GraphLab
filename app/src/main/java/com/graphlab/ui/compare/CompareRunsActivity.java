package com.graphlab.ui.compare;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppExecutors;
import com.graphlab.databinding.ActivityCompareRunsBinding;
import com.graphlab.engine.experiment.ExperimentRecord;
import com.graphlab.provider.GraphContract;
import java.util.ArrayList;
import java.util.List;

/**
 * Compare runs (MS8): lists every saved experiment with its histogram, newest first, so two runs of the same graph (or
 * two graphs) can be compared by eye. This is the <em>second component</em> that uses the app's ContentProvider: it
 * never opens the database; everything arrives through {@link ContentResolver#query} on {@link GraphContract}'s URIs.
 * It also watches the provider's URI, so an experiment that finishes while this screen is open appears at once.
 */
public class CompareRunsActivity extends AppCompatActivity {
    private ActivityCompareRunsBinding binding;
    private CompareRunsAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            load();
        }
    };

    /** What one load of the provider produced. */
    private static final class Loaded {
        final List<CompareRunsAdapter.Row> rows;
        final int graphCount;

        Loaded(List<CompareRunsAdapter.Row> rows, int graphCount) {
            this.rows = rows;
            this.graphCount = graphCount;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityCompareRunsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        adapter = new CompareRunsAdapter();
        binding.compareList.setLayoutManager(new LinearLayoutManager(this));
        binding.compareList.setAdapter(adapter);
        binding.compareList.setItemAnimator(null);
        binding.compareBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(GraphContract.RUNS_URI, true, observer);
        load();
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(observer);
        super.onStop();
    }

    /** Queries the provider off the main thread and shows the result. */
    private void load() {
        final ContentResolver resolver = getContentResolver();
        final String unknownGraph = getString(R.string.experiment_graph_deleted);
        GraphLabApp.container(this).executors().disk(() -> {
            List<CompareRunsAdapter.Row> rows = new ArrayList<>();
            String[] columns = {GraphContract.Runs.ID, GraphContract.Runs.GRAPH_NAME, GraphContract.Runs.CREATED_AT,
                    GraphContract.Runs.SUMMARY, GraphContract.Runs.DETAIL};
            try (Cursor c = resolver.query(GraphContract.RUNS_URI, columns, GraphContract.Runs.ONLY_EXPERIMENTS, null, null)) {
                while (c != null && c.moveToNext()) {
                    ExperimentRecord record = null;
                    try {
                        record = ExperimentRecord.parse(c.getString(4));
                    } catch (Exception unreadable) {
                        // Show the row with its text summary and no chart.
                    }
                    rows.add(new CompareRunsAdapter.Row(c.getLong(0), c.isNull(1) ? unknownGraph : c.getString(1),
                            DateUtils.formatDateTime(CompareRunsActivity.this, c.getLong(2),
                                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL),
                            record == null ? c.getString(3) : record.summary(), record));
                }
            }
            int graphs = 0;
            try (Cursor c = resolver.query(GraphContract.GRAPHS_URI, new String[] {GraphContract.Graphs.ID}, null, null, null)) {
                graphs = c == null ? 0 : c.getCount();
            }
            return new Loaded(rows, graphs);
        }, new AppExecutors.Callback<Loaded>() {
            @Override
            public void onSuccess(Loaded loaded) {
                if (binding == null || isDestroyed()) {
                    return;
                }
                adapter.submitList(loaded.rows);
                binding.compareEmpty.setVisibility(loaded.rows.isEmpty() ? View.VISIBLE : View.GONE);
                binding.compareSource.setText(getString(R.string.compare_source, GraphContract.AUTHORITY,
                        getResources().getQuantityString(R.plurals.compare_runs_count, loaded.rows.size(), loaded.rows.size()),
                        getResources().getQuantityString(R.plurals.graphs_count, loaded.graphCount, loaded.graphCount)));
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                binding.compareSource.setText(R.string.compare_failed);
            }
        });
    }
}

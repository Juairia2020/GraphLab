package com.graphlab.ui.me;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.db.LogRecord;
import com.graphlab.databinding.ActivityLifecycleLogBinding;
import java.util.List;

/**
 * Shows the newest 200 Activity lifecycle events (created, started, resumed, paused, stopped,
 * destroyed ...) that {@code LifecycleLogger} recorded. Rotate the phone, switch tabs or press
 * Home, then come back here to see exactly what the system did. It is a normal screen, not a
 * tab, reached from Me with an explicit intent; Back returns to Me.
 */
public class LifecycleLogActivity extends AppCompatActivity {
    private static final int SHOW = 200;

    private ActivityLifecycleLogBinding binding;
    private LogAdapter adapter;
    private AppContainer container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityLifecycleLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        container = GraphLabApp.container(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });

        adapter = new LogAdapter();
        binding.logList.setLayoutManager(new LinearLayoutManager(this));
        binding.logList.setAdapter(adapter);
        binding.logList.setItemAnimator(null);

        binding.logBack.setOnClickListener(v -> finish());
        binding.logRefresh.setOnClickListener(v -> load());
        binding.logClear.setOnClickListener(v -> container.executors().disk(
                () -> container.repository().clearLogs(), new AppExecutors.Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer removed) {
                        load();
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Snackbar.make(binding.getRoot(), R.string.log_failed, Snackbar.LENGTH_LONG).show();
                    }
                }));
        load();
    }

    private void load() {
        container.executors().disk(() -> container.repository().recentLogs(SHOW),
                new AppExecutors.Callback<List<LogRecord>>() {
                    @Override
                    public void onSuccess(List<LogRecord> rows) {
                        if (binding == null) {
                            return;
                        }
                        adapter.submitList(rows);
                        binding.logEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(), R.string.log_failed, Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

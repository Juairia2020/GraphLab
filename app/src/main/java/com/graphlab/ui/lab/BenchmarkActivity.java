package com.graphlab.ui.lab;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.databinding.ActivityBenchmarkBinding;
import com.graphlab.engine.bench.Benchmark;
import com.graphlab.service.BenchmarkMonitor;
import com.graphlab.service.BenchmarkService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows the benchmark (MS15). The measuring is done by {@link BenchmarkService}; this screen only draws what the monitor reports. */
public class BenchmarkActivity extends AppCompatActivity {
    private ActivityBenchmarkBinding binding;
    private final BenchmarkMonitor.Listener listener = this::render;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityBenchmarkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        binding.benchBack.setOnClickListener(v -> finish());
        binding.benchStart.setOnClickListener(v -> BenchmarkService.start(this));
        binding.benchStop.setOnClickListener(v -> BenchmarkService.stop(this));
        BenchmarkMonitor.Snapshot now = BenchmarkMonitor.get().current();
        if (now.running || now.result != null) {
            render(now);
        } else {
            showSaved();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        BenchmarkMonitor.get().addListener(listener);
        BenchmarkMonitor.Snapshot now = BenchmarkMonitor.get().current();
        if (now.running) {
            render(now);
        }
    }

    @Override
    protected void onStop() {
        BenchmarkMonitor.get().removeListener(listener);
        super.onStop();
    }

    /** The points saved by the last finished run, read off the main thread. */
    private void showSaved() {
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> container.repository().progressValue(BenchmarkService.PROGRESS_KEY),
                new AppExecutors.Callback<String>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onSuccess(@Nullable String text) {
                        List<List<? extends Number>> pair = BenchmarkService.decode(text);
                        if (pair.get(0).size() < 2) {
                            return;
                        }
                        List<Integer> ns = new ArrayList<>();
                        List<Double> ms = new ArrayList<>();
                        for (int i = 0; i < pair.get(0).size(); i++) {
                            ns.add(pair.get(0).get(i).intValue());
                            ms.add(pair.get(1).get(i).doubleValue());
                        }
                        show(Benchmark.fit(ns, ms, false), ns, ms);
                    }

                    @Override
                    public void onFailure(@NonNull Exception error) {
                        // nothing saved: the idle text stays
                    }
                });
    }

    private void render(@NonNull BenchmarkMonitor.Snapshot s) {
        binding.benchStart.setEnabled(!s.running);
        binding.benchStop.setEnabled(s.running);
        if (s.running) {
            binding.benchChart.setData(s.ns, s.ms, Double.NaN);
            binding.benchStatus.setText(getString(R.string.benchmark_running, Math.min(s.ns.size() + 1, s.total), s.total));
        } else if (s.result != null) {
            show(s.result, s.ns, s.ms);
        }
    }

    private void show(Benchmark.Result r, List<Integer> ns, List<Double> ms) {
        binding.benchChart.setData(ns, ms, r.scale());
        binding.benchStatus.setText(r.cancelled() ? getString(R.string.benchmark_stopped) + "\n" + summary(r) : summary(r));
    }

    private String summary(Benchmark.Result r) {
        return getString(R.string.benchmark_done, String.format(Locale.US, "%.2e", r.scale()),
                String.format(Locale.US, "%.3f", r.rSquared()), String.format(Locale.US, "%.0f%%", r.maxRelativeError() * 100));
    }
}

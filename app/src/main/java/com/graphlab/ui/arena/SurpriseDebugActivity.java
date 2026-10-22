package com.graphlab.ui.arena;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.slider.Slider;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppContainer;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.prefs.SurprisePrefs;
import com.graphlab.databinding.ActivitySurpriseDebugBinding;
import com.graphlab.engine.arena.NeedModel;
import com.graphlab.engine.arena.QuestionKind;
import com.graphlab.engine.arena.SurpriseEngine;
import com.graphlab.engine.progress.Mastery;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * The debug screen of the Surprise Engine v2 (MS12): every activity with the parts of its score (need, mastery, novelty,
 * due), the score, the chance of being drawn next and the time a question takes. The four weights are sliders; the table
 * is computed again at every move, and the weights are saved so the Arena uses them. Used to tune the starting guesses
 * 0.4, 0.25, 0.25 and 0.5.
 */
public class SurpriseDebugActivity extends AppCompatActivity {

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, SurpriseDebugActivity.class);
    }

    private ActivitySurpriseDebugBinding binding;
    private NeedModel needs = NeedModel.neutral();
    private Mastery mastery = new Mastery();
    private SurpriseEngine.Weights weights;
    private boolean quiet;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySurpriseDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });
        weights = SurprisePrefs.load(this);
        binding.debugBack.setOnClickListener(v -> finish());
        binding.debugReset.setOnClickListener(v -> {
            weights = SurpriseEngine.Weights.DEFAULT;
            SurprisePrefs.reset(this);
            showWeights();
            render();
        });
        Slider.OnChangeListener changed = (slider, value, fromUser) -> {
            if (quiet || !fromUser) {
                return;
            }
            weights = new SurpriseEngine.Weights(binding.weightNeed.getValue(), binding.weightWeakness.getValue(),
                    binding.weightNovelty.getValue(), binding.weightDue.getValue());
            SurprisePrefs.save(this, weights);
            showLabels();
            render();
        };
        binding.weightNeed.addOnChangeListener(changed);
        binding.weightWeakness.addOnChangeListener(changed);
        binding.weightNovelty.addOnChangeListener(changed);
        binding.weightDue.addOnChangeListener(changed);
        showWeights();
        render();
        load();
    }

    /** The attempts table and the mastery record, read off the main thread. */
    private void load() {
        final AppContainer container = GraphLabApp.container(this);
        container.executors().disk(() -> new NeedModel(container.repository().activityStats(),
                container.repository().topicStats()), new AppExecutors.Callback<NeedModel>() {
            @Override
            public void onSuccess(NeedModel result) {
                needs = result;
                mastery = container.progress().masteryCopy();
                render();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                render();
            }
        });
    }

    private void showWeights() {
        quiet = true;
        binding.weightNeed.setValue((float) weights.need());
        binding.weightWeakness.setValue((float) weights.weakness());
        binding.weightNovelty.setValue((float) weights.novelty());
        binding.weightDue.setValue((float) weights.due());
        quiet = false;
        showLabels();
    }

    private void showLabels() {
        binding.weightNeedLabel.setText(getString(R.string.debug_weight_value, getString(R.string.debug_weight_need), num(weights.need())));
        binding.weightWeaknessLabel.setText(getString(R.string.debug_weight_value, getString(R.string.debug_weight_weakness), num(weights.weakness())));
        binding.weightNoveltyLabel.setText(getString(R.string.debug_weight_value, getString(R.string.debug_weight_novelty), num(weights.novelty())));
        binding.weightDueLabel.setText(getString(R.string.debug_weight_value, getString(R.string.debug_weight_due), num(weights.due())));
    }

    private void render() {
        SurpriseEngine engine = new SurpriseEngine(needs, mastery, weights, Instant.now());
        List<SurpriseEngine.Row> rows = engine.explain(List.of(QuestionKind.values()), null);
        binding.debugRows.removeAllViews();
        for (SurpriseEngine.Row row : rows) {
            TextView line = new TextView(this);
            line.setTypeface(Typeface.MONOSPACE);
            line.setTextSize(12f);
            line.setPadding(0, 6, 0, 6);
            line.setText(getString(R.string.debug_row, row.kind().title(), num(row.need()), num(row.mastery()),
                    num(row.novelty()), num(row.due()), num(row.score()),
                    String.format(Locale.US, "%.1f%%", row.probability() * 100.0), row.seconds()));
            binding.debugRows.addView(line);
        }
    }

    private static String num(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}

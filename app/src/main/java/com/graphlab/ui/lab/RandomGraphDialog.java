package com.graphlab.ui.lab;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.graphlab.R;
import com.graphlab.engine.experiment.GraphGenerator;
import com.graphlab.engine.io.GraphDocument;

/**
 * "Make a random graph" (MS10, Experiment): the person sets the nodes, the density, the weight range and a seed, and
 * {@link GraphGenerator} makes the graph. The same numbers always give the same graph. A number that is out of range is
 * shown on its field and the dialog stays open. A line under the fields says how many edges the graph will have.
 */
public final class RandomGraphDialog {

    public interface Callback {
        void onGraph(@NonNull GraphDocument document);
    }

    private RandomGraphDialog() {
    }

    @NonNull
    public static AlertDialog show(@NonNull Context context, @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_random_graph, null, false);
        TextInputLayout nodesLayout = view.findViewById(R.id.random_nodes_layout);
        TextInputLayout densityLayout = view.findViewById(R.id.random_density_layout);
        TextInputLayout minLayout = view.findViewById(R.id.random_min_layout);
        TextInputLayout maxLayout = view.findViewById(R.id.random_max_layout);
        TextInputLayout seedLayout = view.findViewById(R.id.random_seed_layout);
        EditText nodes = view.findViewById(R.id.random_nodes_input);
        EditText density = view.findViewById(R.id.random_density_input);
        EditText min = view.findViewById(R.id.random_min_input);
        EditText max = view.findViewById(R.id.random_max_input);
        EditText seed = view.findViewById(R.id.random_seed_input);
        TextView summary = view.findViewById(R.id.random_summary);
        nodes.setText("10");
        density.setText("30");
        min.setText("1");
        max.setText("20");
        seed.setText("1");

        TextWatcher refresh = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Integer n = number(nodes);
                Integer d = number(density);
                if (n != null && d != null && n >= GraphGenerator.MIN_NODES && n <= GraphGenerator.MAX_NODES && d >= 0 && d <= 100) {
                    summary.setText(context.getString(R.string.random_summary, n, GraphGenerator.edgeCount(n, d)));
                } else {
                    summary.setText("");
                }
            }
        };
        nodes.addTextChangedListener(refresh);
        density.addTextChangedListener(refresh);
        refresh.afterTextChanged(null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.random_graph_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.random_graph_make, null)   // real handler set after show()
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            for (TextInputLayout layout : new TextInputLayout[] {nodesLayout, densityLayout, minLayout, maxLayout, seedLayout}) {
                layout.setError(null);
            }
            Integer n = number(nodes);
            Integer d = number(density);
            Integer lo = number(min);
            Integer hi = number(max);
            Long s = longNumber(seed);
            boolean ok = true;
            if (n == null) {
                nodesLayout.setError(context.getString(R.string.random_need_number));
                ok = false;
            }
            if (d == null) {
                densityLayout.setError(context.getString(R.string.random_need_number));
                ok = false;
            }
            if (lo == null) {
                minLayout.setError(context.getString(R.string.random_need_number));
                ok = false;
            }
            if (hi == null) {
                maxLayout.setError(context.getString(R.string.random_need_number));
                ok = false;
            }
            if (s == null) {
                seedLayout.setError(context.getString(R.string.random_need_number));
                ok = false;
            }
            if (!ok) {
                return;
            }
            try {
                GraphDocument document = GraphGenerator.generate(n, d, lo, hi, s);
                dialog.dismiss();
                callback.onGraph(document);
            } catch (IllegalArgumentException e) {
                // The engine's message names the field in plain words; show it on the field it is about.
                String message = e.getMessage() == null ? context.getString(R.string.random_need_number) : e.getMessage();
                if (message.startsWith("Nodes")) {
                    nodesLayout.setError(message);
                } else if (message.startsWith("Density")) {
                    densityLayout.setError(message);
                } else if (message.contains("smallest")) {
                    minLayout.setError(message);
                } else {
                    maxLayout.setError(message);
                }
            }
        });
        return dialog;
    }

    private static Integer number(EditText field) {
        try {
            return Integer.valueOf(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longNumber(EditText field) {
        try {
            return Long.valueOf(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

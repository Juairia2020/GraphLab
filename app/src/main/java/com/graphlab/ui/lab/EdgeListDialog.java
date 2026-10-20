package com.graphlab.ui.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.graphlab.R;
import com.graphlab.engine.io.EdgeListParser;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphNames;

/**
 * "Type a graph" (MS10): the person types one edge per line, and {@link EdgeListParser} turns the text into a graph.
 * A mistake is shown on the field with its line number, and the dialog stays open so the text can be fixed.
 */
public final class EdgeListDialog {

    public interface Callback {
        /** A graph that parsed; {@code name} is the cleaned name the person typed. */
        void onGraph(@NonNull GraphDocument document, @NonNull String name);
    }

    private EdgeListDialog() {
    }

    @NonNull
    public static AlertDialog show(@NonNull Context context, @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edge_list, null, false);
        TextInputLayout nameLayout = view.findViewById(R.id.edge_name_layout);
        TextInputLayout textLayout = view.findViewById(R.id.edge_text_layout);
        EditText nameInput = view.findViewById(R.id.edge_name_input);
        EditText textInput = view.findViewById(R.id.edge_text_input);
        CheckBox directed = view.findViewById(R.id.edge_directed);
        nameInput.setText(context.getString(R.string.edge_list_default_name));
        view.findViewById(R.id.edge_example).setOnClickListener(v -> {
            textInput.setText(EdgeListParser.EXAMPLE);
            textLayout.setError(null);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edge_list_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.edge_list_import, null)   // real handler set after show()
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            nameLayout.setError(null);
            textLayout.setError(null);
            String typedName = nameInput.getText().toString();
            GraphNames.Check check = GraphNames.check(typedName);
            if (check != GraphNames.Check.OK) {
                nameLayout.setError(context.getString(check == GraphNames.Check.EMPTY
                        ? R.string.name_error_empty : R.string.name_error_long, GraphNames.MAX_LENGTH));
                return;
            }
            try {
                GraphDocument document = EdgeListParser.parse(textInput.getText().toString(), directed.isChecked());
                dialog.dismiss();
                callback.onGraph(document, GraphNames.clean(typedName));
            } catch (GraphFormatException e) {
                textLayout.setError(e.getMessage());
            }
        });
        return dialog;
    }
}

package com.graphlab.ui.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.graphlab.R;
import com.graphlab.engine.io.GraphNames;

/**
 * Asks for a graph name. The text is checked with {@link GraphNames#check}: empty or longer than
 * 40 characters shows an error under the field and the dialog stays open.
 */
public final class NameDialog {

    public interface Callback {
        /** The cleaned name (trimmed, single spaces). */
        void onName(@NonNull String name);
    }

    private NameDialog() {
    }

    @NonNull
    public static AlertDialog show(@NonNull Context context, @StringRes int title, @NonNull String initial,
                                   @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_name, null, false);
        TextInputLayout layout = view.findViewById(R.id.name_layout);
        EditText input = view.findViewById(R.id.name_input);
        input.setText(initial);
        input.selectAll();

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)   // real handler set after show()
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = input.getText().toString();
            GraphNames.Check check = GraphNames.check(typed);
            if (check == GraphNames.Check.OK) {
                dialog.dismiss();
                callback.onName(GraphNames.clean(typed));
            } else {
                layout.setError(context.getString(check == GraphNames.Check.EMPTY
                        ? R.string.name_error_empty : R.string.name_error_long, GraphNames.MAX_LENGTH));
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        return dialog;
    }
}

package com.graphlab.ui.lab;

import android.content.Context;
import android.content.DialogInterface;
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
import com.graphlab.engine.edit.WeightFormat;

/**
 * Asks for an edge weight. The text is checked with {@link WeightFormat#parse}: a bad value
 * shows an error under the field and the dialog stays open instead of closing.
 */
public final class WeightDialog {

    /** What the screen does with the answer. */
    public interface Callback {
        void onWeight(double weight);

        /** Only called when the dialog was shown with a delete button. */
        void onDelete();
    }

    private WeightDialog() {
    }

    /**
     * @param title        for example "Edge A-B"
     * @param initial      the weight to start with
     * @param allowDelete  true when editing an existing edge (adds a Delete edge button)
     * @return the dialog, so the Activity can dismiss it in onDestroy (a dialog left open
     *         through rotation would leak its window)
     */
    @NonNull
    public static AlertDialog show(@NonNull Context context, @NonNull String title, double initial,
                            boolean allowDelete, @NonNull Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_weight, null, false);
        TextInputLayout layout = view.findViewById(R.id.weight_layout);
        EditText input = view.findViewById(R.id.weight_input);
        input.setText(WeightFormat.format(initial));
        input.selectAll();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null);   // real handler set after show()
        if (allowDelete) {
            builder.setNeutralButton(R.string.action_delete_edge,
                    (DialogInterface dialog, int which) -> callback.onDelete());
        }
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();

        // Set after show() so a bad value does not dismiss the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            WeightFormat.Parsed parsed = WeightFormat.parse(input.getText().toString());
            if (parsed.isValid()) {
                dialog.dismiss();
                callback.onWeight(parsed.value());
            } else {
                layout.setError(context.getString(errorText(parsed.error())));
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        return dialog;
    }

    @StringRes
    private static int errorText(WeightFormat.Error error) {
        switch (error) {
            case EMPTY:
                return R.string.weight_error_empty;
            case TOO_LARGE:
                return R.string.weight_error_large;
            default:
                return R.string.weight_error_number;
        }
    }
}

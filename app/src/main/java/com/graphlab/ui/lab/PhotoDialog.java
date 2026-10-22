package com.graphlab.ui.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.graphlab.R;
import com.graphlab.engine.io.Background;

/**
 * The "Photo" dialog of Build (MS10 S6): choose or replace the photo, set its opacity, lock it, or remove it. A locked photo
 * keeps its opacity and cannot be replaced or removed, so a stray tap cannot lose a traced layout's background. The dialog
 * shows the state of {@link LabViewModel} and changes it live, so the canvas behind the dialog updates as the slider moves.
 */
public final class PhotoDialog {

    public interface Actions {
        /** The person wants to pick a photo; the caller opens the system picker. */
        void onChoose();
    }

    private PhotoDialog() {
    }

    @NonNull
    public static AlertDialog show(@NonNull Context context, @NonNull LabViewModel model, @NonNull Actions actions) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_photo, null, false);
        MaterialButton choose = view.findViewById(R.id.photo_choose);
        MaterialButton remove = view.findViewById(R.id.photo_remove);
        Slider opacity = view.findViewById(R.id.photo_opacity);
        TextView opacityLabel = view.findViewById(R.id.photo_opacity_label);
        MaterialSwitch lock = view.findViewById(R.id.photo_lock);

        final boolean[] updating = {false};
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                Background bg = model.background();
                boolean has = bg != null;
                boolean locked = has && bg.locked();
                updating[0] = true;
                choose.setText(has ? R.string.photo_replace : R.string.photo_choose);
                choose.setEnabled(!locked);
                remove.setEnabled(has && !locked);
                opacity.setEnabled(has && !locked);
                lock.setEnabled(has);
                lock.setChecked(locked);
                int percent = has ? (int) Math.round(bg.opacity() * 100) : 50;
                percent = Math.max(5, Math.min(100, Math.round(percent / 5f) * 5));
                opacity.setValue(percent);
                opacityLabel.setText(context.getString(R.string.photo_opacity_label, percent));
                opacityLabel.setVisibility(has ? View.VISIBLE : View.GONE);
                opacity.setVisibility(has ? View.VISIBLE : View.GONE);
                lock.setVisibility(has ? View.VISIBLE : View.GONE);
                remove.setVisibility(has ? View.VISIBLE : View.GONE);
                updating[0] = false;
            }
        };

        choose.setOnClickListener(v -> actions.onChoose());
        view.findViewById(R.id.photo_convert).setOnClickListener(v -> context.startActivity(
                new android.content.Intent(context, com.graphlab.ui.imports.ImportActivity.class)));
        remove.setOnClickListener(v -> model.removePhoto());
        lock.setOnCheckedChangeListener((button, checked) -> {
            if (!updating[0]) {
                model.setPhotoLocked(checked);
            }
        });
        opacity.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !updating[0]) {
                model.setPhotoOpacity(value / 100.0);
            }
        });
        model.addPhotoListener(refresh);
        refresh.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.photo_title)
                .setView(view)
                .setPositiveButton(R.string.photo_done, null)
                .create();
        dialog.setOnDismissListener(d -> model.removePhotoListener(refresh));
        dialog.show();
        return dialog;
    }
}

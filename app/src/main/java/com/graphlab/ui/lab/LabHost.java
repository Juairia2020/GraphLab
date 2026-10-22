package com.graphlab.ui.lab;

import androidx.annotation.NonNull;

/** What the Lab's fragments can ask of their Activity: switch to another mode. */
public interface LabHost {
    void showMode(@NonNull LabMode mode);
}

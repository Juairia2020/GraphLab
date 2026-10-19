package com.graphlab.data.db;

import androidx.annotation.NonNull;

/** The result of a save: which row, and the name it ended up with (an automatic name is chosen while saving). */
public final class SavedGraph {
    public final long id;
    @NonNull
    public final String name;

    public SavedGraph(long id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }
}

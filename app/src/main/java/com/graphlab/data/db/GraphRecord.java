package com.graphlab.data.db;

import androidx.annotation.NonNull;

/** A saved graph with its JSON text, as stored. */
public final class GraphRecord {
    public final long id;
    @NonNull
    public final String name;
    @NonNull
    public final String json;

    public GraphRecord(long id, @NonNull String name, @NonNull String json) {
        this.id = id;
        this.name = name;
        this.json = json;
    }
}

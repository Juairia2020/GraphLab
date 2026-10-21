package com.graphlab.ui.online;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.io.GraphDocument;

/**
 * One line of either online list, as the shared adapter draws it: a sample from the library or a
 * graph shared in the cloud. Plain data, so the adapter does not need to know which list it shows.
 */
final class PostRow {
    final String id;
    final String title;
    /** Second line: node and edge counts, plus the author and age for cloud posts. */
    final String meta;
    final String description;
    final GraphDocument document;
    /** True if the signed-in user published it, which enables Delete. */
    final boolean mine;

    PostRow(@NonNull String id, @NonNull String title, @NonNull String meta, @Nullable String description,
            @NonNull GraphDocument document, boolean mine) {
        this.id = id;
        this.title = title;
        this.meta = meta;
        this.description = description == null ? "" : description;
        this.document = document;
        this.mine = mine;
    }
}

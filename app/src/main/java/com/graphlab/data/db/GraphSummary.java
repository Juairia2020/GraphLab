package com.graphlab.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One row of the library list: what the screen needs to show a saved graph. Immutable. */
public final class GraphSummary {
    public final long id;
    @NonNull
    public final String name;
    @NonNull
    public final String json;
    public final long updatedAt;
    public final int nodeCount;
    public final int edgeCount;
    public final int runCount;
    @Nullable
    public final String lastAlgorithm;

    public GraphSummary(long id, @NonNull String name, @NonNull String json, long updatedAt, int nodeCount,
                        int edgeCount, int runCount, @Nullable String lastAlgorithm) {
        this.id = id;
        this.name = name;
        this.json = json;
        this.updatedAt = updatedAt;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.runCount = runCount;
        this.lastAlgorithm = lastAlgorithm;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphSummary)) {
            return false;
        }
        GraphSummary that = (GraphSummary) other;
        return id == that.id && updatedAt == that.updatedAt && nodeCount == that.nodeCount
                && edgeCount == that.edgeCount && runCount == that.runCount && name.equals(that.name)
                && json.equals(that.json)
                && (lastAlgorithm == null ? that.lastAlgorithm == null : lastAlgorithm.equals(that.lastAlgorithm));
    }

    @Override
    public int hashCode() {
        int h = Long.hashCode(id);
        h = 31 * h + Long.hashCode(updatedAt);
        h = 31 * h + name.hashCode();
        return 31 * h + runCount;
    }
}

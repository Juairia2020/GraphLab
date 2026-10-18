package com.graphlab.data.db;

import androidx.annotation.NonNull;

/** A finished experiment as the Experiment screen shows it: which graph, when, the one-line summary and the histogram JSON. */
public final class ExperimentRunRecord {
    public final long id;
    @NonNull
    public final String graphName;
    public final long createdAt;
    @NonNull
    public final String summary;
    @NonNull
    public final String detail;

    public ExperimentRunRecord(long id, @NonNull String graphName, long createdAt, @NonNull String summary, @NonNull String detail) {
        this.id = id;
        this.graphName = graphName;
        this.createdAt = createdAt;
        this.summary = summary;
        this.detail = detail;
    }
}

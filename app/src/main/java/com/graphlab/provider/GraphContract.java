package com.graphlab.provider;

import android.content.ContentUris;
import android.net.Uri;
import androidx.annotation.NonNull;

/**
 * The public face of {@link GraphProvider}: the authority, the addresses (URIs) and the column names a client
 * needs. A client never needs to know the database.
 *
 * <pre>
 * content://com.graphlab.provider/graphs        all saved graphs (without the graph text)
 * content://com.graphlab.provider/graphs/7      one graph, with its JSON
 * content://com.graphlab.provider/runs          all runs and experiments, newest first, with the graph's name
 * </pre>
 *
 * Read only. Experiments are the runs whose {@link Runs#DETAIL} is not null.
 */
public final class GraphContract {
    public static final String AUTHORITY = "com.graphlab.provider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri GRAPHS_URI = Uri.withAppendedPath(BASE_URI, "graphs");
    public static final Uri RUNS_URI = Uri.withAppendedPath(BASE_URI, "runs");

    public static final String TYPE_GRAPH_DIR = "vnd.android.cursor.dir/vnd.com.graphlab.graph";
    public static final String TYPE_GRAPH_ITEM = "vnd.android.cursor.item/vnd.com.graphlab.graph";
    public static final String TYPE_RUN_DIR = "vnd.android.cursor.dir/vnd.com.graphlab.run";

    private GraphContract() {
    }

    @NonNull
    public static Uri graphUri(long id) {
        return ContentUris.withAppendedId(GRAPHS_URI, id);
    }

    /** Columns of the graphs URIs. {@link #JSON} is only filled for a single graph. */
    public static final class Graphs {
        public static final String ID = "_id";
        public static final String NAME = "name";
        public static final String NODE_COUNT = "node_count";
        public static final String EDGE_COUNT = "edge_count";
        public static final String UPDATED_AT = "updated_at";
        public static final String JSON = "json";

        private Graphs() {
        }
    }

    /** Columns of the runs URI. */
    public static final class Runs {
        public static final String ID = "_id";
        public static final String GRAPH_ID = "graph_id";
        public static final String GRAPH_NAME = "graph_name";
        public static final String ALGORITHM_ID = "algorithm_id";
        public static final String CREATED_AT = "created_at";
        public static final String STEP_COUNT = "step_count";
        public static final String SUMMARY = "summary";
        public static final String DETAIL = "detail";
        /** Use as the selection to list only experiments. */
        public static final String ONLY_EXPERIMENTS = DETAIL + " IS NOT NULL";

        private Runs() {
        }
    }
}

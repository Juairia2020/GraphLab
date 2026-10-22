package com.graphlab.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.GraphLabApp;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Serves saved graphs and runs to other components through a {@code ContentResolver} (MS8, checklist item 10).
 * The Compare-runs screen is the client: it never opens the database itself.
 *
 * <p><b>Query only.</b> insert, update and delete throw, so no client can change data through here. The
 * provider is not exported (manifest), so only this app can reach it; to share it with another app of
 * yours, export it behind a signature-level permission.
 *
 * <p><b>Safe by construction.</b> Clients may pass a selection and a sort order, so the query builder runs in strict
 * mode with a projection map: only the listed columns can be requested, the selection is checked to be a plain WHERE
 * clause (it cannot close the clause early and add more SQL), values travel as bound arguments, and the sort order
 * must look like {@code column [ASC|DESC]}. Graphs that were swiped away (waiting for Undo) never appear.
 */
public final class GraphProvider extends ContentProvider {
    private static final int GRAPHS = 1;
    private static final int GRAPH_ID = 2;
    private static final int RUNS = 3;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(GraphContract.AUTHORITY, "graphs", GRAPHS);
        MATCHER.addURI(GraphContract.AUTHORITY, "graphs/#", GRAPH_ID);
        MATCHER.addURI(GraphContract.AUTHORITY, "runs", RUNS);
    }

    private static final Pattern SORT = Pattern.compile("[A-Za-z_.]+( (?i:ASC|DESC))?(, ?[A-Za-z_.]+( (?i:ASC|DESC))?)*");

    private static final Map<String, String> GRAPH_LIST_COLUMNS = new HashMap<>();
    private static final Map<String, String> GRAPH_ITEM_COLUMNS = new HashMap<>();
    private static final Map<String, String> RUN_COLUMNS = new HashMap<>();

    static {
        GRAPH_LIST_COLUMNS.put(GraphContract.Graphs.ID, "_id");
        GRAPH_LIST_COLUMNS.put(GraphContract.Graphs.NAME, "name");
        GRAPH_LIST_COLUMNS.put(GraphContract.Graphs.NODE_COUNT, "node_count");
        GRAPH_LIST_COLUMNS.put(GraphContract.Graphs.EDGE_COUNT, "edge_count");
        GRAPH_LIST_COLUMNS.put(GraphContract.Graphs.UPDATED_AT, "updated_at");
        GRAPH_ITEM_COLUMNS.putAll(GRAPH_LIST_COLUMNS);
        GRAPH_ITEM_COLUMNS.put(GraphContract.Graphs.JSON, "json");

        RUN_COLUMNS.put(GraphContract.Runs.ID, "runs._id AS _id");
        RUN_COLUMNS.put(GraphContract.Runs.GRAPH_ID, "runs.graph_id AS graph_id");
        RUN_COLUMNS.put(GraphContract.Runs.GRAPH_NAME, "graphs.name AS graph_name");
        RUN_COLUMNS.put(GraphContract.Runs.ALGORITHM_ID, "runs.algorithm_id AS algorithm_id");
        RUN_COLUMNS.put(GraphContract.Runs.CREATED_AT, "runs.created_at AS created_at");
        RUN_COLUMNS.put(GraphContract.Runs.STEP_COUNT, "runs.step_count AS step_count");
        RUN_COLUMNS.put(GraphContract.Runs.SUMMARY, "runs.summary AS summary");
        RUN_COLUMNS.put(GraphContract.Runs.DETAIL, "runs.detail AS detail");
    }

    @Override
    public boolean onCreate() {
        return true;   // the database is opened by the first query, not here
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (getContext() == null) {
            return null;
        }
        if (sortOrder != null && !SORT.matcher(sortOrder).matches()) {
            throw new IllegalArgumentException("Unsupported sort order: " + sortOrder);
        }
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        qb.setStrictColumns(true);
        String defaultSort;
        switch (MATCHER.match(uri)) {
            case GRAPHS:
                qb.setTables("graphs");
                qb.setProjectionMap(GRAPH_LIST_COLUMNS);
                qb.appendWhere("deleted_at IS NULL");
                defaultSort = "updated_at DESC, _id DESC";
                break;
            case GRAPH_ID:
                qb.setTables("graphs");
                qb.setProjectionMap(GRAPH_ITEM_COLUMNS);
                qb.appendWhere("deleted_at IS NULL AND _id = " + ContentUris.parseId(uri));
                defaultSort = null;
                break;
            case RUNS:
                qb.setTables("runs LEFT JOIN graphs ON runs.graph_id = graphs._id");
                qb.setProjectionMap(RUN_COLUMNS);
                qb.appendWhere("(graphs._id IS NULL OR graphs.deleted_at IS NULL)");
                defaultSort = "runs.created_at DESC, runs._id DESC";
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        SQLiteDatabase db = GraphLabApp.container(getContext()).repository().readableDatabase();
        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null,
                sortOrder == null || sortOrder.isEmpty() ? defaultSort : sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case GRAPHS:
                return GraphContract.TYPE_GRAPH_DIR;
            case GRAPH_ID:
                return GraphContract.TYPE_GRAPH_ITEM;
            case RUNS:
                return GraphContract.TYPE_RUN_DIR;
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("GraphProvider is read only");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("GraphProvider is read only");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("GraphProvider is read only");
    }
}

package com.graphlab.data.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.io.GraphNames;
import java.util.ArrayList;
import java.util.List;

/**
 * Every read and write of saved graphs, runs and the lifecycle log. Plain synchronous methods:
 * call them only from the disk executor ({@code AppExecutors.disk}). All SQL uses bound
 * parameters, never string concatenation, so a graph named {@code '); DROP TABLE graphs;--} is
 * just a graph with an odd name.
 *
 * <p>This is where "database manipulation" lives: insert, update, query, soft delete with Undo,
 * and delete with cascade.
 */
public final class GraphRepository {
    /** The library list. Counts runs and finds the latest one with sub-selects, so it is one query. */
    static final String LIST_SQL = "SELECT g._id, g.name, g.json, g.updated_at, g.node_count, g.edge_count, "
            + "(SELECT COUNT(*) FROM runs r WHERE r.graph_id = g._id) AS run_count, "
            + "(SELECT r.algorithm_id FROM runs r WHERE r.graph_id = g._id "
            + "ORDER BY r.created_at DESC, r._id DESC LIMIT 1) AS last_algorithm "
            + "FROM graphs g WHERE g.deleted_at IS NULL ORDER BY g.updated_at DESC, g._id DESC";

    private final GraphDb helper;

    public GraphRepository(@NonNull GraphDb helper) {
        this.helper = helper;
    }

    // ---- graphs ------------------------------------------------------------------------

    /** Saved graphs, most recently changed first. Graphs waiting for Undo are not included. */
    @NonNull
    public List<GraphSummary> listGraphs() {
        List<GraphSummary> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(LIST_SQL, null)) {
            while (c.moveToNext()) {
                out.add(new GraphSummary(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                        c.getInt(4), c.getInt(5), c.getInt(6), c.isNull(7) ? null : c.getString(7)));
            }
        }
        return out;
    }

    /** The names of all saved graphs, for choosing an unused name. */
    @NonNull
    public List<String> graphNames() {
        List<String> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT name FROM graphs WHERE deleted_at IS NULL", null)) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return out;
    }

    public int graphCount() {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM graphs WHERE deleted_at IS NULL", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** One graph by id, or null if there is none (or it was deleted). */
    @Nullable
    public GraphRecord getGraph(long id) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT _id, name, json FROM graphs WHERE _id = ? AND deleted_at IS NULL",
                new String[] {Long.toString(id)})) {
            return c.moveToFirst() ? new GraphRecord(c.getLong(0), c.getString(1), c.getString(2)) : null;
        }
    }

    /** Adds a graph and returns its new id. The name is stored as given. */
    public long insertGraph(@NonNull String name, @NonNull String json, int nodeCount, int edgeCount, long now) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("json", json);
        values.put("updated_at", now);
        values.put("node_count", nodeCount);
        values.put("edge_count", edgeCount);
        return helper.getWritableDatabase().insertOrThrow("graphs", null, values);
    }

    /**
     * Saves the graph being edited. With {@code id} 0 this adds a new row, named {@code name} or, if
     * that is null, "Untitled graph" (made unique). With an id it changes that row (keeping its
     * name unless {@code name} is given). If the row is gone (deleted from the library meanwhile) a
     * new one is added instead, so an edit is never lost.
     */
    @NonNull
    public SavedGraph saveGraph(long id, @Nullable String name, @NonNull String json, int nodeCount,
                                int edgeCount, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            SavedGraph result = null;
            if (id != 0) {
                ContentValues values = new ContentValues();
                values.put("json", json);
                values.put("updated_at", now);
                values.put("node_count", nodeCount);
                values.put("edge_count", edgeCount);
                if (name != null) {
                    values.put("name", name);
                }
                int rows = db.update("graphs", values, "_id = ? AND deleted_at IS NULL", new String[] {Long.toString(id)});
                if (rows > 0) {
                    GraphRecord record = getGraph(id);
                    result = new SavedGraph(id, record == null ? (name == null ? "" : name) : record.name);
                }
            }
            if (result == null) {
                String finalName = name != null ? name : GraphNames.untitled(graphNames());
                result = new SavedGraph(insertGraph(finalName, json, nodeCount, edgeCount, now), finalName);
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    public boolean renameGraph(long id, @NonNull String name, long now) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("updated_at", now);
        return helper.getWritableDatabase().update("graphs", values, "_id = ? AND deleted_at IS NULL",
                new String[] {Long.toString(id)}) > 0;
    }

    /** Hides a graph (deleted = true) or brings it back (false). Nothing is lost until {@link #purge}. */
    public boolean setDeleted(long id, boolean deleted, long now) {
        ContentValues values = new ContentValues();
        if (deleted) {
            values.put("deleted_at", now);
        } else {
            values.putNull("deleted_at");
        }
        return helper.getWritableDatabase().update("graphs", values, "_id = ?", new String[] {Long.toString(id)}) > 0;
    }

    /** Removes a hidden graph for good, and its runs with it. Does nothing to a graph that is not hidden. */
    public boolean purge(long id) {
        return helper.getWritableDatabase().delete("graphs", "_id = ? AND deleted_at IS NOT NULL",
                new String[] {Long.toString(id)}) > 0;
    }

    /** Removes every hidden graph. Called at start-up, in case the app was closed while an Undo bar was showing. */
    public int purgeAllDeleted() {
        return helper.getWritableDatabase().delete("graphs", "deleted_at IS NOT NULL", null);
    }

    // ---- runs --------------------------------------------------------------------------

    /** Records one algorithm run. {@code graphId} is 0 for a graph that has not been saved. */
    public long addRun(long graphId, @NonNull String algorithmId, int stepCount, @NonNull String summary, long now) {
        ContentValues values = new ContentValues();
        if (graphId == 0 || !graphRowExists(graphId)) {
            values.putNull("graph_id");
        } else {
            values.put("graph_id", graphId);
        }
        values.put("algorithm_id", algorithmId);
        values.put("created_at", now);
        values.put("step_count", stepCount);
        values.put("summary", summary);
        return helper.getWritableDatabase().insertOrThrow("runs", null, values);
    }

    /** Records a finished experiment: a run with its histogram JSON in {@code detail}. {@code trials} goes in step_count. */
    public long addExperimentRun(long graphId, @NonNull String algorithmId, int trials, @NonNull String summary,
                                 @NonNull String detailJson, long now) {
        ContentValues values = new ContentValues();
        // A long experiment can outlive its graph (deleted from the library meanwhile). The result is still worth
        // keeping, so it is saved without a graph instead of failing on the foreign key.
        if (graphId == 0 || !graphRowExists(graphId)) {
            values.putNull("graph_id");
        } else {
            values.put("graph_id", graphId);
        }
        values.put("algorithm_id", algorithmId);
        values.put("created_at", now);
        values.put("step_count", trials);
        values.put("summary", summary);
        values.put("detail", detailJson);
        return helper.getWritableDatabase().insertOrThrow("runs", null, values);
    }

    private boolean graphRowExists(long graphId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT 1 FROM graphs WHERE _id = ?",
                new String[] {Long.toString(graphId)})) {
            return c.moveToFirst();
        }
    }

    /** The newest finished experiment, or null if there is none. */
    @Nullable
    public ExperimentRunRecord latestExperiment() {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT r._id, g.name, r.created_at, r.summary, r.detail FROM runs r "
                        + "LEFT JOIN graphs g ON g._id = r.graph_id WHERE r.detail IS NOT NULL "
                        + "ORDER BY r.created_at DESC, r._id DESC LIMIT 1", null)) {
            return c.moveToFirst() ? new ExperimentRunRecord(c.getLong(0), c.isNull(1) ? "" : c.getString(1),
                    c.getLong(2), c.getString(3), c.getString(4)) : null;
        }
    }

    // ---- Arena attempts (MS6) -----------------------------------------------------------

    /** Records one answered question. */
    public void addAttempt(@NonNull String activity, @NonNull String topic, @NonNull String level, boolean correct,
            long now) {
        ContentValues v = new ContentValues();
        v.put("activity", activity);
        v.put("topic", topic);
        v.put("level", level);
        v.put("correct", correct ? 1 : 0);
        v.put("at", now);
        helper.getWritableDatabase().insertOrThrow("attempts", null, v);
    }

    /**
     * The newest wrong answers (MS17 Ghost round), newest first. Rows whose activity or level this version does not know
     * (saved by another version) are skipped.
     */
    @NonNull
    public java.util.List<com.graphlab.engine.arena.MistakeHeap.Mistake> recentMistakes(int limit) {
        java.util.List<com.graphlab.engine.arena.MistakeHeap.Mistake> out = new java.util.ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT activity, level, at FROM attempts WHERE correct = 0 ORDER BY at DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                com.graphlab.engine.arena.QuestionKind kind = com.graphlab.engine.arena.QuestionKind.findById(c.getString(0));
                com.graphlab.engine.arena.Level level;
                try {
                    level = com.graphlab.engine.arena.Level.valueOf(c.getString(1));
                } catch (IllegalArgumentException unknown) {
                    continue;
                }
                if (kind != null) {
                    out.add(new com.graphlab.engine.arena.MistakeHeap.Mistake(kind, level, c.getLong(2)));
                }
            }
        }
        return out;
    }

    /** Attempts and right answers per activity id, as {attempts, correct}. */
    @NonNull
    public java.util.Map<String, int[]> activityStats() {
        return stats("activity");
    }

    /** Attempts and right answers per topic, as {attempts, correct}. */
    @NonNull
    public java.util.Map<String, int[]> topicStats() {
        return stats("topic");
    }

    private java.util.Map<String, int[]> stats(String column) {
        java.util.Map<String, int[]> out = new java.util.HashMap<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT " + column + ", COUNT(*), SUM(correct) FROM attempts GROUP BY " + column, null)) {
            while (c.moveToNext()) {
                out.put(c.getString(0), new int[] {c.getInt(1), c.getInt(2)});
            }
        }
        return out;
    }

    // ---- Player progress (MS12) ---------------------------------------------------------

    /** The saved value for a progress key ("player" or "mastery"), or null when nothing was saved yet. */
    @androidx.annotation.Nullable
    public String progressValue(@NonNull String key) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT value FROM progress WHERE key = ?", new String[] {key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Saves a progress value, replacing the old one. */
    public void putProgressValue(@NonNull String key, @NonNull String value, long now) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        v.put("updated_at", now);
        helper.getWritableDatabase().insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** The stored matrix JSON for a place-set key, or null. */
    @androidx.annotation.Nullable
    public String routeMatrix(@NonNull String key) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT json FROM route_matrix WHERE key = ?", new String[] {key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public void putRouteMatrix(@NonNull String key, @NonNull String json, long now) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("json", json);
        v.put("updated_at", now);
        helper.getWritableDatabase().insertWithOnConflict("route_matrix", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** The saved events of a graph, in time order (MS15). */
    @NonNull
    public java.util.List<com.graphlab.engine.dynamic.DynamicEvent> events(long graphId) {
        java.util.List<com.graphlab.engine.dynamic.DynamicEvent> out = new java.util.ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT t, type, payload FROM graph_events WHERE graph_id = ? ORDER BY t, _id", new String[] {String.valueOf(graphId)})) {
            while (c.moveToNext()) {
                try {
                    out.add(new com.graphlab.engine.dynamic.DynamicEvent(c.getInt(0),
                            com.graphlab.engine.dynamic.DynamicEvent.Type.valueOf(c.getString(1)), c.getString(2)));
                } catch (IllegalArgumentException damaged) {
                    // a row from a newer version: skip it
                }
            }
        }
        return out;
    }

    public void addEvent(long graphId, @NonNull com.graphlab.engine.dynamic.DynamicEvent event) {
        ContentValues v = new ContentValues();
        v.put("graph_id", graphId);
        v.put("t", event.t());
        v.put("type", event.type().name());
        v.put("payload", event.payload());
        helper.getWritableDatabase().insert("graph_events", null, v);
    }

    public int clearEvents(long graphId) {
        return helper.getWritableDatabase().delete("graph_events", "graph_id = ?", new String[] {String.valueOf(graphId)});
    }

    public int clearAttempts() {
        return helper.getWritableDatabase().delete("attempts", null, null);
    }

    /** The open database for reading, for the ContentProvider's query builder. Not for the main thread. */
    @NonNull
    public SQLiteDatabase readableDatabase() {
        return helper.getReadableDatabase();
    }

    public int runCount() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM runs", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---- lifecycle log -----------------------------------------------------------------

    public void addLog(long at, @NonNull String source, @NonNull String event) {
        ContentValues values = new ContentValues();
        values.put("at", at);
        values.put("source", source);
        values.put("event", event);
        helper.getWritableDatabase().insert("lifecycle_log", null, values);
    }

    /** The newest events first. */
    @NonNull
    public List<LogRecord> recentLogs(int limit) {
        List<LogRecord> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT _id, at, source, event FROM lifecycle_log ORDER BY _id DESC LIMIT ?",
                new String[] {Integer.toString(limit)})) {
            while (c.moveToNext()) {
                out.add(new LogRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)));
            }
        }
        return out;
    }

    /** Keeps only the newest {@code keep} events. */
    public void trimLogs(int keep) {
        helper.getWritableDatabase().execSQL(
                "DELETE FROM lifecycle_log WHERE _id <= (SELECT MAX(_id) FROM lifecycle_log) - ?",
                new Object[] {keep});
    }

    public int clearLogs() {
        return helper.getWritableDatabase().delete("lifecycle_log", null, null);
    }
}

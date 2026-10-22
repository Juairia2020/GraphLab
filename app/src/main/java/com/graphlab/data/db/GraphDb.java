package com.graphlab.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The app's SQLite database (the Android framework's own SQLite, no extra library).
 *
 * <p>Tables:
 * <ul>
 *   <li>{@code graphs}: one row per saved graph. The graph itself is one JSON column (the same text
 *       a .json export contains), plus a few columns the library list needs without parsing it.
 *       {@code deleted_at} is set when a graph is swiped away, so Undo can bring it back; the row
 *       is removed for good when the Undo bar goes away.</li>
 *   <li>{@code runs}: one row per algorithm run, linked to its graph (deleted with it). An experiment (MS8)
 *       is a run whose {@code detail} column holds its histogram as JSON.</li>
 *   <li>{@code lifecycle_log}: the Activity lifecycle events shown in Me.</li>
 *   <li>{@code attempts}: one row per answered Arena question (activity, topic, level, right or wrong).</li>
 * </ul>
 *
 * <p>Always open and used on the disk executor, never on the main thread.
 */
public final class GraphDb extends SQLiteOpenHelper {
    static final String NAME = "graphlab.db";
    static final int VERSION = 6;

    static final String CREATE_GRAPHS = "CREATE TABLE graphs ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "name TEXT NOT NULL, "
            + "json TEXT NOT NULL, "
            + "updated_at INTEGER NOT NULL, "
            + "node_count INTEGER NOT NULL, "
            + "edge_count INTEGER NOT NULL, "
            + "deleted_at INTEGER)";
    static final String CREATE_RUNS = "CREATE TABLE runs ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "graph_id INTEGER REFERENCES graphs(_id) ON DELETE CASCADE, "
            + "algorithm_id TEXT NOT NULL, "
            + "created_at INTEGER NOT NULL, "
            + "step_count INTEGER NOT NULL, "
            + "summary TEXT NOT NULL, "
            + "detail TEXT)";
    static final String CREATE_RUNS_INDEX = "CREATE INDEX runs_by_graph ON runs(graph_id)";
    static final String CREATE_LOG = "CREATE TABLE lifecycle_log ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "at INTEGER NOT NULL, "
            + "source TEXT NOT NULL, "
            + "event TEXT NOT NULL)";

    /** MS6: one row per answered question; the Arena reads it to see which activities and topics need practice. */
    static final String CREATE_ATTEMPTS = "CREATE TABLE attempts ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "activity TEXT NOT NULL, "
            + "topic TEXT NOT NULL, "
            + "level TEXT NOT NULL, "
            + "correct INTEGER NOT NULL, "
            + "at INTEGER NOT NULL)";
    static final String CREATE_ATTEMPTS_INDEX = "CREATE INDEX attempts_by_activity ON attempts(activity)";

    /** MS12: small named values the player owns: "player" (xp, gems, streak, stars as JSON) and "mastery" (text). */
    static final String CREATE_PROGRESS = "CREATE TABLE progress ("
            + "key TEXT PRIMARY KEY, "
            + "value TEXT NOT NULL, "
            + "updated_at INTEGER NOT NULL)";

    /** MS14: one stored road-distance matrix per set of places (key = hash of the sorted place names). */
    static final String CREATE_ROUTE_MATRIX = "CREATE TABLE route_matrix ("
            + "key TEXT PRIMARY KEY, "
            + "json TEXT NOT NULL, "
            + "updated_at INTEGER NOT NULL)";

    /** MS15: the changes of a dynamic graph over time (t, type, payload), one row per event. */
    static final String CREATE_GRAPH_EVENTS = "CREATE TABLE graph_events ("
            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "graph_id INTEGER NOT NULL, "
            + "t INTEGER NOT NULL, "
            + "type TEXT NOT NULL, "
            + "payload TEXT NOT NULL, "
            + "FOREIGN KEY (graph_id) REFERENCES graphs(_id) ON DELETE CASCADE)";

    public GraphDb(Context context) {
        this(context, NAME);
    }

    /** {@code name} null gives a database that lives only in memory, for tests. */
    public GraphDb(Context context, @androidx.annotation.Nullable String name) {
        super(context.getApplicationContext(), name, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);   // so deleting a graph deletes its runs
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_GRAPHS);
        db.execSQL(CREATE_RUNS);
        db.execSQL(CREATE_RUNS_INDEX);
        db.execSQL(CREATE_LOG);
        db.execSQL(CREATE_ATTEMPTS);
        db.execSQL(CREATE_ATTEMPTS_INDEX);
        db.execSQL(CREATE_PROGRESS);
        db.execSQL(CREATE_ROUTE_MATRIX);
        db.execSQL(CREATE_GRAPH_EVENTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // One "if (oldVersion < N)" block per version, so no user ever loses graphs.
        if (oldVersion < 2) {
            // MS8: an experiment run keeps its histogram as JSON here; an ordinary run leaves it empty (NULL).
            db.execSQL("ALTER TABLE runs ADD COLUMN detail TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL(CREATE_ATTEMPTS);
            db.execSQL(CREATE_ATTEMPTS_INDEX);
        }
        if (oldVersion < 4) {
            db.execSQL(CREATE_PROGRESS);
        }
        if (oldVersion < 5) {
            db.execSQL(CREATE_ROUTE_MATRIX);
        }
        if (oldVersion < 6) {
            db.execSQL(CREATE_GRAPH_EVENTS);
        }
    }
}

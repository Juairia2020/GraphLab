package com.graphlab.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The SQLite repository against a database that lives only in memory (checklist item 14). Run on a device or emulator. */
@RunWith(AndroidJUnit4.class)
public class GraphRepositoryTest {
    private GraphDb db;
    private GraphRepository repo;

    @Before
    public void open() {
        Context context = ApplicationProvider.getApplicationContext();
        db = new GraphDb(context, null);
        repo = new GraphRepository(db);
    }

    @After
    public void close() {
        db.close();
    }

    @Test
    public void insertAndReadBack() {
        long id = repo.insertGraph("Roads", "{\"x\":1}", 6, 9, 1000L);
        GraphRecord record = repo.getGraph(id);
        assertNotNull(record);
        assertEquals("Roads", record.name);
        assertEquals(1, repo.graphCount());
        assertEquals(1, repo.listGraphs().size());
        assertEquals(6, repo.listGraphs().get(0).nodeCount);
    }

    @Test
    public void softDeleteHidesAndUndoRestores() {
        long id = repo.insertGraph("A", "{}", 1, 0, 1L);
        assertTrue(repo.setDeleted(id, true, 2L));
        assertEquals(0, repo.listGraphs().size());
        assertTrue(repo.setDeleted(id, false, 3L));
        assertEquals(1, repo.listGraphs().size());
    }

    @Test
    public void purgeRemovesTheGraphAndItsRuns() {
        long id = repo.insertGraph("A", "{}", 1, 0, 1L);
        repo.addRun(id, "dijkstra", 10, "ok", 5L);
        assertEquals(1, repo.runCount());
        repo.setDeleted(id, true, 2L);
        assertEquals(1, repo.purgeAllDeleted());
        assertEquals(0, repo.runCount());
    }

    @Test
    public void listCountsRunsAndNamesTheLatestAlgorithm() {
        long id = repo.insertGraph("A", "{}", 1, 0, 1L);
        repo.addRun(id, "bfs", 3, "s", 10L);
        repo.addRun(id, "dijkstra", 4, "s", 20L);
        GraphSummary summary = repo.listGraphs().get(0);
        assertEquals(2, summary.runCount);
        assertEquals("dijkstra", summary.lastAlgorithm);
    }

    @Test
    public void anExperimentOutlivesItsGraph() {
        // The graph row is gone (never existed here): the result must still be saved, without a graph.
        long runId = repo.addExperimentRun(9999L, "karger", 10, "10 trials", "{\"detail\":true}", 7L);
        assertTrue(runId > 0);
        ExperimentRunRecord latest = repo.latestExperiment();
        assertNotNull(latest);
        assertEquals(runId, latest.id);
        assertEquals("", latest.graphName);
    }

    @Test
    public void ordinaryRunsAreNotExperiments() {
        long id = repo.insertGraph("A", "{}", 1, 0, 1L);
        repo.addRun(id, "bfs", 3, "s", 10L);
        assertNull(repo.latestExperiment());
        repo.addExperimentRun(id, "karger", 5, "5 trials", "{}", 11L);
        assertEquals("A", repo.latestExperiment().graphName);
    }

    @Test
    public void runsTableHasTheDetailColumn() {
        try (Cursor c = db.getReadableDatabase().rawQuery("PRAGMA table_info(runs)", null)) {
            boolean found = false;
            while (c.moveToNext()) {
                found |= "detail".equals(c.getString(c.getColumnIndexOrThrow("name")));
            }
            assertTrue(found);
        }
        assertFalse(db.getReadableDatabase().isReadOnly());
    }
}

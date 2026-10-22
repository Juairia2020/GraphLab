package com.graphlab.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.graphlab.GraphLabApp;
import com.graphlab.data.db.GraphRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Queries the app's real ContentProvider through a ContentResolver, like any client (checklist item 10). It uses the
 * app's own database, so it adds rows with a recognisable name and removes them afterwards. Run on a device or emulator.
 */
@RunWith(AndroidJUnit4.class)
public class GraphProviderTest {
    private static final String NAME = "ProviderTest-graph";
    private ContentResolver resolver;
    private GraphRepository repo;
    private long graphId;

    @Before
    public void addData() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        resolver = context.getContentResolver();
        repo = GraphLabApp.container(context).repository();
        graphId = repo.insertGraph(NAME, "{\"nodes\":[]}", 4, 5, System.currentTimeMillis());
        repo.addExperimentRun(graphId, "karger", 20, "20 trials", "{\"format\":\"graphlab-experiment\"}", System.currentTimeMillis());
    }

    @After
    public void clean() {
        repo.setDeleted(graphId, true, System.currentTimeMillis());
        repo.purge(graphId);
    }

    @Test
    public void listsGraphsWithoutTheirJson() {
        try (Cursor c = resolver.query(GraphContract.GRAPHS_URI, null, null, null, null)) {
            assertNotNull(c);
            assertEquals(-1, c.getColumnIndex(GraphContract.Graphs.JSON));
            boolean found = false;
            while (c.moveToNext()) {
                found |= NAME.equals(c.getString(c.getColumnIndexOrThrow(GraphContract.Graphs.NAME)));
            }
            assertTrue(found);
        }
    }

    @Test
    public void oneGraphComesWithItsJson() {
        try (Cursor c = resolver.query(GraphContract.graphUri(graphId), null, null, null, null)) {
            assertNotNull(c);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals("{\"nodes\":[]}", c.getString(c.getColumnIndexOrThrow(GraphContract.Graphs.JSON)));
        }
    }

    @Test
    public void runsCarryTheGraphNameAndOnlyExperimentsCanBeFiltered() {
        try (Cursor c = resolver.query(GraphContract.RUNS_URI, null, GraphContract.Runs.ONLY_EXPERIMENTS, null, null)) {
            assertNotNull(c);
            boolean found = false;
            while (c.moveToNext()) {
                if (NAME.equals(c.getString(c.getColumnIndexOrThrow(GraphContract.Runs.GRAPH_NAME)))) {
                    found = true;
                    assertEquals("20 trials", c.getString(c.getColumnIndexOrThrow(GraphContract.Runs.SUMMARY)));
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void aGraphWaitingForUndoIsHidden() {
        repo.setDeleted(graphId, true, System.currentTimeMillis());
        try (Cursor c = resolver.query(GraphContract.graphUri(graphId), null, null, null, null)) {
            assertNotNull(c);
            assertEquals(0, c.getCount());
        }
        repo.setDeleted(graphId, false, System.currentTimeMillis());
    }

    @Test
    public void typesAreReported() {
        assertEquals(GraphContract.TYPE_GRAPH_DIR, resolver.getType(GraphContract.GRAPHS_URI));
        assertEquals(GraphContract.TYPE_GRAPH_ITEM, resolver.getType(GraphContract.graphUri(graphId)));
        assertEquals(GraphContract.TYPE_RUN_DIR, resolver.getType(GraphContract.RUNS_URI));
    }

    @Test
    public void itIsReadOnly() {
        assertThrows(UnsupportedOperationException.class, () -> resolver.insert(GraphContract.GRAPHS_URI, new ContentValues()));
        assertThrows(UnsupportedOperationException.class, () -> resolver.delete(GraphContract.GRAPHS_URI, null, null));
        assertThrows(UnsupportedOperationException.class, () -> resolver.update(GraphContract.GRAPHS_URI, new ContentValues(), null, null));
    }

    @Test
    public void unsafeInputIsRefused() {
        // An unknown column in the projection, a sort order that is not "column [ASC|DESC]", and a selection that
        // tries to close the clause and add SQL must all fail instead of reaching the database.
        assertThrows(Exception.class, () -> resolver.query(GraphContract.GRAPHS_URI, new String[] {"json"}, null, null, null));
        assertThrows(Exception.class, () -> resolver.query(GraphContract.GRAPHS_URI, null, null, null, "name; DROP TABLE graphs"));
        assertThrows(Exception.class, () -> resolver.query(GraphContract.GRAPHS_URI, null, "1) OR (1=1", null, null));
        Uri unknown = Uri.withAppendedPath(GraphContract.BASE_URI, "nothing");
        assertThrows(Exception.class, () -> resolver.query(unknown, null, null, null, null));
        assertFalse(repo.graphNames().isEmpty());   // the table is still there
    }
}

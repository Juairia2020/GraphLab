package com.graphlab.engine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class GalleryPostTest {
    private static GraphDocument sample() {
        return new GraphDocument("Road network", true, SampleGraph.sixNodeUndirected());
    }

    @Test
    public void aPostWrittenAndReadBackIsTheSameGraph() throws Exception {
        Map<String, Object> fields = GalleryPost.toFields("Road network", "Ada", "uid-1", sample(), 1_700_000_000_000L);
        assertEquals(7, fields.size());
        assertEquals(6L, fields.get(GalleryPost.NODES));
        assertEquals(9L, fields.get(GalleryPost.EDGES));
        GalleryPost.Entry entry = GalleryPost.fromFields("doc1", fields);
        assertEquals("doc1", entry.id());
        assertEquals("Road network", entry.title());
        assertEquals("Ada", entry.author());
        assertEquals("uid-1", entry.ownerUid());
        assertEquals(1_700_000_000_000L, entry.createdAt());
        assertEquals(6, entry.nodeCount());
        assertEquals(9, entry.edgeCount());
        assertEquals(sample().graph(), entry.document().graph());
        assertTrue(entry.isOwnedBy("uid-1"));
        assertFalse(entry.isOwnedBy("uid-2"));
        assertFalse(entry.isOwnedBy(null));
        assertFalse(entry.isOwnedBy(""));
    }

    @Test
    public void numbersMayComeBackAsAnyNumberType() throws Exception {
        Map<String, Object> fields = new HashMap<>(GalleryPost.toFields("T", "A", "u", sample(), 5L));
        fields.put(GalleryPost.CREATED, 7.0);
        fields.put(GalleryPost.NODES, 99);          // wrong on purpose: the counts are recomputed
        assertEquals(7L, GalleryPost.fromFields("d", fields).createdAt());
        assertEquals(6, GalleryPost.fromFields("d", fields).nodeCount());
        fields.remove(GalleryPost.CREATED);
        assertEquals(0L, GalleryPost.fromFields("d", fields).createdAt());
    }

    @Test
    public void theTitleIsCleanedAndLimited() {
        Map<String, Object> fields = GalleryPost.toFields("   " + "x".repeat(80) + "   ", "A", "u", sample(), 1L);
        assertTrue(((String) fields.get(GalleryPost.TITLE)).length() <= 40);
        assertEquals(GalleryPost.DEFAULT_TITLE, GalleryPost.toFields("   ", "A", "u", sample(), 1L).get(GalleryPost.TITLE));
    }

    @Test
    public void cleansTheAuthorName() {
        assertEquals("Student", GalleryPost.cleanAuthor(null));
        assertEquals("Student", GalleryPost.cleanAuthor("   \n\t "));
        assertEquals("Ada Lovelace", GalleryPost.cleanAuthor("  Ada \t  Lovelace\u0007 "));
        assertEquals(GalleryPost.MAX_AUTHOR, GalleryPost.cleanAuthor("n".repeat(100)).length());
    }

    @Test
    public void refusesToPublishWithoutAnOwnerOrAnEmptyGraph() {
        assertThrows(IllegalArgumentException.class, () -> GalleryPost.toFields("T", "A", null, sample(), 1L));
        assertThrows(IllegalArgumentException.class, () -> GalleryPost.toFields("T", "A", " ", sample(), 1L));
        GraphDocument empty = new GraphDocument("E", true, Graph.builder(false).build());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GalleryPost.toFields("T", "A", "u", empty, 1L))
                .getMessage().contains("empty"));
    }

    @Test
    public void refusesAGraphThatIsTooLargeToPublish() {
        Graph.Builder b = Graph.builder(false).nodes(60);
        for (int i = 0; i < 60; i++) {
            for (int j = i + 1; j < 60; j++) {
                b.edge(i, j, 1234567.891);
            }
        }
        GraphDocument big = new GraphDocument("Big", true, b.build());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GalleryPost.toFields("T", "A", "u", big, 1L))
                .getMessage().contains("too large"));
    }

    @Test
    public void badStoredPostsAreRejectedWithAReason() throws Exception {
        Map<String, Object> good = GalleryPost.toFields("T", "A", "u", sample(), 1L);
        Map<String, Object> noTitle = new HashMap<>(good);
        noTitle.remove(GalleryPost.TITLE);
        assertTrue(assertThrows(GraphFormatException.class, () -> GalleryPost.fromFields("d", noTitle)).getMessage().contains("title"));
        Map<String, Object> noJson = new HashMap<>(good);
        noJson.put(GalleryPost.JSON, 42);
        assertTrue(assertThrows(GraphFormatException.class, () -> GalleryPost.fromFields("d", noJson)).getMessage().contains("graph"));
        Map<String, Object> junk = new HashMap<>(good);
        junk.put(GalleryPost.JSON, "{\"hello\": 1}");
        assertTrue(assertThrows(GraphFormatException.class, () -> GalleryPost.fromFields("d", junk)).getMessage().contains("Post d"));
        Map<String, Object> huge = new HashMap<>(good);
        huge.put(GalleryPost.JSON, "x".repeat(GalleryPost.MAX_JSON_CHARS + 1));
        assertThrows(GraphFormatException.class, () -> GalleryPost.fromFields("d", huge));
        assertThrows(GraphFormatException.class, () -> GalleryPost.fromFields("", good));
    }

    @Test
    public void missingOptionalFieldsGetDefaults() throws Exception {
        Map<String, Object> fields = new HashMap<>(GalleryPost.toFields("T", "A", "u", sample(), 1L));
        fields.remove(GalleryPost.AUTHOR);
        fields.remove(GalleryPost.OWNER);
        GalleryPost.Entry entry = GalleryPost.fromFields("d", fields);
        assertEquals("Student", entry.author());
        assertEquals("", entry.ownerUid());
        assertFalse(entry.isOwnedBy("u"));
    }
}

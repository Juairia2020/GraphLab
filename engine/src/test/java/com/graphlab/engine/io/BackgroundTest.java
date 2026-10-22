package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.SampleGraph;
import org.junit.Test;

public class BackgroundTest {
    private static final Graph G = SampleGraph.sixNodeUndirected();

    @Test
    public void plainNamesAreSafeAndPathsAreNot() {
        assertTrue(Background.isSafeName("bg_1700000000000.jpg"));
        assertTrue(Background.isSafeName("photo-1.png"));
        assertFalse(Background.isSafeName(""));
        assertFalse(Background.isSafeName("../secret.jpg"));
        assertFalse(Background.isSafeName("a/b.jpg"));
        assertFalse(Background.isSafeName("a\\b.jpg"));
        assertFalse(Background.isSafeName(".hidden"));
        assertFalse(Background.isSafeName("a..b"));
        assertFalse(Background.isSafeName("sp ace.jpg"));
        assertFalse(Background.isSafeName("x".repeat(Background.MAX_NAME + 1)));
        assertFalse(Background.isSafeName(null));
    }

    @Test
    public void constructorRefusesBadValues() {
        assertThrows(IllegalArgumentException.class, () -> new Background("../x.jpg", 0.5, false));
        assertThrows(IllegalArgumentException.class, () -> new Background("x.jpg", 1.5, false));
        assertThrows(IllegalArgumentException.class, () -> new Background("x.jpg", -0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Background("x.jpg", Double.NaN, false));
    }

    @Test
    public void withersChangeOneThing() {
        Background b = Background.of("x.jpg");
        assertEquals(0.5, b.opacity(), 0);
        assertFalse(b.locked());
        assertEquals(0.2, b.withOpacity(0.2).opacity(), 0);
        assertTrue(b.withLocked(true).locked());
        assertEquals("x.jpg", b.withOpacity(0.9).file());
    }

    @Test
    public void jsonRoundTripKeepsThePhoto() throws Exception {
        GraphDocument doc = new GraphDocument("Trace", true, G, new Background("bg_1.jpg", 0.35, true));
        GraphDocument back = GraphJson.read(GraphJson.write(doc));
        assertEquals(doc, back);
        assertEquals(0.35, back.background().opacity(), 1e-12);
        assertTrue(back.background().locked());
    }

    @Test
    public void aGraphWithoutAPhotoWritesNoBackgroundKey() throws Exception {
        String text = GraphJson.write(new GraphDocument("Plain", true, G));
        assertFalse(text.contains("background"));
        assertNull(GraphJson.read(text).background());
    }

    @Test
    public void oldFilesStillRead() throws Exception {
        String old = "{\"format\":\"graphlab-graph\",\"version\":1,\"nodes\":[{\"id\":0},{\"id\":1}],\"edges\":[{\"from\":0,\"to\":1}]}";
        assertNull(GraphJson.read(old).background());
    }

    @Test
    public void aFilePointingOutsideTheFolderIsRefused() {
        String bad = "{\"format\":\"graphlab-graph\",\"version\":1,\"background\":{\"file\":\"../../etc/passwd\"},"
                + "\"nodes\":[{\"id\":0}],\"edges\":[]}";
        GraphFormatException e = assertThrows(GraphFormatException.class, () -> GraphJson.read(bad));
        assertTrue(e.getMessage().contains("plain file name"));
    }

    @Test
    public void opacityOutOfRangeAndWrongTypesAreRefused() {
        String high = "{\"format\":\"graphlab-graph\",\"version\":1,\"background\":{\"file\":\"a.jpg\",\"opacity\":2},"
                + "\"nodes\":[{\"id\":0}],\"edges\":[]}";
        assertThrows(GraphFormatException.class, () -> GraphJson.read(high));
        String notObject = "{\"format\":\"graphlab-graph\",\"version\":1,\"background\":\"a.jpg\","
                + "\"nodes\":[{\"id\":0}],\"edges\":[]}";
        assertThrows(GraphFormatException.class, () -> GraphJson.read(notObject));
    }

    @Test
    public void missingOpacityAndLockUseDefaults() throws Exception {
        String text = "{\"format\":\"graphlab-graph\",\"version\":1,\"background\":{\"file\":\"a.jpg\"},"
                + "\"nodes\":[{\"id\":0}],\"edges\":[]}";
        Background b = GraphJson.read(text).background();
        assertEquals(Background.DEFAULT_OPACITY, b.opacity(), 0);
        assertFalse(b.locked());
    }

    @Test
    public void withNameKeepsThePhoto() {
        GraphDocument doc = new GraphDocument("A", true, G, Background.of("x.jpg"));
        assertEquals(Background.of("x.jpg"), doc.withName("B").background());
        assertNull(doc.withBackground(null).background());
    }

    @Test
    public void editorCountsAPhotoChangeAsAChangeButNotAStructureChange() {
        GraphEditor editor = new GraphEditor(false, true);
        int version = editor.version();
        int structure = editor.structureVersion();
        editor.setBackground(Background.of("x.jpg"));
        assertEquals(version + 1, editor.version());
        assertEquals(structure, editor.structureVersion());
        editor.setBackground(Background.of("x.jpg"));       // the same photo again changes nothing
        assertEquals(version + 1, editor.version());
        editor.setBackground(null);
        assertEquals(version + 2, editor.version());
        assertNull(editor.background());
    }

    @Test
    public void bigPicturesAreShrunkByPowersOfTwo() {
        assertEquals(1, Background.sampleSize(1600, 900, 1600));
        assertEquals(2, Background.sampleSize(3000, 2000, 1600));
        assertEquals(4, Background.sampleSize(4000, 3000, 1600));
        assertEquals(8, Background.sampleSize(12000, 9000, 1600));
        assertEquals(1, Background.sampleSize(100, 100, 1600));
        assertThrows(IllegalArgumentException.class, () -> Background.sampleSize(0, 10, 1600));
    }

    @Test
    public void worldRectKeepsTheShapeOfThePicture() {
        double[] r = Background.worldRect(2000, 1000);
        assertEquals(-500, r[0], 1e-9);
        assertEquals(500, r[2], 1e-9);
        assertEquals(-250, r[1], 1e-9);
        assertEquals(250, r[3], 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Background.worldRect(0, 10));
    }
}

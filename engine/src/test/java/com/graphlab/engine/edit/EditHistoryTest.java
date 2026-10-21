package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Node;
import org.junit.Before;
import org.junit.Test;

public class EditHistoryTest {
    private GraphEditor editor;
    private EditHistory history;

    @Before
    public void setUp() {
        editor = new GraphEditor(false, true);
        history = new EditHistory(editor);
    }

    private int addNode(double x, double y) {
        int id = editor.addNode(x, y);
        history.checkpoint(editor);
        return id;
    }

    @Test
    public void startsWithNothingToUndoOrRedo() {
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertFalse(history.undo(editor));
        assertFalse(history.redo(editor));
    }

    @Test
    public void undoRemovesTheLastNodeAndRedoBringsItBack() {
        addNode(0, 0);
        addNode(100, 0);
        assertEquals(2, editor.nodeCount());
        assertTrue(history.undo(editor));
        assertEquals(1, editor.nodeCount());
        assertTrue(history.canRedo());
        assertTrue(history.redo(editor));
        assertEquals(2, editor.nodeCount());
        assertFalse(history.canRedo());
    }

    @Test
    public void undoAllTheWayReturnsTheStartingGraph() {
        int a = addNode(0, 0);
        int b = addNode(100, 0);
        editor.addEdge(a, b, 5);
        history.checkpoint(editor);
        editor.setEdgeWeight(0, 9);
        history.checkpoint(editor);
        assertEquals(4, history.undoCount());
        while (history.undo(editor)) {
            // keep going
        }
        assertTrue(editor.isEmpty());
        assertFalse(history.canUndo());
        assertEquals(4, history.redoCount());
    }

    @Test
    public void undoRestoresAWeightAndAPosition() {
        int a = addNode(0, 0);
        int b = addNode(100, 0);
        editor.addEdge(a, b, 5);
        history.checkpoint(editor);
        editor.setEdgeWeight(0, 9);
        history.checkpoint(editor);
        editor.moveNode(a, 40, 40);
        history.checkpoint(editor);
        history.undo(editor);
        Node node = editor.findNode(a);
        assertEquals(0.0, node.x(), 1e-9);
        assertEquals(9.0, editor.findEdge(0).weight(), 1e-9);
        history.undo(editor);
        assertEquals(5.0, editor.findEdge(0).weight(), 1e-9);
    }

    @Test
    public void aDragIsOneStepEvenThoughThePositionChangesManyTimes() {
        int a = addNode(0, 0);
        int before = history.undoCount();
        for (int i = 1; i <= 30; i++) {
            editor.moveNode(a, i, i);
        }
        assertTrue(history.checkpoint(editor));
        assertEquals(before + 1, history.undoCount());
        assertFalse("nothing changed, so no second step", history.checkpoint(editor));
    }

    @Test
    public void aNewEditAfterUndoClearsRedo() {
        addNode(0, 0);
        addNode(100, 0);
        history.undo(editor);
        assertTrue(history.canRedo());
        addNode(0, 100);
        assertFalse(history.canRedo());
        assertEquals(2, editor.nodeCount());
    }

    @Test
    public void undoRecordsAnEditThatWasNotCheckpointedYet() {
        addNode(0, 0);
        editor.addNode(100, 0);          // no checkpoint
        assertTrue(history.undo(editor));
        assertEquals(1, editor.nodeCount());
        assertTrue(history.redo(editor));
        assertEquals(2, editor.nodeCount());
    }

    @Test
    public void switchesAreUndoable() {
        int a = addNode(0, 0);
        int b = addNode(100, 0);
        editor.addEdge(a, b, 3);
        history.checkpoint(editor);
        editor.setDirected(true);
        history.checkpoint(editor);
        editor.setWeighted(false);
        history.checkpoint(editor);
        history.undo(editor);
        assertFalse(editor.isWeighted() == false);
        assertTrue(editor.isDirected());
        history.undo(editor);
        assertFalse(editor.isDirected());
        assertEquals(3.0, editor.findEdge(0).weight(), 1e-9);
    }

    @Test
    public void removingANodeAndItsEdgesIsOneUndoStep() {
        int a = addNode(0, 0);
        int b = addNode(100, 0);
        int c = addNode(0, 100);
        editor.addEdge(a, b, 1);
        editor.addEdge(b, c, 2);
        history.checkpoint(editor);
        editor.removeNode(b);
        history.checkpoint(editor);
        assertEquals(0, editor.edgeCount());
        history.undo(editor);
        assertEquals(3, editor.nodeCount());
        assertEquals(2, editor.edgeCount());
    }

    @Test
    public void newNodesAfterUndoGetFreshIdsAndLabels() {
        addNode(0, 0);
        addNode(100, 0);
        history.undo(editor);
        int id = addNode(100, 0);
        assertEquals(1, id);
        assertEquals("B", editor.findNode(id).label());
    }

    @Test
    public void historyIsLimited() {
        for (int i = 0; i < EditHistory.LIMIT + 20; i++) {
            if (editor.canAddNode()) {
                addNode(i * 70.0, 0);
            } else {
                editor.moveNode(0, i, i);
                history.checkpoint(editor);
            }
        }
        assertEquals(EditHistory.LIMIT, history.undoCount());
    }

    @Test
    public void resetStartsAgainFromTheCurrentGraph() {
        addNode(0, 0);
        history.undo(editor);
        history.reset(editor);
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    public void clearIsUndoable() {
        addNode(0, 0);
        addNode(100, 0);
        editor.clear();
        history.checkpoint(editor);
        assertTrue(editor.isEmpty());
        history.undo(editor);
        assertEquals(2, editor.nodeCount());
    }

    @Test
    public void undoAndRedoBumpTheStructureVersionSoARunIsDropped() {
        addNode(0, 0);
        int before = editor.structureVersion();
        history.undo(editor);
        assertTrue(editor.structureVersion() > before);
    }
}

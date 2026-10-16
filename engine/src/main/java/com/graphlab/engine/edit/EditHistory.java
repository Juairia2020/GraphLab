package com.graphlab.engine.edit;

import com.graphlab.engine.model.Graph;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo for the graph editor. It keeps whole snapshots of the graph (nodes with positions, edges with weights,
 * and the directed and weighted settings), at most {@link #LIMIT} steps back. A graph holds at most 60 nodes, so a
 * snapshot is small and the code stays simple and easy to test.
 *
 * <p>How it works: the screen calls {@link #checkpoint} after each finished edit (a node added, a drag ended, a weight
 * changed, a switch flipped). If the editor's {@link GraphEditor#version()} moved since the last call, the state before
 * the edit goes on the undo stack. {@link #undo} and {@link #redo} load a snapshot into the editor and remember its new
 * version, so loading is never mistaken for a new edit. A new edit clears the redo stack, as in every editor.
 *
 * <p>Plain Java, no Android.
 */
public final class EditHistory {
    public static final int LIMIT = 100;

    private record Snapshot(Graph graph, boolean weighted) {
        static Snapshot of(GraphEditor editor) {
            return new Snapshot(editor.toStoredGraph(), editor.isWeighted());
        }

        void applyTo(GraphEditor editor) {
            editor.load(graph, weighted);
        }
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private Snapshot current;
    private int seenVersion = -1;

    /** A history whose starting point is the editor as it is now. */
    public EditHistory(GraphEditor editor) {
        reset(editor);
    }

    /** Forgets everything and takes the editor as the new starting point (after opening or clearing a graph). */
    public void reset(GraphEditor editor) {
        undo.clear();
        redo.clear();
        current = Snapshot.of(editor);
        seenVersion = editor.version();
    }

    /**
     * Records the editor's state if it changed since the last call.
     *
     * @return true if a new undo step was added
     */
    public boolean checkpoint(GraphEditor editor) {
        if (editor.version() == seenVersion) {
            return false;
        }
        undo.push(current);
        while (undo.size() > LIMIT) {
            undo.removeLast();
        }
        redo.clear();
        current = Snapshot.of(editor);
        seenVersion = editor.version();
        return true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoCount() {
        return undo.size();
    }

    public int redoCount() {
        return redo.size();
    }

    /**
     * Goes one step back. Any edit not yet checkpointed is recorded first, so it is not lost.
     *
     * @return false if there is nothing to undo
     */
    public boolean undo(GraphEditor editor) {
        checkpoint(editor);
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(current);
        current = undo.pop();
        current.applyTo(editor);
        seenVersion = editor.version();
        return true;
    }

    /** @return false if there is nothing to redo */
    public boolean redo(GraphEditor editor) {
        checkpoint(editor);
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(current);
        current = redo.pop();
        current.applyTo(editor);
        seenVersion = editor.version();
        return true;
    }
}

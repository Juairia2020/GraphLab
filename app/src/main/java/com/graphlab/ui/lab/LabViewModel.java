package com.graphlab.ui.lab;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.edit.Viewport;
import com.graphlab.ui.common.EditorTool;

/**
 * Everything the Lab editor must not lose when the phone rotates: the graph being edited, the
 * camera (zoom and position) and the active tool. The Activity and the GraphView are rebuilt on
 * rotation; this object is not.
 *
 * <p>MS3 shares this ViewModel between the Lab modes (Build, Run, ...). MS4 saves the graph to
 * the database so it also survives the process being killed.
 */
public class LabViewModel extends ViewModel {
    private final GraphEditor editor = new GraphEditor(false, true);
    private final Viewport viewport = new Viewport();
    private EditorTool tool = EditorTool.NODE;

    @NonNull
    public GraphEditor editor() {
        return editor;
    }

    @NonNull
    public Viewport viewport() {
        return viewport;
    }

    @NonNull
    public EditorTool tool() {
        return tool;
    }

    public void setTool(@NonNull EditorTool tool) {
        this.tool = tool;
    }
}

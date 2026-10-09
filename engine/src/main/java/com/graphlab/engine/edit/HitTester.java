package com.graphlab.engine.edit;

import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Node;

/** Works out what is under a finger. All distances are in screen pixels. */
public final class HitTester {
    private HitTester() {
    }

    /**
     * The nearest node under the point, or -1. A node counts as hit within its drawn radius
     * or within {@code slopPx}, whichever is larger, so tiny zoomed-out nodes stay tappable.
     *
     * @param excludeId a node to ignore (for example the start of an edge being drawn), or -1
     */
    public static int nodeAt(GraphEditor editor, Viewport viewport, double sx, double sy,
                             double slopPx, int excludeId) {
        double radius = Math.max(EditorMetrics.NODE_RADIUS * viewport.scale(), slopPx);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < editor.nodeCount(); i++) {
            Node node = editor.nodeAt(i);
            if (node.id() == excludeId) {
                continue;
            }
            double distance = Math.hypot(viewport.worldToScreenX(node.x()) - sx,
                    viewport.worldToScreenY(node.y()) - sy);
            if (distance <= radius && distance < bestDistance) {
                best = node.id();
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The nearest edge within {@code slopPx} of the point (following curves), or -1. */
    public static int edgeAt(GraphEditor editor, Viewport viewport, double sx, double sy, double slopPx) {
        float radius = (float) (EditorMetrics.NODE_RADIUS * viewport.scale());
        float bend = (float) (EditorMetrics.RECIPROCAL_BEND * viewport.scale());
        EdgeShape shape = new EdgeShape();
        int best = -1;
        double bestDistance = slopPx;
        for (int i = 0; i < editor.edgeCount(); i++) {
            Node from = editor.nodeAt(editor.edgeFromIndex(i));
            Node to = editor.nodeAt(editor.edgeToIndex(i));
            shape.set((float) viewport.worldToScreenX(from.x()), (float) viewport.worldToScreenY(from.y()),
                    (float) viewport.worldToScreenX(to.x()), (float) viewport.worldToScreenY(to.y()),
                    editor.edgeIsReciprocal(i) ? bend : 0f, radius);
            double distance = shape.distanceTo((float) sx, (float) sy);
            if (distance <= bestDistance) {
                Edge edge = editor.edgeAt(i);
                best = edge.id();
                bestDistance = distance;
            }
        }
        return best;
    }
}

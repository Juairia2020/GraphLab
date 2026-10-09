package com.graphlab.engine.edit;

/**
 * Sizes shared by the editor model and the view. World units equal dp at zoom 1, so a node
 * of radius 22 is a 44 dp circle, close to the 48 dp touch target.
 */
public final class EditorMetrics {
    /** Node radius in world units. */
    public static final double NODE_RADIUS = 22.0;
    /** How far (world units) the middle of a curved edge bows away from the straight line. */
    public static final double RECIPROCAL_BEND = 18.0;
    /** New nodes must be at least this far (world units) from every existing node. */
    public static final double MIN_NODE_GAP = 2 * NODE_RADIUS + 6;
    /** A finger within this many dp of a node centre counts as touching it (a 48 dp target). */
    public static final double NODE_SLOP_DP = 24.0;
    /** A finger within this many dp of an edge counts as touching it. */
    public static final double EDGE_SLOP_DP = 20.0;
    /** Free space kept around the graph when it is fitted to the screen. */
    public static final double FIT_MARGIN_DP = 40.0;

    private EditorMetrics() {
    }
}

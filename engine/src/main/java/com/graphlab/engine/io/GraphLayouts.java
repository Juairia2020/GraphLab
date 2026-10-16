package com.graphlab.engine.io;

import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.List;

/** Places nodes when a file has no usable positions. Pure maths, no Android. */
public final class GraphLayouts {
    /** Nodes closer than this (world units) overlap badly: the editor draws a node 44 wide. */
    public static final double MIN_SPACING = 60.0;
    /** Positions closer than this are treated as unusable foreign units (for example 0 to 1). */
    private static final double TOO_CLOSE = 10.0;
    /** A layout wider than this cannot be shown at the editor's zoom limits. */
    private static final double TOO_WIDE = 5000.0;

    private GraphLayouts() {
    }

    /** The same nodes (ids and labels kept) on a circle around (0, 0), starting at the top. */
    public static List<Node> circle(List<Node> nodes) {
        int n = nodes.size();
        double radius = Math.max(100.0, n * MIN_SPACING / (2 * Math.PI));
        List<Node> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(n, 1);
            Node node = nodes.get(i);
            out.add(new Node(node.id(), round(radius * Math.cos(angle)), round(radius * Math.sin(angle)), node.label()));
        }
        return out;
    }

    /**
     * Makes positions from another program usable. Positions that are already roomy are kept
     * exactly (so a GraphLab file reads back unchanged). Positions that are far too close, for
     * example 0 to 1 coordinates, are scaled up so the closest pair is {@link #MIN_SPACING} apart.
     * If that makes the layout absurdly wide, or two nodes share a position, a circle is used.
     */
    public static List<Node> fitForeign(List<Node> nodes) {
        if (nodes.size() < 2) {
            return nodes;
        }
        double closest = Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            Node a = nodes.get(i);
            minX = Math.min(minX, a.x());
            maxX = Math.max(maxX, a.x());
            minY = Math.min(minY, a.y());
            maxY = Math.max(maxY, a.y());
            for (int j = i + 1; j < nodes.size(); j++) {
                Node b = nodes.get(j);
                closest = Math.min(closest, Math.hypot(a.x() - b.x(), a.y() - b.y()));
            }
        }
        double extent = Math.max(maxX - minX, maxY - minY);
        if (closest == 0.0) {
            return circle(nodes);
        }
        double scale = closest < TOO_CLOSE ? MIN_SPACING / closest : 1.0;
        if (extent * scale > TOO_WIDE) {
            return circle(nodes);
        }
        if (scale == 1.0) {
            return nodes;
        }
        List<Node> out = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            out.add(new Node(node.id(), node.x() * scale, node.y() * scale, node.label()));
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

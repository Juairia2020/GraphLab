package com.graphlab.engine.edit;

/**
 * The drawn shape of one edge: a straight line, or a quadratic curve for the two edges of a
 * reciprocal pair (A to B and B to A) so they do not overlap. Both ends are pulled back to
 * the rim of their node, so arrowheads touch the circle instead of its centre.
 *
 * <p>One instance is reused for every edge on every frame (call {@link #set} again), which is
 * how the view avoids allocating inside onDraw. Coordinates may be in any one unit (pixels
 * for drawing); {@code bend} and {@code radius} must use the same unit.
 */
public final class EdgeShape {
    private static final int CURVE_SEGMENTS = 12;

    public float startX;
    public float startY;
    public float endX;
    public float endY;
    /** The curve's control point. Equals the middle of the line when the edge is straight. */
    public float ctrlX;
    public float ctrlY;
    /** The middle of the drawn edge: where the weight pill goes. */
    public float midX;
    public float midY;
    /** Unit direction the edge travels as it arrives at the end node (for the arrowhead). */
    public float dirX = 1f;
    public float dirY;
    public boolean curved;
    /** False when the two nodes touch or overlap, so there is no edge to draw. */
    public boolean visible;

    /**
     * @param bend   how far the middle bows away from the straight line; 0 for a straight edge.
     *               The bow is to the left of the direction of travel, so the two edges of a
     *               reciprocal pair bow to opposite sides.
     * @param radius node radius, used to pull both ends back to the rim
     */
    public void set(float x0, float y0, float x1, float y1, float bend, float radius) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        curved = bend != 0f;
        if (len <= 2f * radius + 1f) {
            visible = false;
            startX = endX = ctrlX = midX = (x0 + x1) / 2f;
            startY = endY = ctrlY = midY = (y0 + y1) / 2f;
            dirX = 1f;
            dirY = 0f;
            return;
        }
        visible = true;
        float ux = dx / len;
        float uy = dy / len;
        if (!curved) {
            startX = x0 + ux * radius;
            startY = y0 + uy * radius;
            endX = x1 - ux * radius;
            endY = y1 - uy * radius;
            ctrlX = midX = (startX + endX) / 2f;
            ctrlY = midY = (startY + endY) / 2f;
            dirX = ux;
            dirY = uy;
            return;
        }
        float nx = -uy;
        float ny = ux;
        ctrlX = (x0 + x1) / 2f + nx * 2f * bend;
        ctrlY = (y0 + y1) / 2f + ny * 2f * bend;
        float d0x = ctrlX - x0;
        float d0y = ctrlY - y0;
        float l0 = (float) Math.hypot(d0x, d0y);
        d0x /= l0;
        d0y /= l0;
        float d1x = x1 - ctrlX;
        float d1y = y1 - ctrlY;
        float l1 = (float) Math.hypot(d1x, d1y);
        d1x /= l1;
        d1y /= l1;
        startX = x0 + d0x * radius;
        startY = y0 + d0y * radius;
        endX = x1 - d1x * radius;
        endY = y1 - d1y * radius;
        dirX = d1x;
        dirY = d1y;
        midX = 0.25f * startX + 0.5f * ctrlX + 0.25f * endX;
        midY = 0.25f * startY + 0.5f * ctrlY + 0.25f * endY;
    }

    /** Distance from a point to the drawn edge, or {@code Float.MAX_VALUE} when nothing is drawn. */
    public float distanceTo(float px, float py) {
        if (!visible) {
            return Float.MAX_VALUE;
        }
        if (!curved) {
            return segmentDistance(px, py, startX, startY, endX, endY);
        }
        float best = Float.MAX_VALUE;
        float prevX = startX;
        float prevY = startY;
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            float t = (float) i / CURVE_SEGMENTS;
            float u = 1f - t;
            float x = u * u * startX + 2f * u * t * ctrlX + t * t * endX;
            float y = u * u * startY + 2f * u * t * ctrlY + t * t * endY;
            best = Math.min(best, segmentDistance(px, py, prevX, prevY, x, y));
            prevX = x;
            prevY = y;
        }
        return best;
    }

    /** Shortest distance from a point to the line segment (x0, y0)-(x1, y1). */
    public static float segmentDistance(float px, float py, float x0, float y0, float x1, float y1) {
        float vx = x1 - x0;
        float vy = y1 - y0;
        float lenSq = vx * vx + vy * vy;
        float t = lenSq == 0f ? 0f : ((px - x0) * vx + (py - y0) * vy) / lenSq;
        t = Math.max(0f, Math.min(1f, t));
        float cx = x0 + t * vx;
        float cy = y0 + t * vy;
        return (float) Math.hypot(px - cx, py - cy);
    }
}

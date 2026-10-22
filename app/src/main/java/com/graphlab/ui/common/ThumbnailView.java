package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;

/**
 * A tiny picture of a graph for the library list: edges as thin lines, nodes as dots, scaled
 * to fit. It draws straight from a {@link Graph} (no bitmap), so it is cheap to bind and stays
 * sharp on any screen. Colours come from the theme tokens.
 */
public class ThumbnailView extends View {
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private Graph graph;
    private float dp;

    public ThumbnailView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ThumbnailView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ThumbnailView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dp = getResources().getDisplayMetrics().density;
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1.5f * dp);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        nodeFill.setStyle(Paint.Style.FILL);
        nodeRing.setStyle(Paint.Style.STROKE);
        nodeRing.setStrokeWidth(1.5f * dp);
        edgePaint.setColor(MaterialColors.getColor(context, R.attr.gl_idle, Color.GRAY));
        nodeFill.setColor(MaterialColors.getColor(context, R.attr.gl_surface, Color.DKGRAY));
        nodeRing.setColor(MaterialColors.getColor(context, R.attr.gl_path, Color.BLUE));
    }

    /** The graph to draw, or null for an empty picture. */
    public void setGraph(@Nullable Graph graph) {
        this.graph = graph;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Graph g = graph;
        if (g == null || g.nodeCount() == 0) {
            return;
        }
        float pad = 10f * dp;
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        if (w <= 0 || h <= 0) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < g.nodeCount(); i++) {
            Node node = g.nodes().get(i);
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minY = Math.min(minY, node.y());
            maxY = Math.max(maxY, node.y());
        }
        double spanX = Math.max(maxX - minX, 1e-6);
        double spanY = Math.max(maxY - minY, 1e-6);
        double scale = Math.min(w / spanX, h / spanY);
        if (g.nodeCount() == 1) {
            scale = 0;
        }
        double offsetX = pad + (w - spanX * scale) / 2.0;
        double offsetY = pad + (h - spanY * scale) / 2.0;

        for (int i = 0; i < g.edgeCount(); i++) {
            Edge edge = g.edges().get(i);
            Node a = g.node(edge.from());
            Node b = g.node(edge.to());
            canvas.drawLine((float) (offsetX + (a.x() - minX) * scale), (float) (offsetY + (a.y() - minY) * scale),
                    (float) (offsetX + (b.x() - minX) * scale), (float) (offsetY + (b.y() - minY) * scale), edgePaint);
        }
        float r = 3.5f * dp;
        for (int i = 0; i < g.nodeCount(); i++) {
            Node node = g.nodes().get(i);
            float cx = (float) (offsetX + (node.x() - minX) * scale);
            float cy = (float) (offsetY + (node.y() - minY) * scale);
            canvas.drawCircle(cx, cy, r, nodeFill);
            canvas.drawCircle(cx, cy, r, nodeRing);
        }
    }
}

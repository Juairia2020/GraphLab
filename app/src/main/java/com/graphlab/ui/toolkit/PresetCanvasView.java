package com.graphlab.ui.toolkit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.preset.PresetResult;
import com.graphlab.engine.preset.PresetTool;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.ui.common.StateColors;
import com.graphlab.engine.trace.State;
import java.util.HashSet;
import java.util.Set;

/** A plain canvas for a preset graph (MS13): places, roads or pipes with their numbers, and the answer drawn on top. Not interactive. */
public class PresetCanvasView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Graph graph;
    private PresetResult result;
    private int source = -1;
    private int target = -1;

    public PresetCanvasView(@NonNull Context context) {
        this(context, null);
    }

    public PresetCanvasView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void show(@NonNull Graph graph, int source, int target, @Nullable PresetResult result) {
        this.graph = graph;
        this.source = source;
        this.target = target;
        this.result = result;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (graph == null || graph.nodeCount() == 0) {
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        float radius = 20 * dp;
        float pad = radius + 12 * dp;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node n : graph.nodes()) {
            minX = Math.min(minX, n.x());
            maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y());
            maxY = Math.max(maxY, n.y());
        }
        double w = Math.max(1.0, maxX - minX);
        double h = Math.max(1.0, maxY - minY);
        double scale = Math.min((getWidth() - 2 * pad) / w, (getHeight() - 2 * pad) / h);
        double offX = (getWidth() - w * scale) / 2.0;
        double offY = (getHeight() - h * scale) / 2.0;

        int[] colours = StateColors.resolve(getContext());
        int idle = colours[State.IDLE.ordinal()];
        int path = colours[State.PATH.ordinal()];
        int fail = colours[State.FAIL.ordinal()];
        int visited = colours[State.VISITED.ordinal()];
        int chosen = colours[State.CHOSEN.ordinal()];
        int surface = MaterialColors.getColor(this, R.attr.gl_surface);
        int ink = MaterialColors.getColor(this, R.attr.gl_text);

        Set<Integer> highlight = new HashSet<>();
        Set<Integer> carrying = new HashSet<>();
        Set<Integer> onRoute = new HashSet<>();
        if (result != null) {
            highlight.addAll(result.highlightEdges());
            carrying.addAll(result.flowEdges());
            onRoute.addAll(result.route());
        }
        boolean cut = result != null && result.tool() == PresetTool.MAX_FLOW;

        text.setTextSize(13 * dp);
        for (Edge e : graph.edges()) {
            Node a = graph.node(e.from());
            Node b = graph.node(e.to());
            float ax = (float) (offX + (a.x() - minX) * scale), ay = (float) (offY + (a.y() - minY) * scale);
            float bx = (float) (offX + (b.x() - minX) * scale), by = (float) (offY + (b.y() - minY) * scale);
            boolean marked = highlight.contains(e.id());
            line.setPathEffect(null);
            if (marked && cut) {
                line.setColor(fail);
                line.setStrokeWidth(4 * dp);
                line.setPathEffect(new DashPathEffect(new float[] {10 * dp, 6 * dp}, 0));
            } else if (marked) {
                line.setColor(path);
                line.setStrokeWidth(5 * dp);
            } else if (carrying.contains(e.id())) {
                line.setColor(visited);
                line.setStrokeWidth(4 * dp);
            } else {
                line.setColor(idle);
                line.setStrokeWidth(2 * dp);
            }
            canvas.drawLine(ax, ay, bx, by, line);
            line.setPathEffect(null);
            if (graph.isDirected()) {
                arrow(canvas, ax, ay, bx, by, radius, 9 * dp);
            }
            float mx = (ax + bx) / 2f, my = (ay + by) / 2f;
            fill.setColor(surface);
            canvas.drawCircle(mx, my - 4 * dp, 11 * dp, fill);
            text.setColor(ink);
            canvas.drawText(WeightFormat.format(e.weight()), mx, my, text);
        }
        for (Node n : graph.nodes()) {
            float x = (float) (offX + (n.x() - minX) * scale), y = (float) (offY + (n.y() - minY) * scale);
            fill.setColor(n.id() == source || n.id() == target ? chosen : onRoute.contains(n.id()) ? path : surface);
            canvas.drawCircle(x, y, radius, fill);
            line.setColor(ink);
            line.setStrokeWidth(1.5f * dp);
            canvas.drawCircle(x, y, radius, line);
            text.setTextSize(10 * dp);
            text.setColor(n.id() == source || n.id() == target || onRoute.contains(n.id())
                    ? MaterialColors.getColor(this, R.attr.gl_on_primary) : ink);
            String label = n.label().length() > 7 ? n.label().substring(0, 6) + "." : n.label();
            canvas.drawText(label, x, y + 3.5f * dp, text);
            text.setTextSize(13 * dp);
        }
    }

    private void arrow(Canvas canvas, float ax, float ay, float bx, float by, float radius, float size) {
        double angle = Math.atan2(by - ay, bx - ax);
        float tx = (float) (bx - Math.cos(angle) * radius);
        float ty = (float) (by - Math.sin(angle) * radius);
        for (int side = -1; side <= 1; side += 2) {
            double wing = angle + Math.PI + side * 0.45;
            canvas.drawLine(tx, ty, (float) (tx + Math.cos(wing) * size), (float) (ty + Math.sin(wing) * size), line);
        }
    }
}

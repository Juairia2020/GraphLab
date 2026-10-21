package com.graphlab.ui.imports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.imageimport.DetectedGraph;
import com.graphlab.engine.imageimport.ReviewSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The graph as the importer read it (MS16). Anything the importer was unsure of is drawn in amber: a doubtful node gets an
 * amber ring, a doubtful line is amber and dashed, and a doubtful or missing weight is an amber label ("?" when missing).
 * A tap on a node or a line tells the screen which one, so it can open the fix dialog.
 */
public class ReviewCanvasView extends View {
    /** Called when the person taps something. */
    public interface Listener {
        void onNode(int nodeId);

        void onEdge(int edgeId);
    }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ReviewSession session;
    private Listener listener;
    private final Map<Integer, float[]> nodeAt = new HashMap<>();
    private final Map<Integer, float[]> edgeMid = new HashMap<>();
    private float nodeRadius;

    public ReviewCanvasView(@NonNull Context context) {
        this(context, null);
    }

    public ReviewCanvasView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        setContentDescription(getContext().getString(R.string.import_canvas_description));
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void show(@NonNull ReviewSession session) {
        this.session = session;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        nodeAt.clear();
        edgeMid.clear();
        if (session == null || session.nodes().isEmpty()) {
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        nodeRadius = 18 * dp;
        layout(dp);
        int ink = MaterialColors.getColor(this, R.attr.gl_text, 0xFF161933);
        int muted = MaterialColors.getColor(this, R.attr.gl_idle, 0xFF7A82B8);
        int amber = MaterialColors.getColor(this, R.attr.gl_frontier, 0xFFA65B00);
        int surface = MaterialColors.getColor(this, R.attr.gl_surface, 0xFFFFFFFF);

        Set<Integer> badNode = new HashSet<>();
        Set<Integer> badEdge = new HashSet<>();
        Set<Integer> badWeight = new HashSet<>();
        for (ReviewSession.Flag f : session.flags()) {
            switch (f.kind()) {
                case NODE:
                    badNode.add(f.id());
                    break;
                case EDGE:
                    badEdge.add(f.id());
                    break;
                case WEIGHT:
                    badWeight.add(f.id());
                    break;
                default:
                    break;
            }
        }
        text.setTextSize(13 * dp);
        labelBg.setStyle(Paint.Style.FILL);
        labelBg.setColor(surface);

        for (DetectedGraph.Edge e : session.edges()) {
            float[] a = nodeAt.get(e.from());
            float[] b = nodeAt.get(e.to());
            if (a == null || b == null) {
                continue;
            }
            boolean bad = badEdge.contains(e.id());
            line.setStrokeWidth((bad ? 3.5f : 2.5f) * dp);
            line.setColor(bad ? amber : muted);
            line.setPathEffect(bad ? new DashPathEffect(new float[] {10 * dp, 6 * dp}, 0) : null);
            canvas.drawLine(a[0], a[1], b[0], b[1], line);
            line.setPathEffect(null);
            if (session.directed()) {
                arrow(canvas, a, b, bad ? amber : muted, dp);
            }
            float mx = (a[0] + b[0]) / 2;
            float my = (a[1] + b[1]) / 2;
            edgeMid.put(e.id(), new float[] {mx, my});
            if (session.weighted()) {
                boolean weightBad = badWeight.contains(e.id());
                String w = e.weight() == null ? "?" : WeightFormat.format(e.weight());
                float tw = text.measureText(w);
                canvas.drawRoundRect(mx - tw / 2 - 5 * dp, my - 11 * dp, mx + tw / 2 + 5 * dp, my + 9 * dp, 6 * dp, 6 * dp, labelBg);
                text.setColor(weightBad ? amber : ink);
                canvas.drawText(w, mx, my + 5 * dp, text);
            }
        }
        for (DetectedGraph.Node n : session.nodes()) {
            float[] p = nodeAt.get(n.id());
            boolean bad = badNode.contains(n.id());
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(surface);
            canvas.drawCircle(p[0], p[1], nodeRadius, fill);
            line.setStrokeWidth((bad ? 4f : 2.5f) * dp);
            line.setColor(bad ? amber : ink);
            canvas.drawCircle(p[0], p[1], nodeRadius, line);
            text.setColor(bad ? amber : ink);
            canvas.drawText(n.label(), p[0], p[1] + 5 * dp, text);
        }
    }

    /** Positions from the picture when there are some, otherwise on a circle. Always fitted inside the view. */
    private void layout(float dp) {
        List<DetectedGraph.Node> nodes = session.nodes();
        float pad = nodeRadius + 10 * dp;
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        int n = nodes.size();
        if (!session.hasPositions() || n < 2) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.max(10 * dp, Math.min(w, h) / 2f);
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + 2 * Math.PI * i / n;
                nodeAt.put(nodes.get(i).id(), new float[] {(float) (cx + r * Math.cos(a)), (float) (cy + r * Math.sin(a))});
            }
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (DetectedGraph.Node nd : nodes) {
            minX = Math.min(minX, nd.x());
            maxX = Math.max(maxX, nd.x());
            minY = Math.min(minY, nd.y());
            maxY = Math.max(maxY, nd.y());
        }
        double sx = maxX > minX ? w / (maxX - minX) : 1;
        double sy = maxY > minY ? h / (maxY - minY) : 1;
        double s = Math.min(sx, sy);
        double ox = pad + (w - (maxX - minX) * s) / 2;
        double oy = pad + (h - (maxY - minY) * s) / 2;
        for (DetectedGraph.Node nd : nodes) {
            nodeAt.put(nd.id(), new float[] {(float) (ox + (nd.x() - minX) * s), (float) (oy + (nd.y() - minY) * s)});
        }
    }

    private void arrow(Canvas canvas, float[] a, float[] b, int color, float dp) {
        double ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
        float tipX = (float) (b[0] - Math.cos(ang) * nodeRadius);
        float tipY = (float) (b[1] - Math.sin(ang) * nodeRadius);
        float len = 10 * dp;
        Path path = new Path();
        path.moveTo(tipX, tipY);
        path.lineTo((float) (tipX - len * Math.cos(ang - 0.45)), (float) (tipY - len * Math.sin(ang - 0.45)));
        path.lineTo((float) (tipX - len * Math.cos(ang + 0.45)), (float) (tipY - len * Math.sin(ang + 0.45)));
        path.close();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null || session == null) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dp = getResources().getDisplayMetrics().density;
            float x = event.getX();
            float y = event.getY();
            int bestNode = -1;
            float bestD = nodeRadius + 8 * dp;
            for (Map.Entry<Integer, float[]> e : nodeAt.entrySet()) {
                float d = (float) Math.hypot(x - e.getValue()[0], y - e.getValue()[1]);
                if (d < bestD) {
                    bestD = d;
                    bestNode = e.getKey();
                }
            }
            if (bestNode >= 0) {
                performClick();
                listener.onNode(bestNode);
                return true;
            }
            int bestEdge = -1;
            float bestE = 36 * dp;
            for (Map.Entry<Integer, float[]> e : edgeMid.entrySet()) {
                float d = (float) Math.hypot(x - e.getValue()[0], y - e.getValue()[1]);
                if (d < bestE) {
                    bestE = d;
                    bestEdge = e.getKey();
                }
            }
            if (bestEdge >= 0) {
                performClick();
                listener.onEdge(bestEdge);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

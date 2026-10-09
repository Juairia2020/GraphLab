package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.edit.EdgeShape;
import com.graphlab.engine.edit.EditorMetrics;
import com.graphlab.engine.edit.GraphEditor;
import com.graphlab.engine.edit.HitTester;
import com.graphlab.engine.edit.Viewport;
import com.graphlab.engine.edit.WeightFormat;
import com.graphlab.engine.model.Node;

/**
 * The graph canvas: draws a {@link GraphEditor} through a {@link Viewport} and turns touches
 * into edits. The model and the camera live outside (in the ViewModel), so this View can be
 * thrown away and rebuilt on rotation without losing anything.
 *
 * <p>Colours come from the theme tokens (?attr/gl_*), never from hex values. Everything that
 * onDraw uses is allocated once, so drawing does not create garbage; the only allocation is
 * the cache of weight texts, rebuilt when an edit changes a weight.
 */
public class GraphView extends View {

    /** What the screen has to do for the View: dialogs, messages and keeping the status text fresh. */
    public interface Listener {
        /** The user joined two different nodes that are not connected yet. */
        void onConnectRequested(int fromId, int toId);

        /** The user tapped an edge to change its weight (only sent when the graph is weighted). */
        void onEdgeWeightRequested(int edgeId);

        void onMessage(@StringRes int message);

        /** Nodes or edges were added or removed. */
        void onGraphChanged();
    }

    private enum Drag { NONE, PAN, MOVE_NODE, CONNECT }

    private static final double GRID_WORLD = 40.0;

    // ---- model, camera, tool ----
    private GraphEditor editor = new GraphEditor(false, true);
    private Viewport viewport = new Viewport();
    private EditorTool tool = EditorTool.NODE;
    @Nullable
    private Listener listener;

    // ---- drawing tools, created once ----
    private final Paint gridPaint = new Paint();
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rubberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final EdgeShape shape = new EdgeShape();
    private final Path path = new Path();
    private final RectF borderRect = new RectF();
    private final RectF pillRect = new RectF();

    private int colorBg;
    private int colorIdle;
    private int colorSurface;
    private int colorText;
    private int colorChosen;
    private int colorVisited;
    private int colorPath;

    private float dp;
    private float nodeSlopPx;
    private float edgeSlopPx;
    private float cornerRadius;
    private float nodeTextMin;
    private float nodeTextMax;
    private String emptyTitle = "";
    private String emptyBody = "";

    // ---- cache of weight texts, rebuilt only when an edit changes a weight ----
    private String[] weightLabels = new String[0];
    private int labelsVersion = -1;
    @Nullable
    private GraphEditor labelsEditor;

    // ---- touch state ----
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private Drag drag = Drag.NONE;
    private boolean pinched;
    private int downNodeId = -1;
    private int sourceNodeId = -1;
    private int hoverNodeId = -1;
    private float pointerX;
    private float pointerY;
    private float lastFocusX;
    private float lastFocusY;

    public GraphView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GraphView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GraphView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dp = getResources().getDisplayMetrics().density;
        nodeSlopPx = (float) (EditorMetrics.NODE_SLOP_DP * dp);
        edgeSlopPx = (float) (EditorMetrics.EDGE_SLOP_DP * dp);
        cornerRadius = 14f * dp;
        nodeTextMin = sp(11);
        nodeTextMax = sp(20);
        emptyTitle = context.getString(R.string.canvas_empty_title);
        emptyBody = context.getString(R.string.canvas_empty_body);

        gridPaint.setStrokeWidth(1f * dp);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2.5f * dp);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStyle(Paint.Style.FILL);
        pillFillPaint.setStyle(Paint.Style.FILL);
        pillStrokePaint.setStyle(Paint.Style.STROKE);
        pillStrokePaint.setStrokeWidth(1f * dp);
        pillTextPaint.setTextAlign(Paint.Align.CENTER);
        pillTextPaint.setTextSize(sp(12));
        nodeFillPaint.setStyle(Paint.Style.FILL);
        nodeStrokePaint.setStyle(Paint.Style.STROKE);
        nodeTextPaint.setTextAlign(Paint.Align.CENTER);
        nodeTextPaint.setFakeBoldText(true);
        rubberPaint.setStyle(Paint.Style.STROKE);
        rubberPaint.setStrokeWidth(2.5f * dp);
        rubberPaint.setStrokeCap(Paint.Cap.ROUND);
        rubberPaint.setPathEffect(new DashPathEffect(new float[] {10f * dp, 8f * dp}, 0f));
        hintTitlePaint.setTextAlign(Paint.Align.CENTER);
        hintTitlePaint.setTextSize(sp(16));
        hintTitlePaint.setFakeBoldText(true);
        hintBodyPaint.setTextAlign(Paint.Align.CENTER);
        hintBodyPaint.setTextSize(sp(14));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * dp);

        refreshColors();

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        scaleDetector.setQuickScaleEnabled(false);
        gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
        setFocusable(true);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    /** Reads the theme tokens. Called on creation and whenever the View is attached. */
    private void refreshColors() {
        Context c = getContext();
        colorBg = MaterialColors.getColor(c, R.attr.gl_code_bg, Color.BLACK);
        colorIdle = MaterialColors.getColor(c, R.attr.gl_idle, Color.GRAY);
        colorSurface = MaterialColors.getColor(c, R.attr.gl_surface, Color.DKGRAY);
        colorText = MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE);
        colorChosen = MaterialColors.getColor(c, R.attr.gl_chosen, Color.MAGENTA);
        colorVisited = MaterialColors.getColor(c, R.attr.gl_visited, Color.GREEN);
        colorPath = MaterialColors.getColor(c, R.attr.gl_path, Color.BLUE);
        int colorGrid = MaterialColors.getColor(c, R.attr.gl_grid, Color.DKGRAY);
        int colorOutline = MaterialColors.getColor(c, R.attr.gl_outline, Color.GRAY);
        int colorMuted = MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY);

        gridPaint.setColor(colorGrid);
        edgePaint.setColor(colorIdle);
        arrowPaint.setColor(colorIdle);
        pillFillPaint.setColor(colorSurface);
        pillStrokePaint.setColor(colorIdle);
        pillTextPaint.setColor(colorText);
        nodeFillPaint.setColor(colorSurface);
        nodeTextPaint.setColor(colorText);
        rubberPaint.setColor(colorChosen);
        hintTitlePaint.setColor(colorText);
        hintBodyPaint.setColor(colorMuted);
        borderPaint.setColor(colorOutline);
    }

    // ---- wiring ----------------------------------------------------------------------

    /** Hands the View its model and camera (both owned by the ViewModel). */
    public void bind(@NonNull GraphEditor editor, @NonNull Viewport viewport) {
        this.editor = editor;
        this.viewport = viewport;
        viewport.setDensity(dp);
        if (getWidth() > 0 && getHeight() > 0) {
            viewport.setSize(getWidth(), getHeight());
        }
        sourceNodeId = -1;
        hoverNodeId = -1;
        drag = Drag.NONE;
        invalidate();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setTool(@NonNull EditorTool tool) {
        this.tool = tool;
        sourceNodeId = -1;
        hoverNodeId = -1;
        drag = Drag.NONE;
        invalidate();
    }

    /** Redraws after the model changed from outside (a dialog, a settings switch). */
    public void refresh() {
        invalidate();
    }

    /** Centres and zooms so the whole graph is visible. */
    public void fitToGraph() {
        viewport.fit(editor.bounds(), EditorMetrics.NODE_RADIUS, EditorMetrics.FIT_MARGIN_DP * dp);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshColors();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewport.setSize(w, h);
        borderRect.set(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp);
        invalidateOutline();
        if (viewport.consumeFitRequest()) {
            fitToGraph();
        }
    }

    // ---- drawing ---------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(colorBg);
        drawGrid(canvas, w, h);
        syncWeightLabels();
        drawEdges(canvas);
        drawWeightPills(canvas);
        drawRubberBand(canvas);
        drawNodes(canvas);
        if (editor.nodeCount() == 0) {
            drawEmptyHint(canvas, w, h);
        }
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint);
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        double scale = viewport.scale();
        if (GRID_WORLD * scale < 10 * dp) {
            return;   // too dense to be useful
        }
        double firstX = Math.ceil(viewport.screenToWorldX(0) / GRID_WORLD) * GRID_WORLD;
        for (double wx = firstX; ; wx += GRID_WORLD) {
            float sx = (float) viewport.worldToScreenX(wx);
            if (sx > w) {
                break;
            }
            canvas.drawLine(sx, 0, sx, h, gridPaint);
        }
        double firstY = Math.ceil(viewport.screenToWorldY(0) / GRID_WORLD) * GRID_WORLD;
        for (double wy = firstY; ; wy += GRID_WORLD) {
            float sy = (float) viewport.worldToScreenY(wy);
            if (sy > h) {
                break;
            }
            canvas.drawLine(0, sy, w, sy, gridPaint);
        }
    }

    private void syncWeightLabels() {
        if (labelsEditor == editor && labelsVersion == editor.structureVersion()) {
            return;
        }
        int n = editor.edgeCount();
        weightLabels = new String[n];
        for (int i = 0; i < n; i++) {
            weightLabels[i] = WeightFormat.format(editor.edgeAt(i).weight());
        }
        labelsEditor = editor;
        labelsVersion = editor.structureVersion();
    }

    /** Fills {@link #shape} for edge {@code i} in screen pixels. */
    private void shapeOfEdge(int i, float radius, float bend) {
        Node from = editor.nodeAt(editor.edgeFromIndex(i));
        Node to = editor.nodeAt(editor.edgeToIndex(i));
        shape.set((float) viewport.worldToScreenX(from.x()), (float) viewport.worldToScreenY(from.y()),
                (float) viewport.worldToScreenX(to.x()), (float) viewport.worldToScreenY(to.y()),
                editor.edgeIsReciprocal(i) ? bend : 0f, radius);
    }

    private void drawEdges(Canvas canvas) {
        float radius = (float) (EditorMetrics.NODE_RADIUS * viewport.scale());
        float bend = (float) (EditorMetrics.RECIPROCAL_BEND * viewport.scale());
        boolean directed = editor.isDirected();
        for (int i = 0; i < editor.edgeCount(); i++) {
            shapeOfEdge(i, radius, bend);
            if (!shape.visible) {
                continue;
            }
            path.reset();
            path.moveTo(shape.startX, shape.startY);
            if (shape.curved) {
                path.quadTo(shape.ctrlX, shape.ctrlY, shape.endX, shape.endY);
            } else {
                path.lineTo(shape.endX, shape.endY);
            }
            canvas.drawPath(path, edgePaint);
            if (directed) {
                drawArrowHead(canvas);
            }
        }
    }

    private void drawArrowHead(Canvas canvas) {
        float length = 12f * dp;
        float half = 5.5f * dp;
        float bx = shape.endX - shape.dirX * length;
        float by = shape.endY - shape.dirY * length;
        float px = -shape.dirY * half;
        float py = shape.dirX * half;
        path.reset();
        path.moveTo(shape.endX, shape.endY);
        path.lineTo(bx + px, by + py);
        path.lineTo(bx - px, by - py);
        path.close();
        canvas.drawPath(path, arrowPaint);
    }

    private void drawWeightPills(Canvas canvas) {
        if (!editor.isWeighted()) {
            return;
        }
        float radius = (float) (EditorMetrics.NODE_RADIUS * viewport.scale());
        float bend = (float) (EditorMetrics.RECIPROCAL_BEND * viewport.scale());
        float height = 22f * dp;
        pillTextPaint.getFontMetrics(fontMetrics);
        float baselineShift = -(fontMetrics.ascent + fontMetrics.descent) / 2f;
        for (int i = 0; i < editor.edgeCount(); i++) {
            shapeOfEdge(i, radius, bend);
            if (!shape.visible) {
                continue;
            }
            String text = weightLabels[i];
            float width = Math.max(height, pillTextPaint.measureText(text) + 14f * dp);
            pillRect.set(shape.midX - width / 2f, shape.midY - height / 2f,
                    shape.midX + width / 2f, shape.midY + height / 2f);
            canvas.drawRoundRect(pillRect, height / 2f, height / 2f, pillFillPaint);
            canvas.drawRoundRect(pillRect, height / 2f, height / 2f, pillStrokePaint);
            canvas.drawText(text, shape.midX, shape.midY + baselineShift, pillTextPaint);
        }
    }

    private void drawRubberBand(Canvas canvas) {
        if (drag != Drag.CONNECT || sourceNodeId < 0) {
            return;
        }
        Node source = editor.findNode(sourceNodeId);
        if (source == null) {
            return;
        }
        canvas.drawLine((float) viewport.worldToScreenX(source.x()), (float) viewport.worldToScreenY(source.y()),
                pointerX, pointerY, rubberPaint);
    }

    private void drawNodes(Canvas canvas) {
        float radius = (float) (EditorMetrics.NODE_RADIUS * viewport.scale());
        nodeTextPaint.setTextSize(Math.max(nodeTextMin, Math.min(radius * 0.85f, nodeTextMax)));
        nodeTextPaint.getFontMetrics(fontMetrics);
        float baselineShift = -(fontMetrics.ascent + fontMetrics.descent) / 2f;
        for (int i = 0; i < editor.nodeCount(); i++) {
            Node node = editor.nodeAt(i);
            float cx = (float) viewport.worldToScreenX(node.x());
            float cy = (float) viewport.worldToScreenY(node.y());
            int ring = colorIdle;
            float ringWidth = 2.5f * dp;
            if (node.id() == sourceNodeId) {
                ring = colorChosen;
                ringWidth = 4f * dp;
            } else if (node.id() == hoverNodeId) {
                ring = colorVisited;
                ringWidth = 4f * dp;
            } else if (drag == Drag.MOVE_NODE && node.id() == downNodeId) {
                ring = colorPath;
                ringWidth = 4f * dp;
            }
            canvas.drawCircle(cx, cy, radius, nodeFillPaint);
            nodeStrokePaint.setColor(ring);
            nodeStrokePaint.setStrokeWidth(ringWidth);
            canvas.drawCircle(cx, cy, radius, nodeStrokePaint);
            canvas.drawText(node.label(), cx, cy + baselineShift, nodeTextPaint);
        }
    }

    private void drawEmptyHint(Canvas canvas, int w, int h) {
        canvas.drawText(emptyTitle, w / 2f, h / 2f - 4f * dp, hintTitlePaint);
        canvas.drawText(emptyBody, w / 2f, h / 2f + 20f * dp, hintBodyPaint);
    }

    // ---- touch -----------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pinched = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);   // keep the canvas gestures to ourselves
            }
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP) {
            finishDrag(event.getX(), event.getY());
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelDrag();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float nodeSlop() {
        return nodeSlopPx;
    }

    private void startDrag() {
        switch (tool) {
            case MOVE:
                drag = downNodeId >= 0 ? Drag.MOVE_NODE : Drag.PAN;
                break;
            case EDGE:
                if (downNodeId >= 0) {
                    drag = Drag.CONNECT;
                    sourceNodeId = downNodeId;
                } else {
                    drag = Drag.PAN;
                }
                break;
            default:
                drag = Drag.PAN;
                break;
        }
    }

    private void finishDrag(float x, float y) {
        if (drag == Drag.CONNECT && sourceNodeId >= 0) {
            int source = sourceNodeId;
            int target = HitTester.nodeAt(editor, viewport, x, y, nodeSlop(), source);
            sourceNodeId = -1;
            hoverNodeId = -1;
            if (target >= 0 && listener != null) {
                listener.onConnectRequested(source, target);
            }
        }
        drag = Drag.NONE;
        downNodeId = -1;
        invalidate();
    }

    private void cancelDrag() {
        if (drag == Drag.CONNECT) {
            sourceNodeId = -1;
        }
        hoverNodeId = -1;
        drag = Drag.NONE;
        downNodeId = -1;
        invalidate();
    }

    private void handleTap(float x, float y) {
        int node = HitTester.nodeAt(editor, viewport, x, y, nodeSlop(), -1);
        int edge = node < 0 ? HitTester.edgeAt(editor, viewport, x, y, edgeSlopPx) : -1;
        switch (tool) {
            case NODE:
                if (node < 0) {
                    addNodeAt(x, y);
                }
                break;
            case EDGE:
                if (node >= 0) {
                    tapNodeWithEdgeTool(node);
                } else if (edge >= 0) {
                    requestWeight(edge);
                } else {
                    sourceNodeId = -1;
                }
                break;
            case MOVE:
                if (edge >= 0) {
                    requestWeight(edge);
                }
                break;
            case DELETE:
                if (node >= 0) {
                    editor.removeNode(node);
                    if (node == sourceNodeId) {
                        sourceNodeId = -1;
                    }
                    graphChanged();
                } else if (edge >= 0) {
                    editor.removeEdge(edge);
                    graphChanged();
                }
                break;
            default:
                break;
        }
        invalidate();
    }

    private void addNodeAt(float x, float y) {
        if (!editor.canAddNode()) {
            message(R.string.msg_node_limit);
            return;
        }
        double wx = viewport.screenToWorldX(x);
        double wy = viewport.screenToWorldY(y);
        if (!editor.isFreeSpot(wx, wy)) {
            message(R.string.msg_too_close);
            return;
        }
        editor.addNode(wx, wy);
        graphChanged();
    }

    private void tapNodeWithEdgeTool(int node) {
        if (sourceNodeId < 0) {
            sourceNodeId = node;
        } else if (sourceNodeId == node) {
            sourceNodeId = -1;
        } else {
            int source = sourceNodeId;
            sourceNodeId = -1;
            if (listener != null) {
                listener.onConnectRequested(source, node);
            }
        }
    }

    private void requestWeight(int edgeId) {
        if (!editor.isWeighted()) {
            message(R.string.msg_unweighted);
        } else if (listener != null) {
            listener.onEdgeWeightRequested(edgeId);
        }
    }

    private void message(@StringRes int resId) {
        if (listener != null) {
            listener.onMessage(resId);
        }
    }

    private void graphChanged() {
        invalidate();
        if (listener != null) {
            listener.onGraphChanged();
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            drag = Drag.NONE;
            downNodeId = HitTester.nodeAt(editor, viewport, e.getX(), e.getY(), nodeSlop(), -1);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            performClick();
            handleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (pinched || scaleDetector.isInProgress() || e2.getPointerCount() > 1) {
                return true;   // pinch handles its own panning
            }
            if (drag == Drag.NONE) {
                startDrag();
            }
            switch (drag) {
                case PAN:
                    viewport.panByScreen(-distanceX, -distanceY);
                    break;
                case MOVE_NODE: {
                    Node node = editor.findNode(downNodeId);
                    if (node != null) {
                        double scale = viewport.scale();
                        editor.moveNode(downNodeId, node.x() - distanceX / scale, node.y() - distanceY / scale);
                    }
                    break;
                }
                case CONNECT:
                    pointerX = e2.getX();
                    pointerY = e2.getY();
                    hoverNodeId = HitTester.nodeAt(editor, viewport, pointerX, pointerY, nodeSlop(), sourceNodeId);
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            pinched = true;
            cancelDrag();
            lastFocusX = detector.getFocusX();
            lastFocusY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float fx = detector.getFocusX();
            float fy = detector.getFocusY();
            viewport.panByScreen(fx - lastFocusX, fy - lastFocusY);
            viewport.zoomAt(detector.getScaleFactor(), fx, fy);
            lastFocusX = fx;
            lastFocusY = fy;
            invalidate();
            return true;
        }
    }
}

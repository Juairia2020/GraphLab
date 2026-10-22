package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.State;

/**
 * Draws a {@link GridFrame} as a table: row and column headers stay in view while the cells
 * scroll with a drag, and the view scrolls by itself to keep the cell being worked on in sight.
 * A cell's state tints it and gives it an outline (dashed for the cells a value comes from, thick
 * for the cell being filled), so it is not colour alone.
 */
public class GridView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint header = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final RectF rect = new RectF();
    private final RectF borderRect = new RectF();
    private int[] stateColors = new int[State.values().length];
    private int bg;
    private float dp;
    private float corner;
    private float cellW;
    private float cellH;
    private float headW;
    private float headH;
    private float scrollX;
    private float scrollY;
    @Nullable
    private GridFrame frame;
    private GestureDetector gestures;

    public GridView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dp = getResources().getDisplayMetrics().density;
        corner = 14f * dp;
        cellW = 44f * dp;
        cellH = 34f * dp;
        headW = 76f * dp;
        headH = 34f * dp;
        fill.setStyle(Paint.Style.FILL);
        outline.setStyle(Paint.Style.STROKE);
        headerFill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sp(13));
        header.setTextAlign(Paint.Align.CENTER);
        header.setTextSize(sp(11));
        header.setFakeBoldText(true);
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTextSize(sp(14));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f * dp);
        refreshColors();
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, MotionEvent e2, float dx, float dy) {
                scrollX += dx;
                scrollY += dy;
                clampScroll();
                invalidate();
                return true;
            }
        });
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private void refreshColors() {
        Context c = getContext();
        stateColors = StateColors.resolve(c);
        bg = MaterialColors.getColor(c, R.attr.gl_code_bg, Color.BLACK);
        text.setColor(MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE));
        header.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        headerFill.setColor(MaterialColors.getColor(c, R.attr.gl_surface_raised, Color.DKGRAY));
        hint.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        border.setColor(MaterialColors.getColor(c, R.attr.gl_outline, Color.GRAY));
    }

    /** The step to draw, or null for an empty view. Scrolls to keep the active cell visible. */
    public void setFrame(@Nullable GridFrame frame) {
        boolean first = this.frame == null;
        this.frame = frame;
        if (frame != null) {
            if (first) {
                scrollX = 0;
                scrollY = 0;
            }
            followActiveCell();
        }
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
        borderRect.set(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp);
        clampScroll();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void clampScroll() {
        GridFrame f = frame;
        if (f == null) {
            return;
        }
        float maxX = Math.max(0, f.cols() * cellW - (getWidth() - headW));
        float maxY = Math.max(0, f.rows() * cellH - (getHeight() - headH));
        scrollX = Math.max(0, Math.min(maxX, scrollX));
        scrollY = Math.max(0, Math.min(maxY, scrollY));
    }

    private void followActiveCell() {
        GridFrame f = frame;
        if (f == null || getWidth() == 0) {
            return;
        }
        for (int r = 0; r < f.rows(); r++) {
            for (int c = 0; c < f.cols(); c++) {
                if (f.state(r, c) == State.CHOSEN) {
                    float viewW = getWidth() - headW;
                    float viewH = getHeight() - headH;
                    if (c * cellW < scrollX) {
                        scrollX = c * cellW;
                    } else if ((c + 1) * cellW > scrollX + viewW) {
                        scrollX = (c + 1) * cellW - viewW;
                    }
                    if (r * cellH < scrollY) {
                        scrollY = r * cellH;
                    } else if ((r + 1) * cellH > scrollY + viewH) {
                        scrollY = (r + 1) * cellH - viewH;
                    }
                    clampScroll();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bg);
        GridFrame f = frame;
        if (f == null) {
            canvas.drawText(getContext().getString(R.string.grid_empty), getWidth() / 2f, getHeight() / 2f, hint);
        } else {
            drawCells(canvas, f);
            drawHeaders(canvas, f);
        }
        canvas.drawRoundRect(borderRect, corner, corner, border);
    }

    private void drawCells(Canvas canvas, GridFrame f) {
        canvas.save();
        canvas.clipRect(headW, headH, getWidth(), getHeight());
        int firstRow = (int) Math.max(0, Math.floor(scrollY / cellH));
        int lastRow = (int) Math.min(f.rows() - 1, Math.ceil((scrollY + getHeight()) / cellH));
        int firstCol = (int) Math.max(0, Math.floor(scrollX / cellW));
        int lastCol = (int) Math.min(f.cols() - 1, Math.ceil((scrollX + getWidth()) / cellW));
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = firstCol; c <= lastCol; c++) {
                float x = headW + c * cellW - scrollX;
                float y = headH + r * cellH - scrollY;
                rect.set(x + 1.5f * dp, y + 1.5f * dp, x + cellW - 1.5f * dp, y + cellH - 1.5f * dp);
                State state = f.state(r, c);
                int color = stateColors[state.ordinal()];
                if (state != State.IDLE) {
                    fill.setColor(color);
                    fill.setAlpha(state == State.CHOSEN ? 150 : state == State.VISITED ? 55 : 95);
                    canvas.drawRoundRect(rect, 6f * dp, 6f * dp, fill);
                }
                outline.setColor(color);
                outline.setStrokeWidth((state == State.CHOSEN || state == State.PATH ? 3f : 1.5f) * dp);
                outline.setPathEffect(state == State.FRONTIER || state == State.FAIL
                        ? new android.graphics.DashPathEffect(new float[] {6f * dp, 4f * dp}, 0f) : null);
                canvas.drawRoundRect(rect, 6f * dp, 6f * dp, outline);
                String value = f.cell(r, c);
                if (!value.isEmpty()) {
                    text.getFontMetrics(fm);
                    canvas.drawText(value, x + cellW / 2f, y + cellH / 2f - (fm.ascent + fm.descent) / 2f, text);
                }
            }
        }
        canvas.restore();
    }

    private void drawHeaders(Canvas canvas, GridFrame f) {
        // Column headers across the top.
        canvas.save();
        canvas.clipRect(headW, 0, getWidth(), headH);
        for (int c = 0; c < f.cols(); c++) {
            float x = headW + c * cellW - scrollX;
            rect.set(x + 1.5f * dp, 2f * dp, x + cellW - 1.5f * dp, headH - 2f * dp);
            canvas.drawRoundRect(rect, 6f * dp, 6f * dp, headerFill);
            header.getFontMetrics(fm);
            canvas.drawText(f.colHeaders().get(c), x + cellW / 2f, headH / 2f - (fm.ascent + fm.descent) / 2f, header);
        }
        canvas.restore();
        // Row headers down the left side.
        canvas.save();
        canvas.clipRect(0, headH, headW, getHeight());
        for (int r = 0; r < f.rows(); r++) {
            float y = headH + r * cellH - scrollY;
            rect.set(2f * dp, y + 1.5f * dp, headW - 2f * dp, y + cellH - 1.5f * dp);
            canvas.drawRoundRect(rect, 6f * dp, 6f * dp, headerFill);
            header.getFontMetrics(fm);
            canvas.drawText(f.rowHeaders().get(r), headW / 2f, y + cellH / 2f - (fm.ascent + fm.descent) / 2f, header);
        }
        canvas.restore();
        rect.set(2f * dp, 2f * dp, headW - 2f * dp, headH - 2f * dp);
        canvas.drawRoundRect(rect, 6f * dp, 6f * dp, headerFill);
        header.getFontMetrics(fm);
        canvas.drawText(f.corner(), headW / 2f, headH / 2f - (fm.ascent + fm.descent) / 2f, header);
    }
}

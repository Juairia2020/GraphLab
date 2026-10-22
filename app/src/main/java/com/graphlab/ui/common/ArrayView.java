package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.State;
import java.util.Map;

/**
 * Draws an {@link ArrayFrame} as a row of bars: height is the value, colour is the state, the
 * value is printed above the bar, the 1-based position and any short text below it, and named
 * pointers ("lo", "hi", "i", "j") as small tags over the bars they point at. State is also
 * shown by the bar's outline (dashed for the active part), so it is not colour alone. No touch
 * handling; it only shows the current step.
 */
public class ArrayView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF borderRect = new RectF();
    private int[] stateColors = new int[State.values().length];
    private int bg;
    private float dp;
    private float corner;
    @Nullable
    private ArrayFrame frame;

    public ArrayView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ArrayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ArrayView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dp = getResources().getDisplayMetrics().density;
        corner = 14f * dp;
        barPaint.setStyle(Paint.Style.FILL);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2.5f * dp);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(13));
        textPaint.setFakeBoldText(true);
        mutedPaint.setTextAlign(Paint.Align.CENTER);
        mutedPaint.setTextSize(sp(11));
        tagFill.setStyle(Paint.Style.FILL);
        tagText.setTextAlign(Paint.Align.CENTER);
        tagText.setTextSize(sp(11));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(sp(14));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f * dp);
        refreshColors();
        setFocusable(false);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private void refreshColors() {
        Context c = getContext();
        stateColors = StateColors.resolve(c);
        bg = MaterialColors.getColor(c, R.attr.gl_code_bg, Color.BLACK);
        textPaint.setColor(MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE));
        mutedPaint.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        tagFill.setColor(MaterialColors.getColor(c, R.attr.gl_surface, Color.DKGRAY));
        tagText.setColor(MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE));
        hintPaint.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        border.setColor(MaterialColors.getColor(c, R.attr.gl_outline, Color.GRAY));
    }

    /** The step to draw, or null for an empty view. */
    public void setFrame(@Nullable ArrayFrame frame) {
        this.frame = frame;
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
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bg);
        ArrayFrame f = frame;
        float w = getWidth();
        float h = getHeight();
        if (f == null || f.values().isEmpty()) {
            canvas.drawText(getContext().getString(R.string.array_empty), w / 2f, h / 2f, hintPaint);
        } else {
            drawBars(canvas, f, w, h);
        }
        canvas.drawRoundRect(borderRect, corner, corner, border);
    }

    private void drawBars(Canvas canvas, ArrayFrame f, float w, float h) {
        int n = f.values().size();
        float pad = 12f * dp;
        float tagBand = 30f * dp;                   // room above the bars for pointer tags
        float footer = 38f * dp;                    // position number and short text
        float top = pad + tagBand;
        float bottom = h - pad - footer;
        float slot = (w - 2 * pad) / n;
        float barWidth = Math.min(slot * 0.74f, 48f * dp);
        int max = 1;
        for (int v : f.values()) {
            max = Math.max(max, v);
        }
        float usable = Math.max(bottom - top - 16f * dp, 10f * dp);
        for (int i = 0; i < n; i++) {
            float cx = pad + slot * (i + 0.5f);
            int value = f.values().get(i);
            State state = f.states().get(i);
            int color = stateColors[state.ordinal()];
            float barHeight = Math.max(6f * dp, usable * value / max);
            rect.set(cx - barWidth / 2f, bottom - barHeight, cx + barWidth / 2f, bottom);
            barPaint.setColor(color);
            barPaint.setAlpha(state == State.IDLE ? 70 : state == State.FRONTIER ? 110 : 255);
            canvas.drawRoundRect(rect, 6f * dp, 6f * dp, barPaint);
            outlinePaint.setColor(color);
            outlinePaint.setPathEffect(state == State.FRONTIER ? new android.graphics.DashPathEffect(new float[] {8f * dp, 5f * dp}, 0f) : null);
            canvas.drawRoundRect(rect, 6f * dp, 6f * dp, outlinePaint);
            canvas.drawText(Integer.toString(value), cx, bottom - barHeight - 5f * dp, textPaint);
            canvas.drawText(Integer.toString(i + 1), cx, bottom + 16f * dp, mutedPaint);
            String extra = f.text(i);
            if (!extra.isEmpty()) {
                canvas.drawText(extra, cx, bottom + 31f * dp, mutedPaint);
            }
        }
        // Pointer tags, stacked when several point at the same bar.
        int[] stack = new int[n];
        for (Map.Entry<String, Integer> marker : f.markers().entrySet()) {
            int i = marker.getValue();
            float cx = pad + slot * (i + 0.5f);
            float tagW = Math.max(26f * dp, tagText.measureText(marker.getKey()) + 12f * dp);
            float tagH = 20f * dp;
            float y = pad + stack[i] * (tagH + 2f * dp);
            stack[i]++;
            rect.set(cx - tagW / 2f, y, cx + tagW / 2f, y + tagH);
            canvas.drawRoundRect(rect, tagH / 2f, tagH / 2f, tagFill);
            outlinePaint.setColor(stateColors[State.CHOSEN.ordinal()]);
            outlinePaint.setPathEffect(null);
            outlinePaint.setStrokeWidth(1.5f * dp);
            canvas.drawRoundRect(rect, tagH / 2f, tagH / 2f, outlinePaint);
            outlinePaint.setStrokeWidth(2.5f * dp);
            Paint.FontMetrics fm = tagText.getFontMetrics();
            canvas.drawText(marker.getKey(), cx, y + tagH / 2f - (fm.ascent + fm.descent) / 2f, tagText);
        }
    }
}

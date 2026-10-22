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
import com.graphlab.engine.experiment.Histogram;
import java.util.List;

/**
 * A bar chart of a {@link Histogram}: one bar per result value (every whole number from the smallest to the
 * largest, empty ones included), height is how often it came up. The smallest value, the best cut in a Karger
 * experiment, is drawn in a second colour <em>and</em> with a heavier outline, so it does not depend on colour alone.
 * Counts are printed above the bars and values below them when there is room. Reused by later experiments.
 * No touch handling. The accessibility description lists every bar.
 */
public class HistogramView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF borderRect = new RectF();
    private int bg;
    private float dp;
    private float corner;
    @Nullable
    private Histogram histogram;

    public HistogramView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public HistogramView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HistogramView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        dp = getResources().getDisplayMetrics().density;
        corner = 14f * dp;
        barPaint.setStyle(Paint.Style.FILL);
        bestPaint.setStyle(Paint.Style.FILL);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2.5f * dp);
        axis.setStyle(Paint.Style.STROKE);
        axis.setStrokeWidth(1f * dp);
        countText.setTextAlign(Paint.Align.CENTER);
        countText.setTextSize(sp(11));
        countText.setFakeBoldText(true);
        valueText.setTextAlign(Paint.Align.CENTER);
        valueText.setTextSize(sp(12));
        hintText.setTextAlign(Paint.Align.CENTER);
        hintText.setTextSize(sp(14));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f * dp);
        refreshColors();
        setFocusable(false);
        setContentDescription(context.getString(R.string.histogram_empty));
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private void refreshColors() {
        Context c = getContext();
        bg = MaterialColors.getColor(c, R.attr.gl_code_bg, Color.BLACK);
        barPaint.setColor(MaterialColors.getColor(c, R.attr.gl_visited, Color.GRAY));
        bestPaint.setColor(MaterialColors.getColor(c, R.attr.gl_chosen, Color.GREEN));
        outline.setColor(MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE));
        axis.setColor(MaterialColors.getColor(c, R.attr.gl_outline, Color.GRAY));
        countText.setColor(MaterialColors.getColor(c, R.attr.gl_text, Color.WHITE));
        valueText.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        hintText.setColor(MaterialColors.getColor(c, R.attr.gl_text_muted, Color.LTGRAY));
        border.setColor(MaterialColors.getColor(c, R.attr.gl_outline, Color.GRAY));
    }

    /** The counts to draw, or null for an empty chart. The view keeps its own copy. */
    public void setHistogram(@Nullable Histogram histogram) {
        this.histogram = histogram == null || histogram.isEmpty() ? null : histogram.copy();
        setContentDescription(describe(this.histogram));
        invalidate();
    }

    private String describe(@Nullable Histogram h) {
        if (h == null) {
            return getContext().getString(R.string.histogram_empty);
        }
        StringBuilder out = new StringBuilder(getContext().getString(R.string.histogram_description_intro, h.total()));
        for (int[] e : h.entries()) {
            out.append(' ').append(getContext().getString(R.string.histogram_description_bar, e[0], e[1]));
        }
        return out.toString();
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
        float w = getWidth();
        float h = getHeight();
        Histogram data = histogram;
        if (data == null) {
            canvas.drawText(getContext().getString(R.string.histogram_empty), w / 2f, h / 2f, hintText);
        } else {
            drawBars(canvas, data, w, h);
        }
        canvas.drawRoundRect(borderRect, corner, corner, border);
    }

    private void drawBars(Canvas canvas, Histogram data, float w, float h) {
        List<int[]> bars = data.bars();
        int n = bars.size();
        float pad = 12f * dp;
        float labelBand = 20f * dp;      // values under the axis
        float countBand = 18f * dp;      // counts above the tallest bar
        float top = pad + countBand;
        float axisY = h - pad - labelBand;
        float slot = (w - 2 * pad) / n;
        float barWidth = Math.min(slot * 0.72f, 56f * dp);
        float tallest = Math.max(1, data.tallest());
        boolean showCounts = slot >= 22f * dp;
        int labelEvery = slot >= 18f * dp ? 1 : (int) Math.ceil(18f * dp / slot);
        int best = data.smallest();
        canvas.drawLine(pad, axisY, w - pad, axisY, axis);
        for (int i = 0; i < n; i++) {
            int value = bars.get(i)[0];
            int count = bars.get(i)[1];
            float cx = pad + slot * (i + 0.5f);
            float height = (axisY - top) * (count / tallest);
            boolean isBest = value == best;
            if (count > 0) {
                rect.set(cx - barWidth / 2f, axisY - Math.max(height, 2f * dp), cx + barWidth / 2f, axisY);
                canvas.drawRoundRect(rect, 4f * dp, 4f * dp, isBest ? bestPaint : barPaint);
                if (isBest) {
                    canvas.drawRoundRect(rect, 4f * dp, 4f * dp, outline);
                }
                if (showCounts) {
                    canvas.drawText(Integer.toString(count), cx, rect.top - 4f * dp, countText);
                }
            }
            if (i % labelEvery == 0) {
                canvas.drawText(Integer.toString(value), cx, axisY + 15f * dp, valueText);
            }
        }
    }
}

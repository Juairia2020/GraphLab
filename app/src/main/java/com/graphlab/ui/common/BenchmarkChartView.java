package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import java.util.ArrayList;
import java.util.List;

/** Measured time against a dashed, scaled n log n curve (MS15). Both axes are linear; the dots are the measurements. */
public class BenchmarkChartView extends View {
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Integer> ns = new ArrayList<>();
    private List<Double> ms = new ArrayList<>();
    private double scale = Double.NaN;

    public BenchmarkChartView(@NonNull Context context) {
        this(context, null);
    }

    public BenchmarkChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        axis.setStyle(Paint.Style.STROKE);
        curve.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** @param scale ms per n·log2(n) from the fit, or NaN to draw the points alone */
    public void setData(@NonNull List<Integer> ns, @NonNull List<Double> ms, double scale) {
        this.ns = new ArrayList<>(ns);
        this.ms = new ArrayList<>(ms);
        this.scale = scale;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        int ink = MaterialColors.getColor(this, R.attr.gl_text);
        int muted = MaterialColors.getColor(this, R.attr.gl_text_muted);
        int primary = MaterialColors.getColor(this, R.attr.gl_primary);
        int path = MaterialColors.getColor(this, R.attr.gl_path);
        float left = 48 * dp;
        float bottom = getHeight() - 28 * dp;
        float top = 12 * dp;
        float right = getWidth() - 12 * dp;
        axis.setColor(muted);
        axis.setStrokeWidth(1.5f * dp);
        canvas.drawLine(left, top, left, bottom, axis);
        canvas.drawLine(left, bottom, right, bottom, axis);
        if (ns.isEmpty()) {
            return;
        }
        double maxN = ns.get(ns.size() - 1);
        double maxMs = 0;
        for (double v : ms) {
            maxMs = Math.max(maxMs, v);
        }
        if (!Double.isNaN(scale)) {
            maxMs = Math.max(maxMs, scale * maxN * Math.log(maxN) / Math.log(2));
        }
        maxMs = Math.max(maxMs, 1e-6);
        text.setColor(ink);
        text.setTextSize(11 * dp);
        canvas.drawText(String.format(java.util.Locale.US, "%.0f ms", maxMs), left - 24 * dp, top + 10 * dp, text);
        canvas.drawText("0", left - 12 * dp, bottom, text);
        canvas.drawText(getContext().getString(R.string.benchmark_axis) + "  " + (long) maxN, (left + right) / 2, getHeight() - 6 * dp, text);

        if (!Double.isNaN(scale)) {
            curve.setColor(path);
            curve.setStrokeWidth(2.5f * dp);
            curve.setPathEffect(new DashPathEffect(new float[] {10 * dp, 6 * dp}, 0));
            Path p = new Path();
            boolean first = true;
            for (int step = 0; step <= 40; step++) {
                double n = Math.max(2, maxN * step / 40.0);
                double y = scale * n * Math.log(n) / Math.log(2);
                float px = (float) (left + (right - left) * n / maxN);
                float py = (float) (bottom - (bottom - top) * y / maxMs);
                if (first) {
                    p.moveTo(px, py);
                    first = false;
                } else {
                    p.lineTo(px, py);
                }
            }
            canvas.drawPath(p, curve);
        }
        dot.setColor(primary);
        for (int i = 0; i < ns.size(); i++) {
            float px = (float) (left + (right - left) * ns.get(i) / maxN);
            float py = (float) (bottom - (bottom - top) * ms.get(i) / maxMs);
            canvas.drawCircle(px, py, 5 * dp, dot);
        }
    }
}

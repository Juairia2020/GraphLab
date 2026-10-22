package com.graphlab.ui.common;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import androidx.annotation.NonNull;
import com.graphlab.engine.model.Edge;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.report.RunReport;
import com.graphlab.engine.trace.ArrayFrame;
import com.graphlab.engine.trace.Frame;
import com.graphlab.engine.trace.GraphFrame;
import com.graphlab.engine.trace.GridFrame;
import com.graphlab.engine.trace.Result;
import com.graphlab.engine.trace.State;
import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Draws a finished run as a PDF: a summary page, a few key frames, then the full step log.
 * It paints with its own fixed light colours on white paper, so the file looks the same whatever
 * the app theme is. It does not reuse {@code GraphView}, which is built for the screen.
 * Pure drawing: no Activity, no disk. The caller supplies the output stream.
 */
public final class RunPdf {
    private static final int W = 595;   // A4 at 72 dpi
    private static final int H = 842;
    private static final int M = 40;
    private static final int MAX_FRAMES = 6;
    private static final int FRAMES_PER_PAGE = 2;
    private static final int LINE = 15;
    private static final int[] STATE_COLOUR = {
            Color.rgb(120, 130, 140),   // IDLE
            Color.rgb(230, 160, 0),     // FRONTIER
            Color.rgb(30, 150, 80),     // VISITED
            Color.rgb(180, 40, 150),    // CHOSEN
            Color.rgb(30, 90, 200),     // PATH
            Color.rgb(200, 40, 40)};    // FAIL

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PdfDocument doc;
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo;
    private int y;

    private RunPdf() {
        text.setColor(Color.BLACK);
        text.setTextSize(11);
        bold.setColor(Color.BLACK);
        bold.setTextSize(18);
        bold.setFakeBoldText(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        fill.setStyle(Paint.Style.FILL);
    }

    /** Writes the report. {@code title} is the algorithm name; {@code summary} lines are shown on page one. */
    public static void write(@NonNull OutputStream out, @NonNull String title, @NonNull List<String> summary,
                             @NonNull Graph graph, @NonNull Trace trace) throws IOException {
        new RunPdf().build(out, title, summary, graph, trace);
    }

    private void build(OutputStream out, String title, List<String> summary, Graph graph, Trace trace) throws IOException {
        doc = new PdfDocument();
        try {
            newPage();
            canvas.drawText(title, M, y, bold);
            y += 26;
            for (String line : summary) {
                for (String w : RunReport.wrap(line, 90)) {
                    canvas.drawText(w, M, y, text);
                    y += LINE;
                }
            }
            Result r = trace.result();
            y += 6;
            for (String w : RunReport.wrap("Result: " + r.text(), 90)) {
                canvas.drawText(w, M, y, text);
                y += LINE;
            }
            canvas.drawText("Steps: " + trace.size(), M, y, text);
            y += LINE * 2;

            List<Integer> keys = RunReport.keyFrames(trace.size(), MAX_FRAMES);
            int onPage = 0;
            for (int k = 0; k < keys.size(); k++) {
                if (onPage == FRAMES_PER_PAGE || (k == 0 && y > H / 2)) {
                    newPage();
                    onPage = 0;
                }
                drawKeyFrame(graph, trace, keys.get(k));
                onPage++;
            }

            // full step log
            newPage();
            canvas.drawText("Step log", M, y, bold);
            y += 24;
            for (int i = 0; i < trace.size(); i++) {
                Step s = trace.step(i);
                List<String> lines = RunReport.wrap((i + 1) + ". " + s.message(), 95);
                if (y + lines.size() * LINE > H - M) {
                    newPage();
                }
                for (String l : lines) {
                    canvas.drawText(l, M, y, text);
                    y += LINE;
                }
            }
            finishPage();
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    private void newPage() {
        finishPage();
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        y = M + 14;
        Paint foot = new Paint(text);
        foot.setTextSize(9);
        foot.setColor(Color.GRAY);
        canvas.drawText("GraphLab run report - page " + pageNo, M, H - 18, foot);
    }

    private void finishPage() {
        if (page != null) {
            doc.finishPage(page);
            page = null;
        }
    }

    private void drawKeyFrame(Graph graph, Trace trace, int index) {
        Step step = trace.step(index);
        canvas.drawText("Step " + (index + 1) + " of " + trace.size(), M, y, headingPaint());
        y += 16;
        for (String l : RunReport.wrap(step.message(), 95)) {
            canvas.drawText(l, M, y, text);
            y += LINE;
        }
        y += 6;
        RectF box = new RectF(M, y, W - M, y + 280);
        stroke.setColor(Color.LTGRAY);
        stroke.setStrokeWidth(1f);
        canvas.drawRect(box, stroke);
        Frame frame = step.frame();
        if (frame instanceof GraphFrame g) {
            drawGraph(g, graph, box);
        } else if (frame instanceof ArrayFrame a) {
            drawArray(a, box);
        } else if (frame instanceof GridFrame g) {
            drawGrid(g, box);
        }
        y = (int) box.bottom + 28;
    }

    private Paint headingPaint() {
        Paint p = new Paint(bold);
        p.setTextSize(13);
        return p;
    }

    private static int colour(State s) {
        return STATE_COLOUR[s.ordinal()];
    }

    private void drawGraph(GraphFrame frame, Graph graph, RectF box) {
        if (graph.nodeCount() == 0) {
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node n : graph.nodes()) {
            minX = Math.min(minX, n.x());
            maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y());
            maxY = Math.max(maxY, n.y());
        }
        float pad = 34f;
        double sx = (box.width() - 2 * pad) / Math.max(1e-6, maxX - minX);
        double sy = (box.height() - 2 * pad) / Math.max(1e-6, maxY - minY);
        double scale = Math.min(sx, sy);
        if (maxX - minX < 1e-6 && maxY - minY < 1e-6) {
            scale = 1;
        }
        double offX = box.left + (box.width() - (maxX - minX) * scale) / 2;
        double offY = box.top + (box.height() - (maxY - minY) * scale) / 2;
        Paint label = new Paint(text);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10);
        for (Edge e : graph.edges()) {
            Node a = graph.node(e.from());
            Node b = graph.node(e.to());
            float ax = (float) (offX + (a.x() - minX) * scale);
            float ay = (float) (offY + (a.y() - minY) * scale);
            float bx = (float) (offX + (b.x() - minX) * scale);
            float by = (float) (offY + (b.y() - minY) * scale);
            State st = frame.edgeState(e.id());
            stroke.setColor(colour(st));
            stroke.setStrokeWidth(st == State.IDLE ? 1.5f : 3f);
            canvas.drawLine(ax, ay, bx, by, stroke);
            String w = frame.edgeLabel(e.id());
            if (w != null && !w.isEmpty()) {
                canvas.drawText(w, (ax + bx) / 2, (ay + by) / 2 - 3, label);
            }
        }
        for (Node n : graph.nodes()) {
            float cx = (float) (offX + (n.x() - minX) * scale);
            float cy = (float) (offY + (n.y() - minY) * scale);
            State st = frame.nodeState(n.id());
            fill.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, 14f, fill);
            stroke.setColor(colour(st));
            stroke.setStrokeWidth(st == State.IDLE ? 1.5f : 3.5f);
            canvas.drawCircle(cx, cy, 14f, stroke);
            canvas.drawText(n.label(), cx, cy + 4, label);
            String extra = frame.nodeLabel(n.id());
            if (extra != null && !extra.isEmpty()) {
                canvas.drawText(extra, cx, cy - 18, label);
            }
        }
    }

    private void drawArray(ArrayFrame frame, RectF box) {
        int n = frame.values().size();
        if (n == 0) {
            return;
        }
        int max = 1;
        for (int v : frame.values()) {
            max = Math.max(max, v);
        }
        float slot = (box.width() - 20) / n;
        Paint label = new Paint(text);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(9);
        for (int i = 0; i < n; i++) {
            float h = (box.height() - 40) * frame.values().get(i) / max;
            float left = box.left + 10 + i * slot + 2;
            fill.setColor(colour(frame.states().get(i)));
            canvas.drawRect(left, box.bottom - 20 - h, left + slot - 4, box.bottom - 20, fill);
            canvas.drawText(String.valueOf(frame.values().get(i)), left + (slot - 4) / 2, box.bottom - 8, label);
        }
    }

    private void drawGrid(GridFrame frame, RectF box) {
        int rows = frame.rows() + 1;
        int cols = frame.cols() + 1;
        float cw = Math.min(60f, (box.width() - 10) / cols);
        float ch = Math.min(22f, (box.height() - 10) / rows);
        Paint label = new Paint(text);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Math.min(10f, ch * 0.5f));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = box.left + 5 + c * cw;
                float yy = box.top + 5 + r * ch;
                String cell;
                State st = State.IDLE;
                if (r == 0 && c == 0) {
                    cell = frame.corner();
                } else if (r == 0) {
                    cell = frame.colHeaders().get(c - 1);
                } else if (c == 0) {
                    cell = frame.rowHeaders().get(r - 1);
                } else {
                    cell = frame.cell(r - 1, c - 1);
                    st = frame.state(r - 1, c - 1);
                }
                if (st != State.IDLE) {
                    fill.setColor(colour(st));
                    fill.setAlpha(70);
                    canvas.drawRect(x, yy, x + cw, yy + ch, fill);
                    fill.setAlpha(255);
                }
                stroke.setColor(Color.LTGRAY);
                stroke.setStrokeWidth(0.7f);
                canvas.drawRect(x, yy, x + cw, yy + ch, stroke);
                if (cell != null) {
                    canvas.drawText(cell, x + cw / 2, yy + ch * 0.68f, label);
                }
            }
        }
    }
}

package com.graphlab.data.imports;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.graphlab.engine.imageimport.Circle;
import com.graphlab.engine.imageimport.Segment;
import java.util.ArrayList;
import java.util.List;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Finds the circles (nodes) and straight lines (edges) in a photo of a graph drawing (MS16, G11), with OpenCV.
 *
 * <p>This is a line-for-line port of {@code detect()} in {@code tools/import-testset/make_set.py}, the prototype that was
 * run on the 20 test photos. Keep the two in step: same constants, same order of steps.
 *
 * <ol>
 *   <li>Grey, median blur, then an adaptive threshold: ink is dark marks on the local paper colour, so uneven light is fine.</li>
 *   <li>HoughCircles finds node circles. Each is scored by how much of its outline is ink.</li>
 *   <li>The circles are painted out of the ink picture, so their outlines and names do not look like lines.</li>
 *   <li>Canny and HoughLinesP find line pieces; each piece is pushed outwards along its own direction while ink continues.</li>
 * </ol>
 * The result is in the pixels of the bitmap that was given, which the caller has already scaled to about 1000 pixels.
 */
public final class ShapeDetector {
    // ---- constants, equal to make_set.py ----
    static final int BLUR = 5;
    static final double CIRCLE_DP = 1.2;
    static final double CIRCLE_MIN_DIST = 70;
    static final double CIRCLE_P1 = 120;
    static final double CIRCLE_P2 = 34;
    static final int CIRCLE_MIN_R = 16;
    static final int CIRCLE_MAX_R = 48;
    static final int MASK_GROW = 5;
    static final int HOUGH_THRESHOLD = 45;
    static final double HOUGH_MIN_LEN = 45;
    static final double HOUGH_MAX_GAP = 14;
    static final int BORDER = 14;
    static final int RING_SAMPLES = 72;
    static final int LINE_SAMPLES = 40;
    static final int EXTEND_MAX = 160;
    static final int EXTEND_GAP = 8;

    /** What was found. */
    public static final class Result {
        public final List<Circle> circles = new ArrayList<>();
        public final List<Segment> segments = new ArrayList<>();
    }

    private ShapeDetector() {
    }

    /** Loads OpenCV's native code. False when it could not be loaded; the picture import then cannot run. */
    public static boolean init() {
        return OpenCVLoader.initLocal();
    }

    @NonNull
    public static Result detect(@NonNull Bitmap bitmap) {
        Result out = new Result();
        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat blur = new Mat();
        Mat ink = new Mat();
        Mat circlesMat = new Mat();
        Mat lines = new Mat();
        Mat edges = new Mat();
        Mat linesMat = new Mat();
        Mat open = Mat.ones(2, 2, org.opencv.core.CvType.CV_8U);
        try {
            Utils.bitmapToMat(bitmap, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.medianBlur(gray, blur, BLUR);
            Imgproc.adaptiveThreshold(blur, ink, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 41, 12);
            Imgproc.morphologyEx(ink, ink, Imgproc.MORPH_OPEN, open);
            final int w = ink.cols();
            final int h = ink.rows();
            byte[] inkBytes = new byte[w * h];
            ink.get(0, 0, inkBytes);

            Imgproc.HoughCircles(blur, circlesMat, Imgproc.HOUGH_GRADIENT, CIRCLE_DP, CIRCLE_MIN_DIST, CIRCLE_P1, CIRCLE_P2,
                    CIRCLE_MIN_R, CIRCLE_MAX_R);
            ink.copyTo(lines);
            for (int i = 0; i < circlesMat.cols(); i++) {
                double[] c = circlesMat.get(0, i);
                double cov = ringCoverage(inkBytes, w, h, c[0], c[1], c[2]);
                if (cov >= 0.45) {
                    out.circles.add(new Circle(c[0], c[1], c[2], Math.min(1.0, cov / 0.85)));
                }
            }
            for (Circle c : out.circles) {
                Imgproc.circle(lines, new Point(Math.round(c.x()), Math.round(c.y())), (int) (c.r() + MASK_GROW), new Scalar(0), -1);
            }
            byte[] lineBytes = new byte[w * h];
            lines.get(0, 0, lineBytes);
            Imgproc.Canny(lines, edges, 50, 150);
            Imgproc.HoughLinesP(edges, linesMat, 1, Math.PI / 180, HOUGH_THRESHOLD, HOUGH_MIN_LEN, HOUGH_MAX_GAP);
            for (int i = 0; i < linesMat.rows(); i++) {
                double[] l = linesMat.get(i, 0);
                double x1 = l[0];
                double y1 = l[1];
                double x2 = l[2];
                double y2 = l[3];
                double cov = lineCoverage(lineBytes, w, h, x1, y1, x2, y2);
                boolean onBorder = Math.min(x1, x2) < BORDER || Math.max(x1, x2) > w - BORDER
                        || Math.min(y1, y2) < BORDER || Math.max(y1, y2) > h - BORDER;
                if (cov >= 0.5 && !onBorder) {
                    double[] e = extend(lineBytes, w, h, x1, y1, x2, y2);
                    out.segments.add(new Segment(e[0], e[1], e[2], e[3], Math.min(1.0, cov / 0.85)));
                }
            }
        } finally {
            rgba.release();
            gray.release();
            blur.release();
            ink.release();
            circlesMat.release();
            lines.release();
            edges.release();
            linesMat.release();
            open.release();
        }
        return out;
    }

    private static boolean on(byte[] ink, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && ink[y * w + x] != 0;
    }

    /** Share of the outline (sampled at 72 points, plus or minus 3 pixels) that is ink. */
    static double ringCoverage(byte[] ink, int w, int h, double x, double y, double r) {
        int hit = 0;
        for (int k = 0; k < RING_SAMPLES; k++) {
            double a = 2 * Math.PI * k / RING_SAMPLES;
            boolean ok = false;
            for (int dr = -3; dr <= 3 && !ok; dr++) {
                ok = on(ink, w, h, (int) Math.round(x + (r + dr) * Math.cos(a)), (int) Math.round(y + (r + dr) * Math.sin(a)));
            }
            if (ok) {
                hit++;
            }
        }
        return hit / (double) RING_SAMPLES;
    }

    /** Share of a line (sampled at 40 points, a 5 by 5 window each) that is ink. */
    static double lineCoverage(byte[] ink, int w, int h, double x1, double y1, double x2, double y2) {
        int hit = 0;
        for (int k = 0; k < LINE_SAMPLES; k++) {
            double t = (k + 0.5) / LINE_SAMPLES;
            double px = x1 + (x2 - x1) * t;
            double py = y1 + (y2 - y1) * t;
            boolean ok = false;
            for (int dx = -2; dx <= 2 && !ok; dx++) {
                for (int dy = -2; dy <= 2 && !ok; dy++) {
                    ok = on(ink, w, h, (int) Math.round(px + dx), (int) Math.round(py + dy));
                }
            }
            if (ok) {
                hit++;
            }
        }
        return hit / (double) LINE_SAMPLES;
    }

    /** Hough often returns only the middle of a line. Walk each end outwards while ink continues along the same direction. */
    static double[] extend(byte[] ink, int w, int h, double x1, double y1, double x2, double y2) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length == 0) {
            return new double[] {x1, y1, x2, y2};
        }
        double ux = (x2 - x1) / length;
        double uy = (y2 - y1) / length;
        double[] end2 = walk(ink, w, h, x2, y2, ux, uy);
        double[] end1 = walk(ink, w, h, x1, y1, -ux, -uy);
        return new double[] {end1[0], end1[1], end2[0], end2[1]};
    }

    private static double[] walk(byte[] ink, int w, int h, double px, double py, double dx, double dy) {
        int last = 0;
        int gap = 0;
        for (int step = 1; step <= EXTEND_MAX; step++) {
            double qx = px + dx * step;
            double qy = py + dy * step;
            boolean ok = false;
            for (int off = -2; off <= 2 && !ok; off++) {
                ok = on(ink, w, h, (int) Math.round(qx - dy * off), (int) Math.round(qy + dx * off));
            }
            if (ok) {
                last = step;
                gap = 0;
            } else {
                gap++;
                if (gap > EXTEND_GAP) {
                    break;
                }
            }
        }
        return new double[] {px + dx * last, py + dy * last};
    }
}

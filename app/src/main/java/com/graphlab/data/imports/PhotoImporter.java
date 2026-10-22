package com.graphlab.data.imports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import com.graphlab.R;
import com.graphlab.engine.imageimport.Circle;
import com.graphlab.engine.imageimport.DetectedGraph;
import com.graphlab.engine.imageimport.GeometryAssembler;
import com.graphlab.engine.imageimport.GraphTextImporter;
import com.graphlab.engine.imageimport.OcrToken;
import com.graphlab.engine.imageimport.Segment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole photo-to-graph pipeline (MS16), on a background thread.
 *
 * <p>Drawing: scale the photo to about 1000 pixels, find circles and lines with OpenCV ({@link ShapeDetector}), ask the
 * engine where the names and numbers should be ({@link GeometryAssembler#probes}), read just those small squares with
 * ML Kit, then let the engine assemble a graph with a confidence on every item. Text (a matrix or an edge list): read the
 * whole page with ML Kit and let the engine decide which of the two it is.
 */
public final class PhotoImporter {
    private static final int DRAWING_SIDE = 1000;
    private static final int TEXT_SIDE = 2000;
    private static final int WEIGHT_HALF = 48;       // equal to PROBE_HALF in make_set.py
    private static final int WEIGHT_SCALE = 3;
    private static final int LABEL_SCALE = 4;
    private static final double LABEL_HALF = 0.62;   // of the circle's radius

    /** Tells the screen what the pipeline is doing. Called on the worker thread. */
    public interface Progress {
        void onStep(@NonNull String message);
    }

    /** The graph as read, and a small copy of the photo for the review screen. */
    public static final class Result {
        public final DetectedGraph graph;
        public final Bitmap thumbnail;

        Result(DetectedGraph graph, Bitmap thumbnail) {
            this.graph = graph;
            this.thumbnail = thumbnail;
        }
    }

    /** A problem the person can understand (not a photo of a graph, text not found); the message is ready to show. */
    public static final class ImportException extends Exception {
        ImportException(String message) {
            super(message);
        }
    }

    private PhotoImporter() {
    }

    @NonNull
    public static Result run(@NonNull Context context, @Nullable Uri uri, @Nullable File file, boolean textMode,
                             boolean directed, @NonNull Progress progress) throws Exception {
        Bitmap bitmap = decode(context, uri, file, textMode ? TEXT_SIDE : DRAWING_SIDE);
        if (bitmap == null) {
            throw new ImportException(context.getString(R.string.import_err_decode));
        }
        Bitmap thumb = scaleTo(bitmap, 320);
        try (MlKitReader reader = new MlKitReader()) {
            if (textMode) {
                progress.onStep(context.getString(R.string.import_step_text));
                List<OcrToken> tokens = reader.readPage(bitmap);
                DetectedGraph g = GraphTextImporter.parse(tokens);
                if (g.nodes().isEmpty()) {
                    throw new ImportException(context.getString(R.string.import_err_no_text));
                }
                return new Result(g, thumb);
            }
            if (!ShapeDetector.init()) {
                throw new ImportException(context.getString(R.string.import_err_opencv));
            }
            progress.onStep(context.getString(R.string.import_step_shapes));
            ShapeDetector.Result shapes = ShapeDetector.detect(bitmap);
            if (shapes.circles.isEmpty()) {
                throw new ImportException(context.getString(R.string.import_err_no_circles));
            }
            GeometryAssembler.Probes probes = GeometryAssembler.probes(shapes.circles, shapes.segments);
            progress.onStep(context.getString(R.string.import_step_names));
            List<OcrToken> tokens = new ArrayList<>();
            for (Circle c : probes.circles()) {
                OcrToken t = readLabel(reader, bitmap, c);
                if (t != null) {
                    tokens.add(t);
                }
            }
            progress.onStep(context.getString(R.string.import_step_numbers));
            for (double[] spot : probes.weightSpots()) {
                OcrToken t = readWeight(reader, bitmap, spot[0], spot[1], shapes);
                if (t != null) {
                    tokens.add(t);
                }
            }
            progress.onStep(context.getString(R.string.import_step_assemble));
            DetectedGraph g = GeometryAssembler.assemble(shapes.circles, shapes.segments, tokens,
                    new GeometryAssembler.Options(directed, true));
            return new Result(g, thumb);
        } finally {
            if (bitmap != thumb) {
                bitmap.recycle();
            }
        }
    }

    /** The name inside one circle: a square a little smaller than the circle, with the outline painted out. */
    @Nullable
    private static OcrToken readLabel(MlKitReader reader, Bitmap page, Circle c) throws Exception {
        int half = Math.max(8, (int) Math.round(c.r() * LABEL_HALF));
        int left = (int) Math.round(c.x()) - half;
        int top = (int) Math.round(c.y()) - half;
        Bitmap crop = cut(page, left, top, 2 * half);
        if (crop == null) {
            return null;
        }
        Canvas canvas = new Canvas(crop);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.WHITE);
        ring.setStrokeWidth(9f);
        canvas.drawCircle((float) (c.x() - left), (float) (c.y() - top), (float) c.r(), ring);
        OcrToken t = reader.readCrop(crop, left, top, LABEL_SCALE);
        crop.recycle();
        if (t == null) {
            return null;
        }
        String clean = t.text().replaceAll("[^\\p{L}\\p{N}]", "");
        return clean.isEmpty() || clean.length() > 2 ? null
                : new OcrToken(clean, t.x(), t.y(), t.width(), t.height(), t.confidence());
    }

    /** The number beside one line: a square around the line's middle, with every detected line and circle painted out. */
    @Nullable
    private static OcrToken readWeight(MlKitReader reader, Bitmap page, double mx, double my, ShapeDetector.Result shapes)
            throws Exception {
        int left = (int) Math.round(mx) - WEIGHT_HALF;
        int top = (int) Math.round(my) - WEIGHT_HALF;
        Bitmap crop = cut(page, left, top, 2 * WEIGHT_HALF);
        if (crop == null) {
            return null;
        }
        Canvas canvas = new Canvas(crop);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        white.setStrokeWidth(7f);
        for (Segment s : shapes.segments) {
            canvas.drawLine((float) (s.x1() - left), (float) (s.y1() - top), (float) (s.x2() - left), (float) (s.y2() - top), white);
        }
        white.setStyle(Paint.Style.FILL);
        for (Circle c : shapes.circles) {
            canvas.drawCircle((float) (c.x() - left), (float) (c.y() - top), (float) (c.r() + 5), white);
        }
        OcrToken t = reader.readCrop(crop, left, top, WEIGHT_SCALE);
        crop.recycle();
        return t;
    }

    /** A square copy of part of the page. Parts outside the page stay white. Null when the square misses the page. */
    @Nullable
    private static Bitmap cut(Bitmap page, int left, int top, int side) {
        if (left >= page.getWidth() || top >= page.getHeight() || left + side <= 0 || top + side <= 0) {
            return null;
        }
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(page, new Rect(left, top, left + side, top + side), new Rect(0, 0, side, side), null);
        return out;
    }

    // ---- decoding ----

    @Nullable
    static Bitmap decode(Context context, @Nullable Uri uri, @Nullable File file, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri, file)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap raw;
        try (InputStream in = open(context, uri, file)) {
            raw = BitmapFactory.decodeStream(in, null, opts);
        }
        if (raw == null) {
            return null;
        }
        int degrees = 0;
        try (InputStream in = open(context, uri, file)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            degrees = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (IOException ignored) {
            // no orientation tag: use the pixels as they are
        }
        Matrix m = new Matrix();
        if (degrees != 0) {
            m.postRotate(degrees);
        }
        float scale = (float) maxSide / Math.max(raw.getWidth(), raw.getHeight());
        if (scale < 1f) {
            m.postScale(scale, scale);
        }
        Bitmap out = m.isIdentity() ? raw : Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
        if (out != raw) {
            raw.recycle();
        }
        return out;
    }

    private static InputStream open(Context context, @Nullable Uri uri, @Nullable File file) throws IOException {
        if (file != null) {
            return new FileInputStream(file);
        }
        InputStream in = uri == null ? null : context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Cannot open the photo");
        }
        return in;
    }

    private static Bitmap scaleTo(Bitmap src, int side) {
        float s = (float) side / Math.max(src.getWidth(), src.getHeight());
        if (s >= 1f) {
            return src;
        }
        return Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * s), Math.round(src.getHeight() * s), true);
    }
}

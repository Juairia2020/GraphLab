package com.graphlab.data.imports;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.graphlab.engine.imageimport.OcrToken;
import java.util.ArrayList;
import java.util.List;

/**
 * ML Kit's on-device text recogniser (MS16, G10), wrapped so the rest of the import code only sees {@link OcrToken}s.
 * Every call blocks, so it must run off the main thread. Nothing is sent anywhere: the Latin model is bundled in the app.
 */
final class MlKitReader implements AutoCloseable {
    private static final int PAD = 20;     // white margin around a small crop, so the recogniser sees it as a page
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    /** Every word on the page, with its box in the page's pixels. */
    @NonNull
    List<OcrToken> readPage(@NonNull Bitmap page) throws Exception {
        Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(page, 0)));
        List<OcrToken> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box == null || element.getText().trim().isEmpty()) {
                        continue;
                    }
                    out.add(new OcrToken(element.getText(), box.left, box.top, box.width(), box.height(), confidence(element)));
                }
            }
        }
        return out;
    }

    /**
     * Reads one small square of the page: a name inside a circle or a number beside a line. The square is enlarged and put
     * on a white margin first. The returned token is in page pixels, or null when nothing was read.
     *
     * @param left the square's top-left corner in the page; {@code crop} is its pixels (already cleaned by the caller)
     */
    @Nullable
    OcrToken readCrop(@NonNull Bitmap crop, double left, double top, int scale) throws Exception {
        int w = crop.getWidth() * scale + 2 * PAD;
        int h = crop.getHeight() * scale + 2 * PAD;
        Bitmap big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(big);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(crop, null, new Rect(PAD, PAD, w - PAD, h - PAD), null);
        Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(big, 0)));
        big.recycle();
        StringBuilder joined = new StringBuilder();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double conf = 1.0;
        boolean any = false;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    String t = element.getText().replaceAll("\\s+", "");
                    if (box == null || t.isEmpty()) {
                        continue;
                    }
                    any = true;
                    joined.append(t);
                    minX = Math.min(minX, box.left);
                    minY = Math.min(minY, box.top);
                    maxX = Math.max(maxX, box.right);
                    maxY = Math.max(maxY, box.bottom);
                    conf = Math.min(conf, confidence(element));
                }
            }
        }
        if (!any) {
            return null;
        }
        double x = left + (minX - PAD) / scale;
        double y = top + (minY - PAD) / scale;
        return new OcrToken(joined.toString(), x, y, (maxX - minX) / scale, (maxY - minY) / scale, conf);
    }

    /** ML Kit gives a confidence per element; if a version does not, the word is treated as moderately sure. */
    private static double confidence(Text.Element element) {
        float c = element.getConfidence();
        return c > 0f && c <= 1f ? c : 0.8;
    }

    @Override
    public void close() {
        recognizer.close();
    }
}

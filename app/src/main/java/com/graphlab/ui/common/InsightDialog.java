package com.graphlab.ui.common;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.graphlab.R;
import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.insight.Insight;
import com.graphlab.engine.trace.Algorithm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shows an algorithm's Insight page (assets/insight/{id}.json) in a dialog, for screens outside Run mode. */
public final class InsightDialog {
    private InsightDialog() {
    }

    public static void show(@NonNull Context context, @NonNull String algorithmId) {
        Algorithm algorithm = Algorithms.byId(algorithmId);
        Insight insight = null;
        try (InputStream in = context.getAssets().open("insight/" + algorithmId + ".json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            insight = Insight.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | com.graphlab.engine.io.GraphFormatException | RuntimeException e) {
            insight = null;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(algorithm == null ? algorithmId : algorithm.name())
                .setPositiveButton(R.string.dynamic_ok, null);
        if (insight == null) {
            dialog.setMessage(R.string.breakit_insight_missing);
        } else {
            dialog.setMessage(context.getString(R.string.breakit_insight_page, insight.idea(), insight.time(), insight.space(),
                    bullets(insight.useWhen()), bullets(insight.avoidWhen())));
        }
        dialog.show();
    }

    private static String bullets(java.util.List<String> items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) {
            out.append(out.length() == 0 ? "• " : "\n• ").append(item);
        }
        return out.toString();
    }
}

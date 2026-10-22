package com.graphlab.ui.common;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import com.graphlab.R;

/**
 * Adds the system-bar insets to a view's padding without throwing away the padding written in its layout file.
 * Before MS19 every screen called {@code setPadding(bars...)}, which replaced the layout's own padding, so text
 * touched the screen edge.
 */
public final class EdgeInsets {
    private EdgeInsets() {
    }

    public static void pad(@NonNull View view, @NonNull Insets bars) {
        int[] initial = (int[]) view.getTag(R.id.gl_initial_padding);
        if (initial == null) {
            initial = new int[] {view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()};
            view.setTag(R.id.gl_initial_padding, initial);
        }
        view.setPadding(initial[0] + bars.left, initial[1] + bars.top, initial[2] + bars.right, initial[3] + bars.bottom);
    }
}

package com.graphlab.ui.common;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.graphlab.R;
import com.graphlab.databinding.ContentPlaceholderBinding;

/**
 * Stand-in for Arena, Lab and Toolkit until their real screens arrive. The tap counter is a
 * test aid: it proves a tab keeps its state when you switch away and back, and through
 * rotation. Delete it when the real screens replace these placeholders.
 */
public abstract class PlaceholderTabActivity extends BaseActivity {
    private static final String KEY_TAPS = "taps";

    @StringRes
    protected abstract int getTitleRes();

    @StringRes
    protected abstract int getSubtitleRes();

    private int taps;
    private ContentPlaceholderBinding binding;

    @Override
    protected int getContentLayout() {
        return R.layout.content_placeholder;
    }

    @Override
    protected void onContentInflated(View content, @Nullable Bundle savedInstanceState) {
        binding = ContentPlaceholderBinding.bind(content);
        binding.title.setText(getTitleRes());
        binding.subtitle.setText(getSubtitleRes());
        taps = savedInstanceState == null ? 0 : savedInstanceState.getInt(KEY_TAPS);
        renderTaps();
        binding.tapCounter.setOnClickListener(v -> {
            taps++;
            renderTaps();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAPS, taps);
    }

    private void renderTaps() {
        binding.tapCounter.setText(getString(R.string.tap_counter, taps));
    }
}

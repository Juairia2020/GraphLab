package com.graphlab.ui.onboarding;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.graphlab.R;
import com.graphlab.data.prefs.AppPrefs;
import com.graphlab.databinding.ActivityOnboardingBinding;

/**
 * A three-screen introduction shown the first time the app opens (and again from Me). One
 * Activity shows one page at a time with a Next button, Skip and three dots, so it needs no
 * extra library. The current page survives rotation. Finishing or skipping marks the intro as
 * seen; Back counts as skipping.
 */
public class OnboardingActivity extends AppCompatActivity {
    private static final String STATE_PAGE = "page";

    @StringRes
    private static final int[] TITLES = {R.string.onb_title_1, R.string.onb_title_2, R.string.onb_title_3};
    @StringRes
    private static final int[] BODIES = {R.string.onb_body_1, R.string.onb_body_2, R.string.onb_body_3};

    private ActivityOnboardingBinding binding;
    private int page;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, bars);
            return insets;
        });

        page = savedInstanceState == null ? 0 : Math.max(0, Math.min(TITLES.length - 1, savedInstanceState.getInt(STATE_PAGE)));
        binding.onbNext.setOnClickListener(v -> {
            if (page < TITLES.length - 1) {
                page++;
                render();
            } else {
                finishIntro();
            }
        });
        binding.onbSkip.setOnClickListener(v -> finishIntro());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishIntro();
            }
        });
        render();
    }

    private void render() {
        binding.onbTitle.setText(TITLES[page]);
        binding.onbBody.setText(BODIES[page]);
        binding.onbNext.setText(page == TITLES.length - 1 ? R.string.onb_done : R.string.onb_next);
        binding.onbSkip.setVisibility(page == TITLES.length - 1 ? View.INVISIBLE : View.VISIBLE);
        binding.onbDot1.setSelected(page == 0);
        binding.onbDot2.setSelected(page == 1);
        binding.onbDot3.setSelected(page == 2);
        binding.onbPageLabel.setText(getString(R.string.onb_page_label, page + 1, TITLES.length));
    }

    private void finishIntro() {
        AppPrefs.setOnboardingDone(this, true);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, page);
    }
}

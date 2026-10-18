package com.graphlab.ui.common;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.content.res.Configuration;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.graphlab.R;
import com.graphlab.data.prefs.AppPrefs;
import com.graphlab.databinding.ActivityBaseBinding;
import com.graphlab.ui.onboarding.OnboardingActivity;

/**
 * Shared shell for the four tabs: one layout (activity_base) holds the content area and the
 * single bottom bar, so the bar exists once, not four times. Subclasses only say which tab
 * they are and which layout goes in the content area.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected abstract Tab getTab();

    @LayoutRes
    protected abstract int getContentLayout();

    /** Called once after the content layout is inflated. */
    protected void onContentInflated(View content, @Nullable Bundle savedInstanceState) {
    }

    /**
     * Landscape only: the bottom bar was shown or hidden with the menu button. The Lab uses this to show its
     * mode bar together with the bottom bar.
     */
    protected void onLandscapeBarsToggled(boolean shown) {
    }

    private ActivityBaseBinding base;

    /** Landscape leaves little height, so the bottom bar is folded into a menu button instead. */
    protected final boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        base = ActivityBaseBinding.inflate(getLayoutInflater());
        setContentView(base.getRoot());

        applySystemBarInsets();

        View content = getLayoutInflater().inflate(getContentLayout(), base.content, false);
        base.content.addView(content);

        setUpBottomNav();
        setUpLandscapeMenu();
        setUpBackNavigation();
        removeTransitionAnimations();

        onContentInflated(content, savedInstanceState);
        maybeShowIntro(savedInstanceState);
    }

    /**
     * The first time the app starts (Arena is the launcher tab) the three-screen intro opens on top.
     * Not on rotation or when returning to the tab (savedInstanceState is not null then), and only once
     * until the person finishes or skips it. Me can replay it.
     */
    private void maybeShowIntro(@Nullable Bundle savedInstanceState) {
        if (getTab() == Tab.ARENA && savedInstanceState == null && !AppPrefs.isOnboardingDone(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
    }

    private void applySystemBarInsets() {
        // Top/left/right are handled here. The bottom inset is handled by the bar itself
        // (paddingBottomSystemWindowInsets in activity_base.xml).
        ViewCompat.setOnApplyWindowInsetsListener(base.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            com.graphlab.ui.common.EdgeInsets.pad(view, androidx.core.graphics.Insets.of(bars.left, bars.top, bars.right, 0));
            return insets;
        });
    }

    private void setUpBottomNav() {
        // Select our own tab before the listener exists so nothing fires.
        base.bottomNav.setSelectedItemId(getTab().menuId);
        base.bottomNav.setOnItemSelectedListener(item -> {
            Tab target = Tab.fromMenuId(item.getItemId());
            if (target != null && target != getTab()) {
                navigateTo(target);
            }
            // false = do not move the highlight here; the target Activity highlights itself.
            return false;
        });
    }

    /** In landscape the bottom bar starts hidden; the button on the slim rail shows and hides it. */
    private void setUpLandscapeMenu() {
        if (!isLandscape()) {
            return;
        }
        base.navRail.setVisibility(View.VISIBLE);
        setBarsShown(false);
        base.navMenu.setOnClickListener(v -> setBarsShown(base.bottomNav.getVisibility() != View.VISIBLE));
    }

    private void setBarsShown(boolean shown) {
        base.bottomNav.setVisibility(shown ? View.VISIBLE : View.GONE);
        base.navDivider.setVisibility(shown ? View.VISIBLE : View.GONE);
        base.navMenu.setContentDescription(getString(shown ? R.string.bars_hide : R.string.bars_show));
        onLandscapeBarsToggled(shown);
    }

    @SuppressWarnings("deprecation")   // overridePendingTransition is the right call below API 34
    private void navigateTo(Tab target) {
        // Explicit intent. REORDER_TO_FRONT brings the existing instance of that tab forward
        // instead of creating a new one, so each tab keeps its state.
        Intent intent = new Intent(this, target.activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0);
        }
    }

    private void removeTransitionAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }
    }

    private void setUpBackNavigation() {
        // Bottom-nav convention: Back leaves the app from any tab.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }
}

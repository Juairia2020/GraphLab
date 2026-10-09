package com.graphlab.ui.common;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.graphlab.databinding.ActivityBaseBinding;

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

    private ActivityBaseBinding base;

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
        setUpBackNavigation();
        removeTransitionAnimations();

        onContentInflated(content, savedInstanceState);
    }

    private void applySystemBarInsets() {
        // Top/left/right are handled here. The bottom inset is handled by the bar itself
        // (paddingBottomSystemWindowInsets in activity_base.xml).
        ViewCompat.setOnApplyWindowInsetsListener(base.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, 0);
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

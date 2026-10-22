package com.graphlab.ui.common;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;

/**
 * Folds rarely used buttons into one "More" button. The folded buttons stay in the layout (hidden), so their click
 * listeners and enabled state keep working unchanged: choosing a menu item clicks the hidden button, and an item is
 * greyed out while its button is disabled.
 */
public final class OverflowMenu {
    private OverflowMenu() {
    }

    public static void attach(@NonNull View anchor, @NonNull View... actions) {
        for (View action : actions) {
            action.setVisibility(View.GONE);
        }
        anchor.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            for (int i = 0; i < actions.length; i++) {
                View action = actions[i];
                CharSequence title = action instanceof TextView && ((TextView) action).getText().length() > 0
                        ? ((TextView) action).getText() : action.getContentDescription();
                MenuItem item = menu.getMenu().add(0, i, i, title);
                item.setEnabled(action.isEnabled());
            }
            menu.setOnMenuItemClickListener(item -> {
                actions[item.getItemId()].performClick();
                return true;
            });
            menu.show();
        });
    }
}

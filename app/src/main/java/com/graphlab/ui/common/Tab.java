package com.graphlab.ui.common;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.graphlab.R;
import com.graphlab.ui.arena.ArenaActivity;
import com.graphlab.ui.lab.LabActivity;
import com.graphlab.ui.me.MeActivity;
import com.graphlab.ui.toolkit.MapActivity;

public enum Tab {
    ARENA(R.id.nav_arena, R.string.tab_arena, ArenaActivity.class),
    LAB(R.id.nav_lab, R.string.tab_lab, LabActivity.class),
    TOOLKIT(R.id.nav_toolkit, R.string.tab_toolkit, MapActivity.class),
    ME(R.id.nav_me, R.string.tab_me, MeActivity.class);

    @IdRes
    public final int menuId;
    @StringRes
    public final int titleRes;
    public final Class<? extends BaseActivity> activityClass;

    Tab(@IdRes int menuId, @StringRes int titleRes, Class<? extends BaseActivity> activityClass) {
        this.menuId = menuId;
        this.titleRes = titleRes;
        this.activityClass = activityClass;
    }

    @Nullable
    public static Tab fromMenuId(@IdRes int id) {
        for (Tab tab : values()) {
            if (tab.menuId == id) {
                return tab;
            }
        }
        return null;
    }
}

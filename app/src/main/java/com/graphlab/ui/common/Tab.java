package com.graphlab.ui.common;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import com.graphlab.R;
import com.graphlab.ui.arena.ArenaActivity;
import com.graphlab.ui.lab.LabActivity;
import com.graphlab.ui.me.MeActivity;
import com.graphlab.ui.toolkit.ToolkitActivity;

public enum Tab {
    ARENA(R.id.nav_arena, ArenaActivity.class),
    LAB(R.id.nav_lab, LabActivity.class),
    TOOLKIT(R.id.nav_toolkit, ToolkitActivity.class),
    ME(R.id.nav_me, MeActivity.class);

    @IdRes
    public final int menuId;
    public final Class<? extends BaseActivity> activityClass;

    Tab(@IdRes int menuId, Class<? extends BaseActivity> activityClass) {
        this.menuId = menuId;
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

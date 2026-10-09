package com.graphlab.ui.arena;

import com.graphlab.R;
import com.graphlab.ui.common.PlaceholderTabActivity;
import com.graphlab.ui.common.Tab;

public class ArenaActivity extends PlaceholderTabActivity {
    @Override
    protected Tab getTab() {
        return Tab.ARENA;
    }

    @Override
    protected int getTitleRes() {
        return R.string.tab_arena;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.arena_placeholder;
    }
}

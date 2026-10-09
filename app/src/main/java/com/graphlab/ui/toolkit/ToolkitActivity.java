package com.graphlab.ui.toolkit;

import com.graphlab.R;
import com.graphlab.ui.common.PlaceholderTabActivity;
import com.graphlab.ui.common.Tab;

public class ToolkitActivity extends PlaceholderTabActivity {
    @Override
    protected Tab getTab() {
        return Tab.TOOLKIT;
    }

    @Override
    protected int getTitleRes() {
        return R.string.tab_toolkit;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.toolkit_placeholder;
    }
}

package com.graphlab.ui.common;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.color.MaterialColors;
import com.graphlab.R;
import com.graphlab.engine.trace.State;

/**
 * Maps the engine's {@link State} values to the theme colours (gl_idle, gl_frontier,
 * gl_visited, gl_chosen, gl_path, gl_fail) and to their names. The engine never knows about
 * colours; this is the one place where a state becomes a colour, so the canvas, the node
 * table and the chips can never disagree.
 */
public final class StateColors {
    private StateColors() {
    }

    /** One colour per state, indexed by {@code State.ordinal()}. Read again after a theme change. */
    @NonNull
    public static int[] resolve(@NonNull Context context) {
        int[] colors = new int[State.values().length];
        colors[State.IDLE.ordinal()] = MaterialColors.getColor(context, R.attr.gl_idle, Color.GRAY);
        colors[State.FRONTIER.ordinal()] = MaterialColors.getColor(context, R.attr.gl_frontier, Color.YELLOW);
        colors[State.VISITED.ordinal()] = MaterialColors.getColor(context, R.attr.gl_visited, Color.GREEN);
        colors[State.CHOSEN.ordinal()] = MaterialColors.getColor(context, R.attr.gl_chosen, Color.MAGENTA);
        colors[State.PATH.ordinal()] = MaterialColors.getColor(context, R.attr.gl_path, Color.BLUE);
        colors[State.FAIL.ordinal()] = MaterialColors.getColor(context, R.attr.gl_fail, Color.RED);
        return colors;
    }

    /** The label for a state, for the node table and screen readers. */
    @StringRes
    public static int name(@NonNull State state) {
        switch (state) {
            case FRONTIER:
                return R.string.state_frontier;
            case VISITED:
                return R.string.state_visited;
            case CHOSEN:
                return R.string.state_chosen;
            case PATH:
                return R.string.state_path;
            case FAIL:
                return R.string.state_fail;
            default:
                return R.string.state_idle;
        }
    }
}

package com.graphlab.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Hears the app's own {@link #ACTION_RUN_FINISHED} broadcast, sent by the experiment service when a result has been
 * saved. The sender calls {@code setPackage}, so the broadcast never leaves this app, and receivers are registered
 * in code as NOT_EXPORTED. The Lab registers one while it is on screen and refreshes when it fires.
 */
public final class RunFinishedReceiver extends BroadcastReceiver {
    public static final String ACTION_RUN_FINISHED = "com.graphlab.action.RUN_FINISHED";
    public static final String EXTRA_RUN_ID = "run_id";
    public static final String EXTRA_GRAPH_NAME = "graph_name";
    public static final String EXTRA_SUMMARY = "summary";
    public static final String EXTRA_PARTIAL = "partial";

    /** Called on the main thread. */
    public interface Listener {
        void onRunFinished(long runId, @NonNull String graphName, @NonNull String summary, boolean partial);
    }

    private final Listener listener;

    public RunFinishedReceiver(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** Builds the broadcast the service sends. It is limited to this app with {@code setPackage}. */
    @NonNull
    public static Intent buildBroadcast(@NonNull Context context, long runId, @NonNull String graphName,
                                        @NonNull String summary, boolean partial) {
        return new Intent(ACTION_RUN_FINISHED)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_RUN_ID, runId)
                .putExtra(EXTRA_GRAPH_NAME, graphName)
                .putExtra(EXTRA_SUMMARY, summary)
                .putExtra(EXTRA_PARTIAL, partial);
    }

    public void register(@NonNull Context context) {
        ContextCompat.registerReceiver(context, this, new IntentFilter(ACTION_RUN_FINISHED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void unregister(@NonNull Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException alreadyGone) {
            // Not registered: nothing to undo.
        }
    }

    @Override
    public void onReceive(Context context, @Nullable Intent intent) {
        if (intent == null || !ACTION_RUN_FINISHED.equals(intent.getAction())) {
            return;
        }
        String graph = intent.getStringExtra(EXTRA_GRAPH_NAME);
        String summary = intent.getStringExtra(EXTRA_SUMMARY);
        listener.onRunFinished(intent.getLongExtra(EXTRA_RUN_ID, 0L), graph == null ? "" : graph,
                summary == null ? "" : summary, intent.getBooleanExtra(EXTRA_PARTIAL, false));
    }
}

package com.graphlab.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Listens for the system's "battery is low" and "battery is okay again" broadcasts. Registered in code, for as
 * long as an experiment runs (see {@code ExperimentService}), because these two broadcasts are not delivered
 * to receivers declared in the manifest.
 */
public final class BatteryStateReceiver extends BroadcastReceiver {

    /** What to do when the battery changes state. Called on the main thread. */
    public interface Listener {
        void onBatteryLow();

        void onBatteryOkay();
    }

    private final Listener listener;

    public BatteryStateReceiver(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** Registers for both broadcasts. Call {@link #unregister} when done. */
    public void register(@NonNull Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void unregister(@NonNull Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException alreadyGone) {
            // Not registered: nothing to undo.
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
            listener.onBatteryLow();
        } else if (Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) {
            listener.onBatteryOkay();
        }
    }
}

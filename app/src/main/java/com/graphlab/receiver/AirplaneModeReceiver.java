package com.graphlab.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Tells a screen when airplane mode is switched on or off (system broadcast ACTION_AIRPLANE_MODE_CHANGED).
 * Registered in code while the screen is visible, like {@link ConnectivityReceiver}.
 */
public final class AirplaneModeReceiver extends BroadcastReceiver {

    /** Called on the main thread with the state right now. */
    public interface Listener {
        void onAirplaneModeChanged(boolean on);
    }

    private final Listener listener;

    public AirplaneModeReceiver(@NonNull Listener listener) {
        this.listener = listener;
    }

    public static boolean isOn(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public void register(@NonNull Context context) {
        ContextCompat.registerReceiver(context, this, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        listener.onAirplaneModeChanged(isOn(context));   // the current state, before any change
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
        listener.onAirplaneModeChanged(intent.getBooleanExtra("state", isOn(context)));
    }
}

package com.graphlab.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.graphlab.data.net.NetworkStatus;

/**
 * Tells a screen when the phone goes online or offline. Registered in code while the screen is visible (a
 * manifest receiver would not be called on modern Android). The experiment itself needs no network, so this only
 * keeps the Experiment screen honest about what the cloud features can do right now.
 */
@SuppressWarnings("deprecation")   // CONNECTIVITY_ACTION is deprecated but still the broadcast this checklist item is about
public final class ConnectivityReceiver extends BroadcastReceiver {

    /** Called on the main thread with the state right now. */
    public interface Listener {
        void onConnectivityChanged(boolean online);
    }

    private final Listener listener;

    public ConnectivityReceiver(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void register(@NonNull Context context) {
        ContextCompat.registerReceiver(context, this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        listener.onConnectivityChanged(NetworkStatus.isOnline(context));   // the current state, before any change
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
        listener.onConnectivityChanged(NetworkStatus.isOnline(context));
    }
}

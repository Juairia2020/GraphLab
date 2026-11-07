package com.graphlab.data.cloud;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.graphlab.BuildConfig;

/**
 * Live "players online" count on the Firebase Realtime Database. While a screen is started, the signed-in user is
 * listed under {@code presence/{uid}}; the server removes the entry by itself when the connection drops
 * ({@code onDisconnect}), and a listener on {@code presence} reports how many entries there are right now.
 * Firestore holds the saved data (gallery, progress, leaderboard); this class is the one place that needs push-style updates.
 */
public final class PresenceCounter {

    public interface Listener {
        /** Called on the main thread each time the number of players online changes. */
        void onCount(int online, @NonNull String uid);

        void onFailure(@NonNull String message);
    }

    private DatabaseReference presence;
    private DatabaseReference mine;
    private ValueEventListener valueListener;

    public static boolean isAvailable() {
        return CloudConfig.isDatabaseConfigured();
    }

    public void start(@NonNull Context context, @NonNull Listener listener) {
        if (!isAvailable() || !FirebaseInit.init(context.getApplicationContext())) {
            listener.onFailure("The live count is not set up in this build.");
            return;
        }
        stop();
        final FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            attach(user.getUid(), listener);
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            FirebaseUser signedIn = auth.getCurrentUser();
            if (task.isSuccessful() && signedIn != null) {
                attach(signedIn.getUid(), listener);
            } else {
                listener.onFailure("Sign-in failed: " + describe(task.getException()));
            }
        });
    }

    private void attach(@NonNull String uid, @NonNull Listener listener) {
        FirebaseDatabase database = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), BuildConfig.FIREBASE_DATABASE_URL);
        presence = database.getReference("presence");
        mine = presence.child(uid);
        mine.onDisconnect().removeValue();   // the server clears it if the phone loses its connection
        mine.setValue(Boolean.TRUE);
        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onCount((int) snapshot.getChildrenCount(), uid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onFailure(error.getMessage());
            }
        };
        presence.addValueEventListener(valueListener);
    }

    /** Leaves the list and stops listening. Safe to call more than once. */
    public void stop() {
        if (presence != null && valueListener != null) {
            presence.removeEventListener(valueListener);
        }
        if (mine != null) {
            mine.removeValue();
        }
        presence = null;
        mine = null;
        valueListener = null;
    }

    private static String describe(@Nullable Exception error) {
        return error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
    }
}

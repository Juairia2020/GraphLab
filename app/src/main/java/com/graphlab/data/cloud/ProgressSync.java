package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/**
 * The player's progress in one Firestore document, {@code users/{uid}} (MS12). The player is signed in anonymously, as in the
 * gallery. Two calls: {@link #fetch} reads the document and {@link #push} writes it; joining the two copies is the engine's
 * job ({@code PlayerProgress.merge} and {@code Mastery.merge}), so the document can never lose xp or stars that either
 * device had. Does nothing when the build has no Firebase settings.
 */
public final class ProgressSync {
    static final String COLLECTION = "users";
    static final String PROGRESS = "progress";
    static final String MASTERY = "mastery";
    static final String UPDATED = "updatedAt";

    /** What came back from the cloud. Both texts are null when the player has no document yet. */
    public interface FetchCallback {
        void onFetched(@Nullable String progressJson, @Nullable String masteryText);

        void onFailure(@NonNull String message);
    }

    public interface DoneCallback {
        void onDone();

        void onFailure(@NonNull String message);
    }

    private ProgressSync() {
    }

    public static boolean isAvailable() {
        return CloudConfig.isConfigured();
    }

    public static void fetch(@NonNull FetchCallback callback) {
        withUser(new UserCallback() {
            @Override
            public void onUser(@NonNull String uid) {
                FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).get().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onFailure(describe(task.getException()));
                        return;
                    }
                    DocumentSnapshot document = task.getResult();
                    if (document == null || !document.exists()) {
                        callback.onFetched(null, null);
                        return;
                    }
                    callback.onFetched(document.getString(PROGRESS), document.getString(MASTERY));
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                callback.onFailure(message);
            }
        });
    }

    public static void push(@NonNull String progressJson, @NonNull String masteryText, @NonNull DoneCallback callback) {
        withUser(new UserCallback() {
            @Override
            public void onUser(@NonNull String uid) {
                Map<String, Object> data = new HashMap<>();
                data.put(PROGRESS, progressJson);
                data.put(MASTERY, masteryText);
                data.put(UPDATED, System.currentTimeMillis());
                FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).set(data).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onDone();
                    } else {
                        callback.onFailure(describe(task.getException()));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                callback.onFailure(message);
            }
        });
    }

    private interface UserCallback {
        void onUser(@NonNull String uid);

        void onFailure(@NonNull String message);
    }

    private static void withUser(@NonNull UserCallback callback) {
        if (!isAvailable()) {
            callback.onFailure("The cloud is not set up in this build.");
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            callback.onUser(user.getUid());
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            FirebaseUser signedIn = auth.getCurrentUser();
            if (task.isSuccessful() && signedIn != null) {
                callback.onUser(signedIn.getUid());
            } else {
                callback.onFailure("Sign-in failed: " + describe(task.getException()));
            }
        });
    }

    private static String describe(@Nullable Exception error) {
        return error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
    }
}

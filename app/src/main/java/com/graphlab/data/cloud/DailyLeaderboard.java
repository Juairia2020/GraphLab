package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.graphlab.engine.arena.Leaderboard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The daily puzzle leaderboard in Firestore (MS17): one document per player per day at {@code daily/{date}/scores/{uid}}. The
 * first result of the day is kept (the rules allow a create and no update). Ranking is done by the engine's
 * {@link Leaderboard}, so every device shows the same order. Does nothing when the build has no Firebase settings.
 */
public final class DailyLeaderboard {
    static final String DAILY = "daily";
    static final String SCORES = "scores";
    public static final int SHOWN = 20;

    public interface TopCallback {
        /** @param me the signed-in player's id, or "" when unknown */
        void onTop(@NonNull List<Leaderboard.Entry> top, @NonNull String me, int myRank);

        void onFailure(@NonNull String message);
    }

    public interface DoneCallback {
        void onDone();

        void onFailure(@NonNull String message);
    }

    private DailyLeaderboard() {
    }

    public static boolean isAvailable() {
        return CloudConfig.isConfigured();
    }

    /** Writes the player's result for {@code date}. Fails (without harm) when the player already has one. */
    public static void submit(@NonNull String date, @NonNull String name, int score, int correct, int seconds,
            @NonNull DoneCallback callback) {
        withUser(new UserCallback() {
            @Override
            public void onUser(@NonNull String uid) {
                Map<String, Object> data = new HashMap<>();
                data.put("name", name);
                data.put("score", score);
                data.put("correct", correct);
                data.put("seconds", seconds);
                data.put("submittedAt", System.currentTimeMillis());
                FirebaseFirestore.getInstance().collection(DAILY).document(date).collection(SCORES).document(uid)
                        .set(data).addOnCompleteListener(task -> {
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

    /** Reads the best results of {@code date}; the player's own rank is found among the same documents. */
    public static void fetchTop(@NonNull String date, @NonNull TopCallback callback) {
        withUser(new UserCallback() {
            @Override
            public void onUser(@NonNull String uid) {
                FirebaseFirestore.getInstance().collection(DAILY).document(date).collection(SCORES)
                        .orderBy("score", Query.Direction.DESCENDING).limit(100).get().addOnCompleteListener(task -> {
                            if (!task.isSuccessful() || task.getResult() == null) {
                                callback.onFailure(describe(task.getException()));
                                return;
                            }
                            List<Leaderboard.Entry> all = new ArrayList<>();
                            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                                Long score = d.getLong("score");
                                Long seconds = d.getLong("seconds");
                                Long at = d.getLong("submittedAt");
                                String name = d.getString("name");
                                if (score != null && seconds != null && at != null && name != null) {
                                    all.add(new Leaderboard.Entry(d.getId(), name, score.intValue(), seconds.intValue(), at));
                                }
                            }
                            callback.onTop(Leaderboard.top(all, SHOWN), uid, Leaderboard.rankOf(all, uid));
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

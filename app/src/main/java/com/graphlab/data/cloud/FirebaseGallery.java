package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.net.GalleryPost;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link CloudGallery} on Firebase: anonymous Firebase Authentication for the user, and one
 * Cloud Firestore collection, "graphs", for the posts. Firebase must have been started with
 * {@link FirebaseInit#init} first.
 */
public final class FirebaseGallery implements CloudGallery {
    static final String COLLECTION = "graphs";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public void signIn(@NonNull Callback<String> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            callback.onSuccess(user.getUid());
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            FirebaseUser signedIn = auth.getCurrentUser();
            if (task.isSuccessful() && signedIn != null) {
                callback.onSuccess(signedIn.getUid());
            } else {
                callback.onFailure("Sign-in failed: " + describe(task.getException())
                        + " (is Anonymous sign-in turned on in the Firebase console?)");
            }
        });
    }

    @Nullable
    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override
    public void list(int limit, @NonNull Callback<List<GalleryPost.Entry>> callback) {
        db.collection(COLLECTION)
                .orderBy(GalleryPost.CREATED, Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onFailure(describe(task.getException()));
                        return;
                    }
                    List<GalleryPost.Entry> entries = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Map<String, Object> data = document.getData();
                        if (data == null) {
                            continue;
                        }
                        try {
                            entries.add(GalleryPost.fromFields(document.getId(), data));
                        } catch (GraphFormatException skipped) {
                            // Someone wrote a post that is not a valid graph: it is simply not shown.
                        }
                    }
                    callback.onSuccess(entries);
                });
    }

    @Override
    public void publish(@NonNull Map<String, Object> fields, @NonNull Callback<String> callback) {
        db.collection(COLLECTION).add(fields).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess(task.getResult().getId());
            } else {
                callback.onFailure(describe(task.getException()));
            }
        });
    }

    @Override
    public void delete(@NonNull String id, @NonNull Callback<Boolean> callback) {
        db.collection(COLLECTION).document(id).delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess(Boolean.TRUE);
            } else {
                callback.onFailure(describe(task.getException()));
            }
        });
    }

    private static String describe(@Nullable Exception error) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "The cloud refused the request. Check the Firestore rules (see the README, Cloud gallery).";
        }
        if (error instanceof FirebaseNetworkException) {
            return "There is no connection to the cloud.";
        }
        return error == null || error.getMessage() == null ? "Something went wrong with the cloud." : error.getMessage();
    }
}

package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.engine.net.GalleryPost;
import java.util.List;
import java.util.Map;

/**
 * The cloud gallery as the screens see it: sign in, list, publish, delete. Keeping Firebase behind
 * this interface means the screens know nothing about Firebase, and a different backend (or a
 * fake for a test) could replace it. All callbacks arrive on the main thread, and a failure comes
 * as a message that can be shown to the user as it is.
 */
public interface CloudGallery {

    interface Callback<T> {
        void onSuccess(T value);

        void onFailure(@NonNull String message);
    }

    /** Signs in without a password (anonymous account). The callback gets the user id. */
    void signIn(@NonNull Callback<String> callback);

    /** The signed-in user id, or null. */
    @Nullable
    String currentUid();

    /** The newest {@code limit} posts, newest first. Posts that fail the checks are left out. */
    void list(int limit, @NonNull Callback<List<GalleryPost.Entry>> callback);

    /** Stores a post made by {@link GalleryPost#toFields}. The callback gets the new post's id. */
    void publish(@NonNull Map<String, Object> fields, @NonNull Callback<String> callback);

    /** Deletes a post. The cloud rules only allow the owner to do this. */
    void delete(@NonNull String id, @NonNull Callback<Boolean> callback);
}

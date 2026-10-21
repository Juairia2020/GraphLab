package com.graphlab.ui.online;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import com.graphlab.data.cloud.CloudConfig;
import com.graphlab.data.cloud.CloudGallery;
import com.graphlab.data.cloud.FirebaseGallery;
import com.graphlab.data.cloud.FirebaseInit;
import com.graphlab.engine.net.GalleryPost;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * State of the cloud gallery screen, kept across rotation: whether the cloud is set up, who is
 * signed in, and the newest shared graphs. The screen only draws it.
 */
public final class GalleryViewModel extends ViewModel {
    static final int LIMIT = 50;

    enum State { NOT_CONFIGURED, SIGNING_IN, LOADING, READY, ERROR }

    interface Listener {
        void onChanged();
    }

    private State state = State.NOT_CONFIGURED;
    private List<GalleryPost.Entry> entries = Collections.emptyList();
    private String error = "";
    @Nullable
    private String uid;
    @Nullable
    private CloudGallery gallery;
    @Nullable
    private Listener listener;
    private boolean started;

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    State state() {
        return state;
    }

    List<GalleryPost.Entry> entries() {
        return entries;
    }

    String error() {
        return error;
    }

    @Nullable
    String uid() {
        return uid;
    }

    /** Starts once (sign in, then load the list). Later calls do nothing; use {@link #refresh()}. */
    void start(@NonNull Context context) {
        if (started) {
            return;
        }
        started = true;
        if (!CloudConfig.isConfigured()) {
            state = State.NOT_CONFIGURED;
            notifyChanged();
            return;
        }
        try {
            FirebaseInit.init(context.getApplicationContext());
            gallery = new FirebaseGallery();
        } catch (RuntimeException e) {
            state = State.ERROR;
            error = e.getMessage() == null ? "The cloud could not be started." : e.getMessage();
            notifyChanged();
            return;
        }
        signInAndLoad();
    }

    void refresh() {
        if (gallery == null || state == State.SIGNING_IN || state == State.LOADING) {
            return;
        }
        if (uid == null) {
            signInAndLoad();
        } else {
            load();
        }
    }

    private void signInAndLoad() {
        state = State.SIGNING_IN;
        notifyChanged();
        gallery.signIn(new CloudGallery.Callback<String>() {
            @Override
            public void onSuccess(String value) {
                uid = value;
                load();
            }

            @Override
            public void onFailure(@NonNull String message) {
                fail(message);
            }
        });
    }

    private void load() {
        state = State.LOADING;
        notifyChanged();
        gallery.list(LIMIT, new CloudGallery.Callback<List<GalleryPost.Entry>>() {
            @Override
            public void onSuccess(List<GalleryPost.Entry> value) {
                entries = value;
                error = "";
                state = State.READY;
                notifyChanged();
            }

            @Override
            public void onFailure(@NonNull String message) {
                fail(message);
            }
        });
    }

    void publish(@NonNull Map<String, Object> fields, @NonNull CloudGallery.Callback<String> callback) {
        if (gallery == null) {
            callback.onFailure("The cloud is not set up.");
            return;
        }
        gallery.publish(fields, new CloudGallery.Callback<String>() {
            @Override
            public void onSuccess(String id) {
                callback.onSuccess(id);
                load();
            }

            @Override
            public void onFailure(@NonNull String message) {
                callback.onFailure(message);
            }
        });
    }

    void delete(@NonNull String id, @NonNull CloudGallery.Callback<Boolean> callback) {
        if (gallery == null) {
            callback.onFailure("The cloud is not set up.");
            return;
        }
        gallery.delete(id, new CloudGallery.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                callback.onSuccess(value);
                load();
            }

            @Override
            public void onFailure(@NonNull String message) {
                callback.onFailure(message);
            }
        });
    }

    private void fail(String message) {
        error = message;
        state = State.ERROR;
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onChanged();
        }
    }
}

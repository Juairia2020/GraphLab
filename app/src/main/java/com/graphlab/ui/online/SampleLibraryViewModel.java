package com.graphlab.ui.online;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import com.graphlab.BuildConfig;
import com.graphlab.GraphLabApp;
import com.graphlab.R;
import com.graphlab.data.AppExecutors;
import com.graphlab.data.net.HttpFetcher;
import com.graphlab.data.net.NetworkStatus;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.net.SampleLibrary;
import com.graphlab.engine.net.WebAddress;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Loads the sample library and keeps it across rotation. It asks the web first; if there is no
 * address set, no connection, or the answer is not a valid library, it reads the copy that ships
 * inside the app (assets/sample-library.json) and says why. The screen therefore always has
 * something to show, and a bad server can never put an unchecked graph in the app: every library,
 * online or built in, goes through {@link SampleLibrary#parse}.
 */
public final class SampleLibraryViewModel extends ViewModel {
    static final String ASSET = "sample-library.json";

    enum State { IDLE, LOADING, READY, ERROR }

    interface Listener {
        void onChanged();
    }

    /** What a finished load produced. */
    private static final class Loaded {
        final List<SampleLibrary.Entry> entries;
        final boolean online;
        final String note;

        Loaded(List<SampleLibrary.Entry> entries, boolean online, String note) {
            this.entries = entries;
            this.online = online;
            this.note = note;
        }
    }

    private State state = State.IDLE;
    private List<SampleLibrary.Entry> entries = Collections.emptyList();
    private boolean online;
    private String note = "";
    private String error = "";
    @Nullable
    private Listener listener;

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    State state() {
        return state;
    }

    List<SampleLibrary.Entry> entries() {
        return entries;
    }

    /** True if the list came from the web, false if it is the copy inside the app. */
    boolean isOnline() {
        return online;
    }

    /** Why the built-in copy is shown, or an empty string. */
    String note() {
        return note;
    }

    String error() {
        return error;
    }

    /** Loads once; call again with {@code force} to ask the web again. */
    void load(@NonNull Context context, boolean force) {
        if (state == State.LOADING || (state == State.READY && !force)) {
            return;
        }
        state = State.LOADING;
        notifyChanged();
        final Context app = context.getApplicationContext();
        GraphLabApp.container(app).executors().network(() -> fetch(app), new AppExecutors.Callback<Loaded>() {
            @Override
            public void onSuccess(Loaded result) {
                entries = result.entries;
                online = result.online;
                note = result.note;
                error = "";
                state = State.READY;
                notifyChanged();
            }

            @Override
            public void onFailure(@NonNull Exception problem) {
                error = problem.getMessage() == null ? app.getString(R.string.samples_error) : problem.getMessage();
                state = State.ERROR;
                notifyChanged();
            }
        });
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onChanged();
        }
    }

    /** Runs on a network thread. */
    private static Loaded fetch(Context app) throws Exception {
        String reason;
        String address = BuildConfig.SAMPLE_LIBRARY_URL;
        if (WebAddress.isPlaceholder(address)) {
            reason = app.getString(R.string.samples_note_no_address);
        } else if (!NetworkStatus.isOnline(app)) {
            reason = app.getString(R.string.samples_note_offline);
        } else {
            try {
                return new Loaded(SampleLibrary.parse(HttpFetcher.get(address)), true, "");
            } catch (IOException | GraphFormatException e) {
                reason = app.getString(R.string.samples_note_failed, e.getMessage());
            }
        }
        try (InputStream in = app.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new Loaded(SampleLibrary.parse(new String(out.toByteArray(), StandardCharsets.UTF_8)), false, reason);
        }
    }
}

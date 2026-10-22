package com.graphlab.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.graphlab.data.cloud.ProgressSync;
import com.graphlab.data.db.GraphRepository;
import com.graphlab.engine.content.ContentException;
import com.graphlab.engine.progress.Mastery;
import com.graphlab.engine.progress.PlayerProgress;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the player has earned and how well they know each topic (MS12): xp, gems, streak, puzzle stars and mastery. Kept in
 * memory, saved in the database ({@code progress} table) and joined with the player's cloud document when the cloud is set
 * up. Every change runs on the single disk thread, in the order it was asked, so a change made while the first load is still
 * running is not lost. Listeners are called on the main thread.
 */
public final class ProgressStore {
    private static final String KEY_PLAYER = "player";
    private static final String KEY_MASTERY = "mastery";

    /** Told when xp, gems, streak, stars or mastery changed. */
    public interface Listener {
        void onProgressChanged();
    }

    private final AppExecutors executors;
    private final GraphRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private PlayerProgress player = PlayerProgress.EMPTY;
    private Mastery mastery = new Mastery();
    private boolean loaded;

    ProgressStore(@NonNull Context context, @NonNull AppExecutors executors, @NonNull GraphRepository repository) {
        this.executors = executors;
        this.repository = repository;
    }

    /** Reads the saved values, then joins them with the cloud. Called once, when the app starts. */
    void load() {
        executors.disk(() -> {
            String playerJson = repository.progressValue(KEY_PLAYER);
            String masteryText = repository.progressValue(KEY_MASTERY);
            synchronized (this) {
                if (playerJson != null) {
                    try {
                        player = PlayerProgress.fromJson(playerJson);
                    } catch (ContentException damaged) {
                        player = PlayerProgress.EMPTY;   // a damaged save starts again rather than blocking the app
                    }
                }
                mastery = Mastery.parse(masteryText);
                loaded = true;
            }
            notifyListeners();
            sync();
        });
    }

    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** The current values (empty until the first load has finished; listeners are told when it has). */
    @NonNull
    public synchronized PlayerProgress player() {
        return player;
    }

    /** A copy of the mastery record, safe to keep and to change. */
    @NonNull
    public synchronized Mastery masteryCopy() {
        return Mastery.parse(mastery.encode());
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    // ---- earning ---------------------------------------------------------------------

    /** One answered question: moves the mastery of its topic. */
    public void recordAnswer(@NonNull String topic, boolean correct) {
        final Instant now = Instant.now();
        executors.disk(() -> {
            String text;
            synchronized (this) {
                mastery.record(topic, correct, now);
                text = mastery.encode();
            }
            repository.putProgressValue(KEY_MASTERY, text, now.toEpochMilli());
            notifyListeners();
        });
    }

    /** A finished Arena round: xp from its points, gems from its stars, and the streak. */
    public void awardRound(int points, int stars) {
        final Instant now = Instant.now();
        final ZoneId zone = ZoneId.systemDefault();
        executors.disk(() -> {
            synchronized (this) {
                player = player.withRound(points, stars, now, zone);
            }
            savePlayer(now);
            notifyListeners();
            sync();
        });
    }

    /** A finished puzzle level: pays only for stars better than the best so far. */
    public void awardPuzzle(@NonNull String levelId, int stars) {
        final Instant now = Instant.now();
        final ZoneId zone = ZoneId.systemDefault();
        executors.disk(() -> {
            synchronized (this) {
                player = player.withPuzzle(levelId, stars, now, zone);
            }
            savePlayer(now);
            notifyListeners();
            sync();
        });
    }

    private void savePlayer(Instant now) {
        String json;
        synchronized (this) {
            json = player.toJson();
        }
        repository.putProgressValue(KEY_PLAYER, json, now.toEpochMilli());
    }

    // ---- the cloud -------------------------------------------------------------------

    /** Reads the cloud copy, joins it with this one (the engine's merge) and writes the result back. Quiet when offline. */
    private void sync() {
        if (!ProgressSync.isAvailable()) {
            return;
        }
        ProgressSync.fetch(new ProgressSync.FetchCallback() {
            @Override
            public void onFetched(@Nullable String progressJson, @Nullable String masteryText) {
                executors.disk(() -> {
                    String sendPlayer;
                    String sendMastery;
                    synchronized (ProgressStore.this) {
                        if (progressJson != null) {
                            try {
                                player = player.merge(PlayerProgress.fromJson(progressJson));
                            } catch (ContentException ignored) {
                                // a damaged cloud copy is overwritten by this one
                            }
                        }
                        if (masteryText != null) {
                            mastery = mastery.merge(Mastery.parse(masteryText));
                        }
                        sendPlayer = player.toJson();
                        sendMastery = mastery.encode();
                    }
                    long now = System.currentTimeMillis();
                    repository.putProgressValue(KEY_PLAYER, sendPlayer, now);
                    repository.putProgressValue(KEY_MASTERY, sendMastery, now);
                    notifyListeners();
                    ProgressSync.push(sendPlayer, sendMastery, new ProgressSync.DoneCallback() {
                        @Override
                        public void onDone() {
                        }

                        @Override
                        public void onFailure(@NonNull String message) {
                            // offline or rules not published: the local copy is still right and is sent next time
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                // offline: nothing to do, the next change tries again
            }
        });
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Listener listener : listeners) {
                listener.onProgressChanged();
            }
        });
    }
}

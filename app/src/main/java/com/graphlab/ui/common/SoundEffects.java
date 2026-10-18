package com.graphlab.ui.common;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import androidx.annotation.NonNull;
import com.graphlab.R;
import com.graphlab.data.prefs.EffectsPrefs;

/**
 * Short UI sounds from {@code res/raw}: correct, wrong, tick, win and done. They play on the media volume (as game
 * sounds). Silent when sound is off or effects are reduced.
 */
public final class SoundEffects {
    private final Context context;
    private final SoundPool pool;
    private final int correct;
    private final int wrong;
    private final int tick;
    private final int win;
    private final int done;

    public SoundEffects(@NonNull Context context) {
        this.context = context.getApplicationContext();
        pool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        correct = pool.load(this.context, R.raw.fx_correct, 1);
        wrong = pool.load(this.context, R.raw.fx_wrong, 1);
        tick = pool.load(this.context, R.raw.fx_tick, 1);
        win = pool.load(this.context, R.raw.fx_win, 1);
        done = pool.load(this.context, R.raw.fx_done, 1);
    }

    public void playAnswer(boolean right) {
        play(right ? correct : wrong);
    }

    /** A rising jingle for a round finished with good marks. */
    public void playWin() {
        play(win);
    }

    /** A soft two-note chime for the end of a run or an experiment. */
    public void playDone() {
        play(done);
    }

    public void playTick() {
        play(tick);
    }

    private void play(int id) {
        if (EffectsPrefs.sound(context)) {
            pool.play(id, 1f, 1f, 1, 0, 1f);
        }
    }

    public void release() {
        pool.release();
    }
}

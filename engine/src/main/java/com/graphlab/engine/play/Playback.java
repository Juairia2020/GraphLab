package com.graphlab.engine.play;

/**
 * The play head over a finished trace: which step is showing, whether it is playing, and how
 * fast. Pure state, no timers and no Android, so the rules are unit tested. The Android side
 * owns the clock: it calls {@link #tick()} every {@link #delayMillis()} while
 * {@link #isPlaying()} and stops when tick() says so.
 *
 * <p>Steps are numbered from 0 to size - 1, so step 0 is the picture before anything happens.
 * Stepping by hand, scrubbing or jumping always pauses playback, like a video player.
 */
public final class Playback {
    public static final double MIN_SPEED = 0.5;
    public static final double MAX_SPEED = 4.0;
    public static final double DEFAULT_SPEED = 1.0;
    /** Time between steps at 1x. */
    public static final long BASE_DELAY_MILLIS = 1000;

    private final int size;
    private int index;
    private boolean playing;
    private double speed = DEFAULT_SPEED;

    /** @param size how many steps the trace has (at least 1) */
    public Playback(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("A playback needs at least one step, got " + size);
        }
        this.size = size;
    }

    public int size() {
        return size;
    }

    /** The step on screen, 0 to size - 1. */
    public int index() {
        return index;
    }

    public boolean isPlaying() {
        return playing;
    }

    public double speed() {
        return speed;
    }

    public boolean atStart() {
        return index == 0;
    }

    public boolean atEnd() {
        return index == size - 1;
    }

    /** Milliseconds to wait before the next step at the current speed. */
    public long delayMillis() {
        return Math.round(BASE_DELAY_MILLIS / speed);
    }

    /** Clamps to {@link #MIN_SPEED} .. {@link #MAX_SPEED}. Does not change whether it is playing. */
    public void setSpeed(double newSpeed) {
        if (Double.isNaN(newSpeed)) {
            return;
        }
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, newSpeed));
    }

    /**
     * Play or pause. Pressing play on the last step starts again from step 0, as a video does.
     *
     * @return true if the step on screen changed (a restart from the end)
     */
    public boolean togglePlay() {
        if (playing) {
            playing = false;
            return false;
        }
        boolean restarted = false;
        if (atEnd() && size > 1) {
            index = 0;
            restarted = true;
        }
        playing = true;
        return restarted;
    }

    public void pause() {
        playing = false;
    }

    /** One step forward by hand. Pauses. @return true if the step changed */
    public boolean next() {
        playing = false;
        if (atEnd()) {
            return false;
        }
        index++;
        return true;
    }

    /** One step back by hand. Pauses. @return true if the step changed */
    public boolean previous() {
        playing = false;
        if (atStart()) {
            return false;
        }
        index--;
        return true;
    }

    /** Jumps to a step (clamped into range). Pauses. @return true if the step changed */
    public boolean seek(int target) {
        playing = false;
        int clamped = Math.max(0, Math.min(size - 1, target));
        if (clamped == index) {
            return false;
        }
        index = clamped;
        return true;
    }

    public boolean toFirst() {
        return seek(0);
    }

    public boolean toLast() {
        return seek(size - 1);
    }

    /**
     * One clock tick. Moves forward if playing and stops playing on reaching the last step.
     *
     * @return true if the clock should tick again (still playing); false once paused or finished
     */
    public boolean tick() {
        if (!playing) {
            return false;
        }
        if (atEnd()) {
            playing = false;
            return false;
        }
        index++;
        if (atEnd()) {
            playing = false;
        }
        return playing;
    }
}

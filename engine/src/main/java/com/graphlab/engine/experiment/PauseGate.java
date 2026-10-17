package com.graphlab.engine.experiment;

/**
 * Lets one thread (the experiment) stop and wait while another thread (the screen, or a low-battery
 * broadcast) pauses, resumes or cancels it. A pause remembers who asked: a pause because of low battery is
 * lifted automatically when the battery is okay again, but a pause the user chose stays until the user resumes.
 * Plain Java, thread safe.
 */
public final class PauseGate {
    /** Who paused. */
    public enum Reason {
        USER, BATTERY
    }

    private boolean paused;
    private boolean cancelled;
    private Reason reason;

    /** Pauses. If already paused, a user pause takes over from a battery pause (never the other way round). */
    public synchronized void pause(Reason why) {
        if (cancelled) {
            return;
        }
        if (!paused || why == Reason.USER) {
            reason = why;
        }
        paused = true;
    }

    /**
     * Resumes if paused. {@code who} USER always resumes; BATTERY only lifts a battery pause.
     *
     * @return true if the experiment was paused and now runs again
     */
    public synchronized boolean resume(Reason who) {
        if (!paused || (who == Reason.BATTERY && reason != Reason.BATTERY)) {
            return false;
        }
        paused = false;
        reason = null;
        notifyAll();
        return true;
    }

    public synchronized void cancel() {
        cancelled = true;
        paused = false;
        notifyAll();
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** Why it is paused, or null when it is not. */
    public synchronized Reason reason() {
        return paused ? reason : null;
    }

    /**
     * Called by the experiment before each trial: returns at once when running, waits while paused.
     *
     * @return false if the experiment was cancelled (or the thread interrupted) and must stop
     */
    public synchronized boolean awaitRunnable() {
        while (paused && !cancelled) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !cancelled;
    }
}

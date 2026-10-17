package com.graphlab.engine.experiment;

/**
 * Runs randomized trials one after another, reporting progress, honouring a {@link PauseGate}. This is the loop
 * the foreground service runs; it is plain Java so it can be tested without a phone.
 */
public final class ExperimentRunner {
    private ExperimentRunner() {
    }

    /** Called from the running thread after every {@code reportEvery} trials and once at the end. */
    public interface Listener {
        /** {@code histogram} is a private copy the listener may keep. */
        void onProgress(int done, int total, Histogram histogram);
    }

    /** How the run ended. */
    public static final class Outcome {
        private final int done;
        private final boolean cancelled;
        private final Histogram histogram;

        Outcome(int done, boolean cancelled, Histogram histogram) {
            this.done = done;
            this.cancelled = cancelled;
            this.histogram = histogram;
        }

        /** Trials that finished. */
        public int done() {
            return done;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public Histogram histogram() {
            return histogram;
        }
    }

    /**
     * @param trials      the prepared graph
     * @param baseSeed    trial i uses {@link KargerTrials#seedFor(long, int)} of this
     * @param total       how many trials to run
     * @param gate        pause, resume and cancel control
     * @param reportEvery report every this many trials (at least 1)
     * @param paceMillis  sleep after each trial; 0 for full speed, more to watch it work
     * @param listener    progress listener, may be null
     */
    public static Outcome run(Trials trials, long baseSeed, int total, PauseGate gate, int reportEvery,
                              long paceMillis, Listener listener) {
        if (total < 1) {
            throw new IllegalArgumentException("total must be at least 1");
        }
        int every = Math.max(1, reportEvery);
        Histogram histogram = new Histogram();
        int done = 0;
        while (done < total) {
            if (!gate.awaitRunnable()) {
                return new Outcome(done, true, histogram);
            }
            histogram.add(trials.trial(KargerTrials.seedFor(baseSeed, done)));
            done++;
            if (listener != null && (done % every == 0 || done == total)) {
                listener.onProgress(done, total, histogram.copy());
            }
            if (paceMillis > 0 && done < total) {
                try {
                    Thread.sleep(paceMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Outcome(done, true, histogram);
                }
            }
        }
        return new Outcome(done, false, histogram);
    }
}

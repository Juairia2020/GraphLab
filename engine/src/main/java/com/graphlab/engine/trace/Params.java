package com.graphlab.engine.trace;

/**
 * Inputs to a run, apart from the graph. Stored in the {@link Trace} so a run can be repeated.
 *
 * @param source node id to start from, or {@link #NO_NODE} for algorithms that need none
 * @param seed   seed for any randomness (used from MS5); fixed so runs stay reproducible
 */
public record Params(int source, long seed) {
    public static final int NO_NODE = -1;

    public static Params none() {
        return new Params(NO_NODE, 0L);
    }

    public static Params from(int source) {
        return new Params(source, 0L);
    }

    public Params withSeed(long newSeed) {
        return new Params(source, newSeed);
    }

    public boolean hasSource() {
        return source != NO_NODE;
    }
}

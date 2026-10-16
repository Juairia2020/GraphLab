package com.graphlab.engine.trace;

/**
 * Inputs to a run, apart from the graph. Stored in the {@link Trace} so a run can be repeated.
 *
 * @param source node id to start from, or {@link #NO_NODE} for algorithms that need none
 * @param seed   seed for any randomness (random pivots, random contractions); fixed so runs stay reproducible
 * @param target node id to end at (the sink of a flow), or {@link #NO_NODE}
 */
public record Params(int source, long seed, int target) {
    public static final int NO_NODE = -1;

    /** Source and seed only; no end node. Keeps the MS1 way of building params working. */
    public Params(int source, long seed) {
        this(source, seed, NO_NODE);
    }

    public static Params none() {
        return new Params(NO_NODE, 0L);
    }

    public static Params from(int source) {
        return new Params(source, 0L);
    }

    public Params withSeed(long newSeed) {
        return new Params(source, newSeed, target);
    }

    public Params withTarget(int newTarget) {
        return new Params(source, seed, newTarget);
    }

    public boolean hasSource() {
        return source != NO_NODE;
    }

    public boolean hasTarget() {
        return target != NO_NODE;
    }
}

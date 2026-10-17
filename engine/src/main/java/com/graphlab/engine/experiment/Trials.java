package com.graphlab.engine.experiment;

/**
 * One randomized experiment that can be repeated many times. {@code trial(seed)} runs it once with its own
 * random seed and returns one whole number (0 or more) for the histogram: a cut size, a count of comparisons,
 * the fullest bin, and so on. The same seed always gives the same number. Plain Java, no Android.
 */
public interface Trials {
    int trial(long seed);
}

package com.graphlab.engine.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the steps of one run and keeps its running counters. Declare every counter once,
 * up front, so all steps of a trace carry the same keys.
 */
public final class TraceRecorder {
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private int maxSteps = Integer.MAX_VALUE;
    private String tooManyMessage = "This run has too many steps to show.";

    /**
     * Caps the trace. The step that would go over the limit throws an {@link IllegalArgumentException}
     * with {@code message}, so a run that would take thousands of steps (a hard backtracking search)
     * fails with an explanation instead of filling the phone's memory.
     */
    public TraceRecorder limit(int max, String message) {
        this.maxSteps = max;
        this.tooManyMessage = message;
        return this;
    }

    /** Declares a counter with its starting value. */
    public TraceRecorder counter(String name, long initial) {
        counters.put(name, initial);
        return this;
    }

    public void set(String name, long value) {
        if (!counters.containsKey(name)) {
            throw new IllegalArgumentException("Counter not declared: " + name);
        }
        counters.put(name, value);
    }

    public void increment(String name) {
        set(name, counters.getOrDefault(name, 0L) + 1);
    }

    /** Records a step with a snapshot of the counters as they are right now. */
    public void step(Frame frame, int line, String message) {
        if (steps.size() >= maxSteps) {
            throw new IllegalArgumentException(tooManyMessage);
        }
        steps.add(new Step(frame, line, message, counters));
    }

    public int size() {
        return steps.size();
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }
}

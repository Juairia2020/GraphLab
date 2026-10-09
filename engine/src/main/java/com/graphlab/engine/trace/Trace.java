package com.graphlab.engine.trace;

import java.util.List;
import java.util.Objects;

/**
 * Everything a run produced: the list of steps for the UI to replay, and the final
 * {@link Result}. Immutable.
 */
public record Trace(String algorithmId, Params params, List<Step> steps, Result result) {

    public Trace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(result, "result");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A trace needs at least one step");
        }
    }

    public int size() {
        return steps.size();
    }

    public Step step(int index) {
        return steps.get(index);
    }

    public Step last() {
        return steps.get(steps.size() - 1);
    }
}

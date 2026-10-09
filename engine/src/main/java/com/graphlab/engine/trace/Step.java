package com.graphlab.engine.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One moment in a run.
 *
 * @param frame    the complete picture at this moment
 * @param line     the pseudocode line this step belongs to, counting from 1
 *                 (see {@link Algorithm#pseudocode()})
 * @param message  one plain-English sentence for the step log
 * @param counters named running totals, for example "relaxed" or "queue"; same keys in every
 *                 step of a trace
 */
public record Step(Frame frame, int line, String message, Map<String, Long> counters) {

    public Step {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(counters, "counters");
        if (line < 1) {
            throw new IllegalArgumentException("Pseudocode lines count from 1, got " + line);
        }
        counters = Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }
}

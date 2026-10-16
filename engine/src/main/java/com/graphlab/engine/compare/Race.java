package com.graphlab.engine.compare;

import com.graphlab.engine.trace.Step;
import com.graphlab.engine.trace.Trace;
import java.util.Locale;
import java.util.Map;

/**
 * Two finished traces played in step with each other (MS10 S2). The race has one clock, the step index. Each side shows
 * the step with that index; a side that has fewer steps stays on its last step once it is done, so the viewer sees which
 * algorithm finished first. Pure logic, no Android. The play head itself is {@link com.graphlab.engine.play.Playback}
 * created with {@link #length()} steps.
 */
public final class Race {
    private final Trace a;
    private final Trace b;

    /** At least one side must have run; a side that failed is null and shows nothing. */
    public Race(Trace a, Trace b) {
        if (a == null && b == null) {
            throw new IllegalArgumentException("A race needs at least one trace");
        }
        this.a = a;
        this.b = b;
    }

    public static Race of(Comparison.Report report) {
        return new Race(report.a().trace(), report.b().trace());
    }

    /** How many steps the race lasts: the longer of the two traces. */
    public int length() {
        return Math.max(a == null ? 0 : a.size(), b == null ? 0 : b.size());
    }

    public Trace traceA() {
        return a;
    }

    public Trace traceB() {
        return b;
    }

    /** The step side A shows at race step {@code index}, or null when A did not run. */
    public Step stepA(int index) {
        return stepOf(a, index);
    }

    public Step stepB(int index) {
        return stepOf(b, index);
    }

    private static Step stepOf(Trace trace, int index) {
        if (trace == null) {
            return null;
        }
        if (index < 0) {
            throw new IllegalArgumentException("Step index must not be negative: " + index);
        }
        return trace.step(Math.min(index, trace.size() - 1));
    }

    /** True once side A has shown its last step. */
    public boolean doneA(int index) {
        return a != null && index >= a.size() - 1;
    }

    public boolean doneB(int index) {
        return b != null && index >= b.size() - 1;
    }

    /** "1.25 ms", "830 µs" or "0.4 ms": a run time in plain units. */
    public static String formatNanos(long nanos) {
        if (nanos < 1_000_000L) {
            return Math.max(1, Math.round(nanos / 1000.0)) + " µs";
        }
        return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
    }

    /** The named counters of a step, in the order the algorithm declared them. */
    public static Map<String, Long> counters(Step step) {
        return step == null ? Map.of() : step.counters();
    }

    /**
     * Who reaches a counter value first is a matter of reading the live numbers; this tells which side has done more
     * of a named counter (for example "relaxed") at a race step: negative when A has done fewer, 0 when equal or missing.
     */
    public int compareCounter(int index, String name) {
        Long x = counters(stepA(index)).get(name);
        Long y = counters(stepB(index)).get(name);
        if (x == null || y == null) {
            return 0;
        }
        return Long.compare(x, y);
    }
}

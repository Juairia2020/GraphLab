package com.graphlab.engine.experiment;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.util.List;
import java.util.Map;

/**
 * The saved result of one experiment: what was run, how many trials finished and the histogram. It is stored as
 * JSON in the {@code detail} column of a run, so the Compare-runs screen (which only sees the ContentProvider's
 * columns) can draw the chart again. Version 1:
 *
 * <pre>
 * {"format":"graphlab-experiment","version":1,"algorithm":"karger","planned":500,"done":500,"seed":42,
 *  "histogram":[{"value":2,"count":120},{"value":3,"count":380}]}
 * </pre>
 */
public final class ExperimentRecord {
    public static final String FORMAT = "graphlab-experiment";
    public static final int VERSION = 1;

    private final String algorithmId;
    private final int planned;
    private final int done;
    private final long seed;
    private final Histogram histogram;

    /** Seeds are kept below 2^53 so they survive a trip through JSON numbers unchanged. */
    public static final long MAX_SEED = 1L << 53;

    public ExperimentRecord(String algorithmId, int planned, int done, long seed, Histogram histogram) {
        if (Math.abs(seed) >= MAX_SEED) {
            throw new IllegalArgumentException("The seed is too large to be saved exactly");
        }
        this.algorithmId = algorithmId;
        this.planned = planned;
        this.done = done;
        this.seed = seed;
        this.histogram = histogram;
    }

    public String algorithmId() {
        return algorithmId;
    }

    public int planned() {
        return planned;
    }

    public int done() {
        return done;
    }

    public long seed() {
        return seed;
    }

    public Histogram histogram() {
        return histogram;
    }

    /** True when fewer trials finished than were planned (stopped early). */
    public boolean partial() {
        return done < planned;
    }

    /** One line for lists: "500 trials, best cut 2 (found 120 times, 24%)". */
    public String summary() {
        if (histogram.isEmpty()) {
            return done + " trials";
        }
        int best = histogram.smallest();
        int times = histogram.countOf(best);
        return done + (done == 1 ? " trial" : " trials") + (partial() ? " of " + planned : "") + ", " + ExperimentAlgorithm.byId(algorithmId).bestLabel() + " " + best
                + " (found " + times + (times == 1 ? " time, " : " times, ") + Math.round(histogram.smallestShare() * 100) + "%)";
    }

    public String toJson() {
        StringBuilder out = new StringBuilder(128 + histogram.entries().size() * 24);
        out.append("{\"format\":").append(MiniJson.quote(FORMAT))
                .append(",\"version\":").append(VERSION)
                .append(",\"algorithm\":").append(MiniJson.quote(algorithmId))
                .append(",\"planned\":").append(planned)
                .append(",\"done\":").append(done)
                .append(",\"seed\":").append(seed)
                .append(",\"histogram\":[");
        boolean first = true;
        for (int[] e : histogram.entries()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"value\":").append(e[0]).append(",\"count\":").append(e[1]).append('}');
        }
        return out.append("]}").toString();
    }

    public static ExperimentRecord parse(String text) throws GraphFormatException {
        Object root = MiniJson.parse(text);
        if (!(root instanceof Map)) {
            throw new GraphFormatException("An experiment record must be a JSON object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> top = (Map<String, Object>) root;
        if (!FORMAT.equals(top.get("format"))) {
            throw new GraphFormatException("Not an experiment record.");
        }
        int version = whole(top.get("version"), "version");
        if (version < 1 || version > VERSION) {
            throw new GraphFormatException("Unsupported experiment record version " + version + ".");
        }
        if (!(top.get("algorithm") instanceof String)) {
            throw new GraphFormatException("\"algorithm\" must be text.");
        }
        int planned = whole(top.get("planned"), "planned");
        int done = whole(top.get("done"), "done");
        if (planned < 1 || done < 0 || done > planned) {
            throw new GraphFormatException("\"done\" must be between 0 and \"planned\".");
        }
        Object seedValue = top.get("seed");
        if (!(seedValue instanceof Double)) {
            throw new GraphFormatException("\"seed\" must be a number.");
        }
        if (!(top.get("histogram") instanceof List)) {
            throw new GraphFormatException("\"histogram\" must be a list.");
        }
        Histogram histogram = new Histogram();
        for (Object item : (List<?>) top.get("histogram")) {
            if (!(item instanceof Map)) {
                throw new GraphFormatException("Each histogram entry must be an object.");
            }
            Map<?, ?> entry = (Map<?, ?>) item;
            int value = whole(entry.get("value"), "value");
            int count = whole(entry.get("count"), "count");
            if (value < 0 || count < 1) {
                throw new GraphFormatException("A histogram entry has an impossible value or count.");
            }
            histogram.add(value, count);
        }
        if (histogram.total() != done) {
            throw new GraphFormatException("The histogram counts add up to " + histogram.total() + ", not " + done + ".");
        }
        return new ExperimentRecord((String) top.get("algorithm"), planned, done, ((Double) seedValue).longValue(), histogram);
    }

    private static int whole(Object value, String what) throws GraphFormatException {
        if (!(value instanceof Double)) {
            throw new GraphFormatException("\"" + what + "\" must be a whole number.");
        }
        double d = (Double) value;
        if (d != Math.rint(d) || Math.abs(d) > Integer.MAX_VALUE) {
            throw new GraphFormatException("\"" + what + "\" must be a whole number.");
        }
        return (int) d;
    }
}

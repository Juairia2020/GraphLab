package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.io.GraphFormatException;
import org.junit.Test;

public class ExperimentRecordTest {
    private static Histogram sample() {
        Histogram h = new Histogram();
        h.add(2, 120);
        h.add(3, 380);
        return h;
    }

    @Test
    public void roundTrips() throws Exception {
        ExperimentRecord record = new ExperimentRecord("karger", 500, 500, 4242, sample());
        ExperimentRecord back = ExperimentRecord.parse(record.toJson());
        assertEquals("karger", back.algorithmId());
        assertEquals(500, back.planned());
        assertEquals(500, back.done());
        assertEquals(4242, back.seed());
        assertEquals(sample(), back.histogram());
        assertEquals(record.toJson(), back.toJson());
    }

    @Test
    public void summaryNamesTheBestCut() {
        assertEquals("500 trials, best cut 2 (found 120 times, 24%)",
                new ExperimentRecord("karger", 500, 500, 1, sample()).summary());
        Histogram one = new Histogram();
        one.add(4);
        assertEquals("1 trial, best cut 4 (found 1 time, 100%)", new ExperimentRecord("karger", 1, 1, 1, one).summary());
    }

    @Test
    public void aStoppedRunSaysSo() {
        Histogram h = new Histogram();
        h.add(2, 10);
        ExperimentRecord r = new ExperimentRecord("karger", 500, 10, 1, h);
        assertTrue(r.partial());
        assertTrue(r.summary(), r.summary().startsWith("10 trials of 500"));
    }

    @Test
    public void rejectsBrokenRecords() {
        assertThrows(GraphFormatException.class, () -> ExperimentRecord.parse("nope"));
        assertThrows(GraphFormatException.class, () -> ExperimentRecord.parse("{\"format\":\"x\"}"));
        String good = new ExperimentRecord("karger", 5, 5, 1, one(5)).toJson();
        assertThrows(GraphFormatException.class, () -> ExperimentRecord.parse(good.replace("\"done\":5", "\"done\":4")));
        assertThrows(GraphFormatException.class, () -> ExperimentRecord.parse(good.replace("\"version\":1", "\"version\":2")));
        assertThrows(GraphFormatException.class, () -> ExperimentRecord.parse(good.replace("\"count\":5", "\"count\":0")));
    }

    private static Histogram one(int times) {
        Histogram h = new Histogram();
        h.add(2, times);
        return h;
    }

    @Test
    public void hugeSeedsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRecord("karger", 1, 1, Long.MAX_VALUE, one(1)));
        assertFalse(new ExperimentRecord("karger", 1, 1, ExperimentRecord.MAX_SEED - 1, one(1)).partial());
    }
}

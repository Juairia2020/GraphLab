package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.SampleGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ExperimentRunnerTest {
    private static KargerTrials trials() {
        return new KargerTrials(SampleGraph.sixNodeUndirected());
    }

    @Test
    public void runsAllTrialsAndReportsEveryTwentyAndAtTheEnd() {
        List<Integer> reported = new ArrayList<>();
        ExperimentRunner.Outcome out = ExperimentRunner.run(trials(), 5, 50, new PauseGate(), 20, 0,
                (done, total, h) -> {
                    reported.add(done);
                    assertEquals(50, total);
                    assertEquals(done, h.total());
                });
        assertEquals(50, out.done());
        assertFalse(out.cancelled());
        assertEquals(50, out.histogram().total());
        assertEquals(List.of(20, 40, 50), reported);
    }

    @Test
    public void fiveHundredTrialsReportOneHundredPercent() {
        int[] last = new int[1];
        ExperimentRunner.Outcome out = ExperimentRunner.run(trials(), 1, 500, new PauseGate(),
                TrialCount.REPORT_EVERY, 0, (done, total, h) -> last[0] = done);
        assertEquals(500, last[0]);
        assertEquals(500, out.done());
        assertEquals(2, out.histogram().smallest());
    }

    @Test
    public void sameSeedGivesTheSameHistogram() {
        Histogram a = ExperimentRunner.run(trials(), 11, 200, new PauseGate(), 20, 0, null).histogram();
        Histogram b = ExperimentRunner.run(trials(), 11, 200, new PauseGate(), 20, 0, null).histogram();
        assertEquals(a, b);
    }

    @Test
    public void cancelStopsEarlyAndKeepsWhatWasDone() {
        PauseGate gate = new PauseGate();
        ExperimentRunner.Outcome out = ExperimentRunner.run(trials(), 3, 1000, gate, 1, 0, (done, total, h) -> {
            if (done == 30) {
                gate.cancel();
            }
        });
        assertTrue(out.cancelled());
        assertEquals(30, out.done());
        assertEquals(30, out.histogram().total());
    }

    @Test
    public void aPausedRunWaitsAndThenFinishes() throws Exception {
        PauseGate gate = new PauseGate();
        int[] progress = new int[1];
        ExperimentRunner.Outcome[] result = new ExperimentRunner.Outcome[1];
        Thread t = new Thread(() -> result[0] = ExperimentRunner.run(trials(), 3, 100, gate, 1, 2, (done, total, h) -> {
            progress[0] = done;
            if (done == 10) {
                gate.pause(PauseGate.Reason.BATTERY);
            }
        }));
        t.start();
        Thread.sleep(300);
        int whilePaused = progress[0];
        assertEquals("the run must stop at the pause", 10, whilePaused);
        Thread.sleep(100);
        assertEquals(whilePaused, progress[0]);
        assertTrue(gate.resume(PauseGate.Reason.BATTERY));
        t.join(5000);
        assertFalse(t.isAlive());
        assertEquals(100, result[0].done());
        assertFalse(result[0].cancelled());
    }

    @Test
    public void refusesZeroTrials() {
        assertThrows(IllegalArgumentException.class, () -> ExperimentRunner.run(trials(), 0, 0, new PauseGate(), 1, 0, null));
    }
}

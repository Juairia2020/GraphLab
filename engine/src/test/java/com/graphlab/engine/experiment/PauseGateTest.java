package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class PauseGateTest {
    @Test
    public void runsFreelyWhenNotPaused() {
        PauseGate gate = new PauseGate();
        assertTrue(gate.awaitRunnable());
        assertFalse(gate.isPaused());
        assertNull(gate.reason());
    }

    @Test
    public void batteryOkayLiftsOnlyABatteryPause() {
        PauseGate gate = new PauseGate();
        gate.pause(PauseGate.Reason.BATTERY);
        assertEquals(PauseGate.Reason.BATTERY, gate.reason());
        assertTrue(gate.resume(PauseGate.Reason.BATTERY));
        assertFalse(gate.isPaused());

        gate.pause(PauseGate.Reason.USER);
        assertFalse("battery okay must not undo the user's pause", gate.resume(PauseGate.Reason.BATTERY));
        assertTrue(gate.isPaused());
        assertTrue(gate.resume(PauseGate.Reason.USER));
    }

    @Test
    public void aUserPauseTakesOverFromABatteryPause() {
        PauseGate gate = new PauseGate();
        gate.pause(PauseGate.Reason.BATTERY);
        gate.pause(PauseGate.Reason.USER);
        assertEquals(PauseGate.Reason.USER, gate.reason());
        gate.pause(PauseGate.Reason.BATTERY);
        assertEquals(PauseGate.Reason.USER, gate.reason());
    }

    @Test
    public void userResumeLiftsABatteryPause() {
        PauseGate gate = new PauseGate();
        gate.pause(PauseGate.Reason.BATTERY);
        assertTrue(gate.resume(PauseGate.Reason.USER));
    }

    @Test
    public void aWaitingThreadWakesOnResume() throws Exception {
        PauseGate gate = new PauseGate();
        gate.pause(PauseGate.Reason.USER);
        AtomicBoolean result = new AtomicBoolean(false);
        Thread t = new Thread(() -> result.set(gate.awaitRunnable()));
        t.start();
        Thread.sleep(50);
        assertTrue(t.isAlive());
        gate.resume(PauseGate.Reason.USER);
        t.join(2000);
        assertFalse(t.isAlive());
        assertTrue(result.get());
    }

    @Test
    public void cancelWakesAndStopsAWaitingThread() throws Exception {
        PauseGate gate = new PauseGate();
        gate.pause(PauseGate.Reason.USER);
        AtomicBoolean result = new AtomicBoolean(true);
        Thread t = new Thread(() -> result.set(gate.awaitRunnable()));
        t.start();
        Thread.sleep(50);
        gate.cancel();
        t.join(2000);
        assertFalse(t.isAlive());
        assertFalse(result.get());
        assertTrue(gate.isCancelled());
    }
}

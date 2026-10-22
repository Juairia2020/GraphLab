package com.graphlab.engine.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackTest {

    @Test
    public void startsPausedAtStepZero() {
        Playback p = new Playback(5);
        assertEquals(0, p.index());
        assertTrue(p.atStart());
        assertFalse(p.isPlaying());
        assertEquals(1.0, p.speed(), 1e-9);
        assertEquals(1000, p.delayMillis());
    }

    @Test
    public void needsAtLeastOneStep() {
        assertThrows(IllegalArgumentException.class, () -> new Playback(0));
    }

    @Test
    public void stepsForwardAndBackAndStopsAtTheEnds() {
        Playback p = new Playback(3);
        assertFalse(p.previous());
        assertTrue(p.next());
        assertTrue(p.next());
        assertTrue(p.atEnd());
        assertFalse(p.next());
        assertEquals(2, p.index());
        assertTrue(p.previous());
        assertEquals(1, p.index());
    }

    @Test
    public void seekClampsAndReportsChange() {
        Playback p = new Playback(10);
        assertTrue(p.seek(4));
        assertFalse(p.seek(4));
        assertTrue(p.seek(99));
        assertEquals(9, p.index());
        assertTrue(p.seek(-5));
        assertEquals(0, p.index());
        assertTrue(p.toLast());
        assertEquals(9, p.index());
        assertTrue(p.toFirst());
        assertEquals(0, p.index());
    }

    @Test
    public void manualMovesPausePlayback() {
        Playback p = new Playback(10);
        p.togglePlay();
        assertTrue(p.isPlaying());
        p.next();
        assertFalse(p.isPlaying());
        p.togglePlay();
        p.previous();
        assertFalse(p.isPlaying());
        p.togglePlay();
        p.seek(5);
        assertFalse(p.isPlaying());
        p.togglePlay();
        p.toLast();
        assertFalse(p.isPlaying());
    }

    @Test
    public void tickAdvancesWhilePlayingAndStopsAtTheEnd() {
        Playback p = new Playback(3);
        assertFalse(p.tick());          // paused: nothing happens
        assertEquals(0, p.index());
        p.togglePlay();
        assertTrue(p.tick());           // 0 -> 1, keep going
        assertEquals(1, p.index());
        assertFalse(p.tick());          // 1 -> 2, that was the last one: stop
        assertEquals(2, p.index());
        assertFalse(p.isPlaying());
        assertFalse(p.tick());          // already stopped
        assertEquals(2, p.index());
    }

    @Test
    public void playOnTheLastStepRestartsFromTheBeginning() {
        Playback p = new Playback(4);
        p.toLast();
        assertTrue(p.togglePlay());     // the picture changed
        assertEquals(0, p.index());
        assertTrue(p.isPlaying());
    }

    @Test
    public void toggleWhilePlayingPausesWithoutMoving() {
        Playback p = new Playback(6);
        p.togglePlay();
        p.tick();
        assertFalse(p.togglePlay());
        assertFalse(p.isPlaying());
        assertEquals(1, p.index());
    }

    @Test
    public void aOneStepTraceCanBePlayedWithoutLooping() {
        Playback p = new Playback(1);
        assertTrue(p.atStart() && p.atEnd());
        assertFalse(p.togglePlay());    // nothing to restart
        assertTrue(p.isPlaying());
        assertFalse(p.tick());          // stops straight away
        assertFalse(p.isPlaying());
        assertEquals(0, p.index());
    }

    @Test
    public void speedIsClampedAndChangesTheDelay() {
        Playback p = new Playback(5);
        p.setSpeed(2.0);
        assertEquals(500, p.delayMillis());
        p.setSpeed(4.0);
        assertEquals(250, p.delayMillis());
        p.setSpeed(0.5);
        assertEquals(2000, p.delayMillis());
        p.setSpeed(100);
        assertEquals(Playback.MAX_SPEED, p.speed(), 1e-9);
        p.setSpeed(-3);
        assertEquals(Playback.MIN_SPEED, p.speed(), 1e-9);
        p.setSpeed(Double.NaN);                       // ignored
        assertEquals(Playback.MIN_SPEED, p.speed(), 1e-9);
    }

    @Test
    public void changingSpeedKeepsPlayingAndPosition() {
        Playback p = new Playback(8);
        p.togglePlay();
        p.tick();
        p.setSpeed(3);
        assertTrue(p.isPlaying());
        assertEquals(1, p.index());
    }

    @Test
    public void aFullPlayThroughVisitsEveryStepOnce() {
        Playback p = new Playback(7);
        p.togglePlay();
        int seen = 1;                    // step 0 is on screen when play starts
        while (p.tick()) {
            seen++;
        }
        seen++;                          // the tick that returned false still moved to the last step
        assertEquals(7, seen);
        assertEquals(6, p.index());
    }
}

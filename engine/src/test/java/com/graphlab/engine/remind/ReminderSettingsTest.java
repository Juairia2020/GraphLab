package com.graphlab.engine.remind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReminderSettingsTest {
    @Test
    public void startsOffAtSixInTheEvening() {
        ReminderSettings s = ReminderSettings.off();
        assertFalse(s.enabled());
        assertEquals(18, s.hour());
        assertEquals(0, s.minute());
    }

    @Test
    public void encodesAndDecodesEverySetting() {
        for (boolean on : new boolean[] {false, true}) {
            for (int h = 0; h < 24; h++) {
                for (int m = 0; m < 60; m += 7) {
                    ReminderSettings s = ReminderSettings.of(on, h, m);
                    assertEquals(s, ReminderSettings.decode(s.encode()));
                }
            }
        }
        assertEquals("1,18,30", ReminderSettings.of(true, 18, 30).encode());
    }

    @Test
    public void damagedTextGivesOffNeverAnException() {
        for (String bad : new String[] {null, "", "x", "1,2", "1,2,3,4", "2,10,10", "1,24,0", "1,10,60", "1,-1,5", "1,a,b", ",,", "yes,1,1"}) {
            assertEquals("for " + bad, ReminderSettings.off(), ReminderSettings.decode(bad));
        }
        assertEquals(ReminderSettings.of(true, 7, 5), ReminderSettings.decode(" 1 , 7 , 5 "));
    }

    @Test
    public void refusesImpossibleTimes() {
        assertThrows(IllegalArgumentException.class, () -> ReminderSettings.of(true, 24, 0));
        assertThrows(IllegalArgumentException.class, () -> ReminderSettings.of(true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> ReminderSettings.of(true, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> ReminderSettings.off().withTime(9, 99));
    }

    @Test
    public void withersGiveNewObjects() {
        ReminderSettings off = ReminderSettings.off();
        ReminderSettings on = off.withEnabled(true).withTime(7, 45);
        assertTrue(on.enabled());
        assertEquals(7, on.hour());
        assertEquals(45, on.minute());
        assertFalse(off.enabled());
        assertEquals(on.hashCode(), ReminderSettings.of(true, 7, 45).hashCode());
    }
}

package com.graphlab.engine.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class TrialCountTest {
    @Test
    public void readsNumbers() {
        assertEquals(500, TrialCount.parse("500"));
        assertEquals(1, TrialCount.parse(" 1 "));
        assertEquals(TrialCount.MAX, TrialCount.parse("20000"));
    }

    @Test
    public void refusesEverythingElseWithAMessage() {
        for (String bad : new String[] {"", "  ", null, "abc", "1.5", "0", "-3", "20001", "99999999999999999999"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TrialCount.parse(bad));
            assertEquals(false, e.getMessage().isEmpty());
        }
    }
}

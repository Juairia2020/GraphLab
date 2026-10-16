package com.graphlab.engine.effects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EffectsSettingsTest {
    @Test
    public void defaultsHaveEverythingOn() {
        EffectsSettings s = EffectsSettings.defaults();
        assertTrue(s.glowActive(false) && s.soundActive(false) && s.hapticsActive(false));
    }

    @Test
    public void reduceEffectsSwitchesAllOffButKeepsChoices() {
        EffectsSettings s = EffectsSettings.defaults().withSound(false).withReduceEffects(true);
        assertFalse(s.glowActive(false) || s.soundActive(false) || s.hapticsActive(false));
        EffectsSettings back = s.withReduceEffects(false);
        assertTrue(back.glowActive(false));
        assertFalse(back.soundActive(false));
        assertTrue(back.hapticsActive(false));
    }

    @Test
    public void systemReduceMotionActsLikeTheMasterSwitch() {
        EffectsSettings s = EffectsSettings.defaults();
        assertFalse(s.glowActive(true) || s.soundActive(true) || s.hapticsActive(true));
    }

    @Test
    public void individualSwitchesAreIndependent() {
        EffectsSettings s = EffectsSettings.defaults().withGlow(false);
        assertFalse(s.glowActive(false));
        assertTrue(s.soundActive(false) && s.hapticsActive(false));
        assertFalse(EffectsSettings.defaults().withHaptics(false).hapticsActive(false));
        assertFalse(EffectsSettings.defaults().withSound(false).soundActive(false));
    }

    @Test
    public void encodeDecodeRoundTripsAllSixteen() {
        for (int m = 0; m < 16; m++) {
            EffectsSettings s = new EffectsSettings((m & 1) != 0, (m & 2) != 0, (m & 4) != 0, (m & 8) != 0);
            assertEquals(s, EffectsSettings.decode(s.encode()));
        }
    }

    @Test
    public void badTextGivesDefaults() {
        for (String bad : new String[] {null, "", "111", "11111", "1x01", "abcd"}) {
            assertEquals(EffectsSettings.defaults(), EffectsSettings.decode(bad));
        }
    }
}

package com.graphlab.engine.effects;

/**
 * The visual and sensory effects the learner may switch on or off: glow, sound and haptics.
 * A master switch, "reduce effects", turns all three off without forgetting the stored choices.
 * The system "reduce motion" setting counts as the master switch being on. Immutable.
 */
public record EffectsSettings(boolean glow, boolean sound, boolean haptics, boolean reduceEffects) {

    /** Everything on, reduce effects off. */
    public static EffectsSettings defaults() {
        return new EffectsSettings(true, true, true, false);
    }

    public EffectsSettings withGlow(boolean on) {
        return new EffectsSettings(on, sound, haptics, reduceEffects);
    }

    public EffectsSettings withSound(boolean on) {
        return new EffectsSettings(glow, on, haptics, reduceEffects);
    }

    public EffectsSettings withHaptics(boolean on) {
        return new EffectsSettings(glow, sound, on, reduceEffects);
    }

    public EffectsSettings withReduceEffects(boolean on) {
        return new EffectsSettings(glow, sound, haptics, on);
    }

    private boolean reduced(boolean systemReduceMotion) {
        return reduceEffects || systemReduceMotion;
    }

    public boolean glowActive(boolean systemReduceMotion) {
        return glow && !reduced(systemReduceMotion);
    }

    public boolean soundActive(boolean systemReduceMotion) {
        return sound && !reduced(systemReduceMotion);
    }

    public boolean hapticsActive(boolean systemReduceMotion) {
        return haptics && !reduced(systemReduceMotion);
    }

    /** Four characters of 0 and 1: glow, sound, haptics, reduce. */
    public String encode() {
        return bit(glow) + bit(sound) + bit(haptics) + bit(reduceEffects);
    }

    /** Reads {@link #encode()}; anything malformed gives the defaults. */
    public static EffectsSettings decode(String text) {
        if (text == null || text.length() != 4) {
            return defaults();
        }
        for (int i = 0; i < 4; i++) {
            char c = text.charAt(i);
            if (c != '0' && c != '1') {
                return defaults();
            }
        }
        return new EffectsSettings(text.charAt(0) == '1', text.charAt(1) == '1',
                text.charAt(2) == '1', text.charAt(3) == '1');
    }

    private static String bit(boolean b) {
        return b ? "1" : "0";
    }
}

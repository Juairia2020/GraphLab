package com.graphlab.data.prefs;

import static org.junit.Assert.assertEquals;

import androidx.appcompat.app.AppCompatDelegate;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class ThemeModeTest {

    @Test
    public void fromKeyRoundTripsEveryMode() {
        for (ThemeMode mode : ThemeMode.values()) {
            assertEquals(mode, ThemeMode.fromKey(mode.key));
        }
    }

    @Test
    public void fromKeyFallsBackToSystemForNullEmptyAndUnknown() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(""));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("sepia"));
    }

    @Test
    public void defaultIsSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT);
    }

    @Test
    public void storedKeysAreUniqueAndStable() {
        Set<String> keys = new HashSet<>();
        for (ThemeMode mode : ThemeMode.values()) {
            keys.add(mode.key);
        }
        assertEquals(ThemeMode.values().length, keys.size());
        assertEquals("system", ThemeMode.SYSTEM.key);
        assertEquals("light", ThemeMode.LIGHT.key);
        assertEquals("dark", ThemeMode.DARK.key);
    }

    @Test
    public void nightModesMatchAppCompatConstants() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.nightMode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.nightMode);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.nightMode);
    }
}

package com.graphlab.engine;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.Test;

public class EngineInfoTest {
    @Test
    public void versionLooksLikeSemver() {
        Pattern semver = Pattern.compile("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?");
        assertTrue("Unexpected version: " + EngineInfo.VERSION, semver.matcher(EngineInfo.VERSION).matches());
    }
}

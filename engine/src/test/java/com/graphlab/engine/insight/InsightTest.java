package com.graphlab.engine.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.trace.Algorithm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Test;

public class InsightTest {
    /** The app's insight pages, found from the engine module's folder. */
    private static final Path DIR = Path.of("..", "app", "src", "main", "assets", "insight");

    @Test
    public void everyAlgorithmHasAValidInsightPageAndNoPageIsOrphaned() throws Exception {
        if (!Files.isDirectory(DIR)) {
            fail("Cannot find " + DIR.toAbsolutePath() + ". Run the tests from the engine module (Gradle does).");
        }
        for (Algorithm a : Algorithms.all()) {
            Path file = DIR.resolve(a.id() + ".json");
            assertTrue("missing insight page for " + a.id(), Files.exists(file));
            Insight insight = Insight.parse(Files.readString(file, StandardCharsets.UTF_8));
            assertEquals(a.id(), insight.id());
            assertTrue(a.id() + " idea too short", insight.idea().length() > 60);
        }
        try (Stream<Path> files = Files.list(DIR)) {
            assertEquals(Algorithms.all().size(), files.filter(f -> f.toString().endsWith(".json")).count());
        } catch (IOException e) {
            throw e;
        }
    }

    @Test
    public void parseRefusesIncompletePages() {
        assertThrows(GraphFormatException.class, () -> Insight.parse("[]"));
        assertThrows(GraphFormatException.class, () -> Insight.parse("{\"id\":\"x\"}"));
        assertThrows(GraphFormatException.class,
                () -> Insight.parse("{\"id\":\"x\",\"idea\":\"i\",\"time\":\"t\",\"space\":\"s\",\"useWhen\":[],\"avoidWhen\":[\"a\"]}"));
        assertThrows(GraphFormatException.class,
                () -> Insight.parse("{\"id\":\"x\",\"idea\":\"i\",\"time\":\"t\",\"space\":\"s\",\"useWhen\":[\"u\"],\"avoidWhen\":[5]}"));
    }
}

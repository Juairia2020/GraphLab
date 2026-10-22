package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class GraphNamesTest {

    @Test
    public void cleanTrimsAndCollapsesWhitespace() {
        assertEquals("My graph", GraphNames.clean("  My \t graph \n"));
        assertEquals("a b", GraphNames.clean("a\u0000\u0007b"));
        assertEquals("", GraphNames.clean("   "));
        assertEquals("", GraphNames.clean(null));
    }

    @Test
    public void cleanCutsToTheLimitWithoutSplittingAnEmoji() {
        String long50 = "x".repeat(50);
        assertEquals(40, GraphNames.clean(long50).length());
        String emoji = "y".repeat(39) + "\uD83D\uDE00";
        String cut = GraphNames.clean(emoji);
        assertTrue(cut.length() <= 40);
        assertTrue(cut.isEmpty() || !Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
    }

    @Test
    public void checkTellsEmptyFromTooLongFromFine() {
        assertEquals(GraphNames.Check.EMPTY, GraphNames.check(""));
        assertEquals(GraphNames.Check.EMPTY, GraphNames.check("   \n"));
        assertEquals(GraphNames.Check.OK, GraphNames.check("Campus roads"));
        assertEquals(GraphNames.Check.OK, GraphNames.check("x".repeat(40)));
        assertEquals(GraphNames.Check.TOO_LONG, GraphNames.check("x".repeat(41)));
        assertEquals(GraphNames.Check.OK, GraphNames.check("  " + "x".repeat(40) + "  "));   // spaces do not count
        assertEquals(GraphNames.Check.EMPTY, GraphNames.check(null));
    }

    @Test
    public void uniqueAddsANumberAndIgnoresCase() {
        assertEquals("Roads", GraphNames.unique("Roads", List.of()));
        assertEquals("Roads (2)", GraphNames.unique("Roads", List.of("roads")));
        assertEquals("Roads (3)", GraphNames.unique("Roads", List.of("Roads", "Roads (2)")));
        assertEquals("Untitled graph", GraphNames.untitled(List.of("Other")));
        assertEquals("Untitled graph (2)", GraphNames.untitled(List.of("Untitled graph")));
    }

    @Test
    public void copyNamesCountUp() {
        assertEquals("Roads (copy)", GraphNames.copyOf("Roads", List.of("Roads")));
        assertEquals("Roads (copy 2)", GraphNames.copyOf("Roads", List.of("Roads", "Roads (copy)")));
        assertEquals("Roads (copy 3)", GraphNames.copyOf("Roads", List.of("Roads (copy)", "Roads (copy 2)")));
    }

    @Test
    public void suffixesNeverPushANameOverTheLimit() {
        String longName = "n".repeat(40);
        assertTrue(GraphNames.copyOf(longName, List.of()).length() <= 40);
        assertTrue(GraphNames.unique(longName, List.of(longName)).length() <= 40);
        assertTrue(GraphNames.copyOf(longName, List.of(GraphNames.copyOf(longName, List.of()))).length() <= 40);
    }
}

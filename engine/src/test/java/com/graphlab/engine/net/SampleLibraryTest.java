package com.graphlab.engine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.io.GraphFormatException;
import java.util.List;
import org.junit.Test;

public class SampleLibraryTest {
    private static String graph(String name) {
        return "{\"format\":\"graphlab-graph\",\"version\":1,\"name\":\"" + name + "\",\"directed\":false,\"weighted\":true,"
                + "\"nodes\":[{\"id\":0,\"label\":\"A\",\"x\":0,\"y\":0},{\"id\":1,\"label\":\"B\",\"x\":50,\"y\":0}],"
                + "\"edges\":[{\"id\":0,\"from\":0,\"to\":1,\"weight\":3}]}";
    }

    private static String entry(String id, String title, String description) {
        return "{\"id\":\"" + id + "\",\"title\":\"" + title + "\",\"description\":\"" + description + "\",\"graph\":" + graph("x") + "}";
    }

    private static String library(String... entries) {
        return "{\"format\":\"graphlab-library\",\"version\":1,\"graphs\":[" + String.join(",", entries) + "]}";
    }

    private static String message(String text) {
        return assertThrows(GraphFormatException.class, () -> SampleLibrary.parse(text)).getMessage();
    }

    @Test
    public void readsAValidLibrary() throws Exception {
        List<SampleLibrary.Entry> list = SampleLibrary.parse(library(entry("one", "First", "A tiny graph"), entry("two-2", "Second", "")));
        assertEquals(2, list.size());
        assertEquals("one", list.get(0).id());
        assertEquals("First", list.get(0).title());
        assertEquals("A tiny graph", list.get(0).description());
        assertEquals(2, list.get(0).document().graph().nodeCount());
        assertEquals(1, list.get(0).document().graph().edgeCount());
        assertEquals("The document takes the entry's title", "First", list.get(0).document().name());
        assertEquals("", list.get(1).description());
    }

    @Test
    public void theDescriptionIsOptional() throws Exception {
        String noDescription = "{\"id\":\"a\",\"title\":\"T\",\"graph\":" + graph("x") + "}";
        assertEquals("", SampleLibrary.parse(library(noDescription)).get(0).description());
    }

    @Test
    public void theReturnedListCannotBeChanged() throws Exception {
        List<SampleLibrary.Entry> list = SampleLibrary.parse(library(entry("a", "A", "")));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
    }

    @Test
    public void rejectsEmptyAndOversizedText() {
        assertTrue(message("").contains("empty"));
        assertTrue(message(null).contains("empty"));
        assertTrue(message("x".repeat(SampleLibrary.MAX_TEXT_CHARS + 1)).contains("too large"));
    }

    @Test
    public void rejectsTheWrongShape() {
        assertTrue(message("[1,2]").contains("JSON object"));
        assertTrue(message("{\"format\":\"something-else\",\"version\":1,\"graphs\":[]}").contains("sample library"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"graphs\":[]}").contains("version"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":1.5,\"graphs\":[]}").contains("whole number"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":0,\"graphs\":[]}").contains("1 or more"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":2,\"graphs\":[]}").contains("newer"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":1}").contains("\"graphs\""));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":1,\"graphs\":[]}").contains("no graphs"));
        assertTrue(message("{\"format\":\"graphlab-library\",\"version\":1,\"graphs\":[3]}").contains("Sample 1"));
        assertFalse(message("not json at all").isEmpty());
    }

    @Test
    public void rejectsTooManyEntries() {
        String[] many = new String[SampleLibrary.MAX_ENTRIES + 1];
        for (int i = 0; i < many.length; i++) {
            many[i] = entry("g" + i, "G" + i, "");
        }
        assertTrue(message(library(many)).contains("most GraphLab reads"));
    }

    @Test
    public void checksIds() {
        assertTrue(message(library(entry("Bad Id", "T", ""))).contains("lower case"));
        assertTrue(message(library(entry("-lead", "T", ""))).contains("lower case"));
        assertTrue(message(library(entry("a".repeat(41), "T", ""))).contains("longer than 40"));
        String twice = message(library(entry("same", "A", ""), entry("same", "B", "")));
        assertTrue(twice, twice.contains("used twice") && twice.contains("Sample 2"));
        assertTrue(message(library("{\"title\":\"T\",\"graph\":" + graph("x") + "}")).contains("\"id\""));
    }

    @Test
    public void checksTitlesAndDescriptions() {
        assertTrue(message(library(entry("a", "", ""))).contains("\"title\""));
        assertTrue(message(library(entry("a", "T".repeat(SampleLibrary.MAX_TITLE + 1), ""))).contains("longer than"));
        assertTrue(message(library(entry("a", "T", "d".repeat(SampleLibrary.MAX_DESCRIPTION + 1)))).contains("\"description\""));
    }

    @Test
    public void aBadGraphNamesTheSample() {
        String broken = "{\"id\":\"broken\",\"title\":\"T\",\"graph\":{\"format\":\"graphlab-graph\",\"version\":1,"
                + "\"nodes\":[{\"id\":0}],\"edges\":[{\"id\":0,\"from\":0,\"to\":9}]}}";
        String text = message(library(entry("fine", "F", ""), broken));
        assertTrue(text, text.contains("Sample 2 (broken)"));
        assertTrue(message(library("{\"id\":\"nograph\",\"title\":\"T\"}")).contains("no \"graph\""));
        assertTrue(message(library("{\"id\":\"nograph\",\"title\":\"T\",\"graph\":[1]}")).contains("nograph"));
    }
}

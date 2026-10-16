package com.graphlab.engine.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RunReportTest {
    @Test
    public void keyFramesAreFirstLastIncreasingAndAtMostMax() {
        for (int total = 1; total <= 200; total++) {
            for (int max = 1; max <= 30; max++) {
                List<Integer> k = RunReport.keyFrames(total, max);
                assertTrue(k.size() <= max);
                assertEquals(Math.min(total, max), k.size());
                assertEquals(total - 1, (int) k.get(k.size() - 1));
                if (max > 1) {
                    assertEquals(0, (int) k.get(0));
                }
                for (int i = 1; i < k.size(); i++) {
                    assertTrue(k.get(i) > k.get(i - 1));
                }
            }
        }
    }

    @Test
    public void keyFramesAreEvenlySpread() {
        List<Integer> k = RunReport.keyFrames(101, 11);
        for (int i = 0; i < k.size(); i++) {
            assertEquals(i * 10, (int) k.get(i));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void keyFramesRejectZero() {
        RunReport.keyFrames(0, 5);
    }

    @Test
    public void wrapKeepsEveryWordAndRespectsWidth() {
        String text = "the quick brown fox jumps over the lazy dog extraordinarilylongwordhere end";
        for (int width = 1; width <= 30; width++) {
            List<String> lines = RunReport.wrap(text, width);
            StringBuilder joined = new StringBuilder();
            for (String l : lines) {
                assertTrue(l.length() <= width);
                joined.append(l);
            }
            assertEquals(text.replace(" ", ""), joined.toString().replace(" ", ""));
        }
    }

    @Test
    public void wrapKeepsParagraphBreaks() {
        assertEquals(List.of("ab cd", "", "ef"), RunReport.wrap("ab cd\n\nef", 10));
        assertEquals(List.of("ab", "cd"), RunReport.wrap("ab cd", 4));
    }

    @Test
    public void paginateSplitsWithoutLosingLines() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            lines.add("l" + i);
        }
        List<List<String>> pages = RunReport.paginate(lines, 10);
        assertEquals(3, pages.size());
        assertEquals(3, pages.get(2).size());
        List<String> back = new ArrayList<>();
        pages.forEach(back::addAll);
        assertEquals(lines, back);
        assertEquals(1, RunReport.paginate(List.of(), 5).size());
    }
}

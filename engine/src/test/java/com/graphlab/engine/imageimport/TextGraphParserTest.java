package com.graphlab.engine.imageimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class TextGraphParserTest {
    @Test
    public void plainLinesWithAndWithoutWeights() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nB C 2.5\n");
        assertEquals(3, g.nodes().size());
        assertEquals(2, g.edges().size());
        assertEquals(2.5, g.edges().get(1).weight(), 0);
        assertTrue(g.weighted());
        assertFalse(g.directed());
        assertTrue(new ReviewSession(g).flags().isEmpty());
        DetectedGraph u = TextGraphParser.parse("A B\nB C\n");
        assertFalse(u.weighted());
        assertEquals(1.0, u.edges().get(0).weight(), 0);
    }

    @Test
    public void variousSeparators() {
        DetectedGraph g = TextGraphParser.parse("A-B:4\nB -> C (7)\nC,D,1\nD—E 3\n");
        assertEquals(5, g.nodes().size());
        assertEquals(4, g.edges().size());
        assertTrue(g.directed());
        assertEquals(7.0, g.edges().get(1).weight(), 0);
        assertEquals(1.0, g.edges().get(2).weight(), 0);
    }

    @Test
    public void mixedArrowsAndPlainLinesAreFlagged() {
        DetectedGraph g = TextGraphParser.parse("A -> B 1\nB C 2\n");
        assertTrue(g.directedConfidence() < DetectedGraph.REVIEW_BELOW);
    }

    @Test
    public void commentsBlankLinesAndNegativeWeights() {
        DetectedGraph g = TextGraphParser.parse("# my graph\n\nA B -3 # note\n");
        assertEquals(-3.0, g.edges().get(0).weight(), 0);
    }

    @Test
    public void badLinesBecomeWarningsAndReadingContinues() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nhello\nA A 1\nC D 1 2 3\nB C 2\n");
        assertEquals(2, g.edges().size());
        assertEquals(3, g.warnings().size());
        assertFalse(new ReviewSession(g).flags().isEmpty());
    }

    @Test
    public void repairedWeightIsFlagged() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nB C l2\nC D 5\n".replace("l2", "I2"));
        DetectedGraph.Edge e = g.edges().get(1);
        assertEquals(12.0, e.weight(), 0);
        assertTrue(e.weightConfidence() < DetectedGraph.REVIEW_BELOW);
    }

    @Test
    public void nonNumberWeightKeepsEdgeButFlagsIt() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nB C xx\n");
        assertNull(g.edges().get(1).weight());
        assertTrue(new ReviewSession(g).flags().stream().anyMatch(f -> f.kind() == ReviewSession.Kind.WEIGHT));
    }

    @Test
    public void missingWeightInAWeightedListIsFlagged() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nB C\n");
        assertNull(g.edges().get(1).weight());
        assertFalse(new ReviewSession(g).flags().isEmpty());
    }

    @Test
    public void duplicatePairIsWarned() {
        DetectedGraph g = TextGraphParser.parse("A B 4\nB A 5\n");
        assertEquals(1, g.edges().size());
        assertEquals(1, g.warnings().size());
    }

    @Test
    public void ocrConfidenceCarriesIntoWeights() {
        DetectedGraph g = TextGraphParser.parse(List.of(new TextGraphParser.Line("A B 4", 0.5), new TextGraphParser.Line("B C 2", 0.95)));
        assertEquals(0.5, g.edges().get(0).weightConfidence(), 1e-9);
        assertEquals(1, new ReviewSession(g).flags().size());
    }
}

package com.graphlab.engine.imageimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class MatrixParserTest {
    private static Set<String> edgeSet(DetectedGraph g) {
        Set<String> out = new TreeSet<>();
        for (DetectedGraph.Edge e : g.edges()) {
            String a = g.nodes().get(e.from()).label();
            String b = g.nodes().get(e.to()).label();
            if (!g.directed() && a.compareTo(b) > 0) {
                String s = a;
                a = b;
                b = s;
            }
            out.add(a + (g.directed() ? ">" : "-") + b + ":" + e.weight());
        }
        return out;
    }

    private static boolean flagged(DetectedGraph g) {
        return !new ReviewSession(g).flags().isEmpty();
    }

    @Test
    public void cleanWeightedUndirectedWithNames() {
        DetectedGraph g = MatrixParser.parseText("  A B C\nA 0 4 2\nB 4 0 0\nC 2 0 0\n");
        assertFalse(g.directed());
        assertTrue(g.weighted());
        assertEquals(2, g.edges().size());
        assertEquals(new TreeSet<>(List.of("A-B:4.0", "A-C:2.0")), edgeSet(g));
        assertEquals("B", g.nodes().get(1).label());
        assertFalse(flagged(g));
    }

    @Test
    public void headerWithCornerCellAndRowLabels() {
        DetectedGraph g = MatrixParser.parseText("  X A B\nA 0 5\nB 5 0\n");
        assertEquals(2, g.nodes().size());
        assertEquals("A", g.nodes().get(0).label());
        assertEquals(1, g.edges().size());
    }

    @Test
    public void rowLabelsWithoutHeader() {
        DetectedGraph g = MatrixParser.parseText("P 0 1 0\nQ 1 0 1\nR 0 1 0\n");
        assertEquals("P", g.nodes().get(0).label());
        assertEquals("R", g.nodes().get(2).label());
        assertEquals(2, g.edges().size());
        assertFalse(g.weighted());
    }

    @Test
    public void noNamesGivesAandB() {
        DetectedGraph g = MatrixParser.parseText("0 3\n3 0\n");
        assertEquals("A", g.nodes().get(0).label());
        assertTrue(g.nodes().get(0).generatedLabel());
        assertFalse(flagged(g));
    }

    @Test
    public void zeroOneMatrixIsUnweighted() {
        DetectedGraph g = MatrixParser.parseText("0 1 1\n1 0 0\n1 0 0\n");
        assertFalse(g.weighted());
        for (DetectedGraph.Edge e : g.edges()) {
            assertEquals(1.0, e.weight(), 0);
        }
    }

    @Test
    public void asymmetricMeansDirected() {
        DetectedGraph g = MatrixParser.parseText("0 4 0\n0 0 2\n7 0 0\n");
        assertTrue(g.directed());
        assertEquals(new TreeSet<>(List.of("A>B:4.0", "B>C:2.0", "C>A:7.0")), edgeSet(g));
        assertFalse(flagged(g));
    }

    @Test
    public void nearlySymmetricDirectedIsFlaggedBecauseItMayBeAnOcrSlip() {
        // 5 symmetric pairs and one mismatch: more likely a misread than a directed graph
        String m = "0 1 1 1 1\n1 0 1 0 0\n1 1 0 0 0\n1 0 0 0 1\n1 0 0 0 0\n"; // (D,E) present but (E,D) is 0
        DetectedGraph g = MatrixParser.parseText(m);
        assertTrue(g.directed());
        assertTrue(g.directedConfidence() < DetectedGraph.REVIEW_BELOW);
        assertTrue(flagged(g));
    }

    @Test
    public void noEdgeMarks() {
        DetectedGraph g = MatrixParser.parseText("- 3 ∞\n3 - inf\n∞ inf -\n");
        assertEquals(1, g.edges().size());
        assertEquals(3.0, g.edges().get(0).weight(), 0);
        assertFalse(flagged(g));
    }

    @Test
    public void repairedDigitIsFlagged() {
        // the O in the lower-left should be 4 -> not repairable to a digit that matches; use O for 0 in an edge cell
        DetectedGraph g = MatrixParser.parseText("0 3 2\n3 0 O\n2 O 0\n");
        // O reads as 0, so no edge B-C; both mirrored cells are repaired, so the pair is consistent but doubtful
        assertEquals(1 + 1, g.edges().size());
        assertTrue(flagged(g));
        DetectedGraph h = MatrixParser.parseText("0 3 2\n3 0 5\n2 S 0\n");
        // S -> 5 repaired in one cell: the weight is flagged
        assertTrue(h.edges().stream().anyMatch(e -> e.weightConfidence() < DetectedGraph.REVIEW_BELOW));
        assertTrue(flagged(h));
    }

    @Test
    public void missingCellIsFlagged() {
        DetectedGraph g = MatrixParser.parseText("0 3 2\n3 0\n2 0 0\n");
        assertTrue(flagged(g));
    }

    @Test
    public void unreadableCellIsFlagged() {
        DetectedGraph g = MatrixParser.parseText("0 3 2\n3 0 #\n2 7 0\n");
        assertTrue(flagged(g));
    }

    @Test
    public void extraRowsOrColumnsAreFlagged() {
        DetectedGraph g = MatrixParser.parseText("0 1 1\n1 0 0\n"); // 2 rows, 3 columns
        assertEquals(3, g.nodes().size());
        assertTrue(flagged(g));
    }

    @Test
    public void nonZeroDiagonalWarns() {
        DetectedGraph g = MatrixParser.parseText("5 1\n1 0\n");
        assertTrue(flagged(g));
        assertEquals(1, g.edges().size());
    }

    @Test
    public void tokensAreGroupedIntoRowsByHeight() {
        List<OcrToken> t = new ArrayList<>();
        double[][] m = {{0, 4, 0}, {4, 0, 9}, {0, 9, 0}};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                // small jitter in y, columns out of order in the list
                t.add(OcrToken.at(String.valueOf((int) m[r][c]), 50 + c * 40, 100 + r * 30 + (c % 2) * 3, 0.95));
            }
        }
        java.util.Collections.reverse(t);
        DetectedGraph g = MatrixParser.parse(t);
        assertEquals(Set.of("A-B:4.0", "B-C:9.0"), edgeSet(g));
    }

    @Test
    public void emptyInputGivesWarning() {
        DetectedGraph g = MatrixParser.parseText("   \n");
        assertEquals(0, g.nodes().size());
        assertFalse(g.warnings().isEmpty());
        assertNull(null);
    }

    /** Render random graphs as matrices; the parser must give back exactly the same edges (independent reference). */
    @Test
    public void fuzzRoundTrip() {
        Random rnd = new Random(1234);
        for (int iter = 0; iter < 300; iter++) {
            int n = 2 + rnd.nextInt(7);
            boolean directed = rnd.nextBoolean();
            boolean weighted = rnd.nextBoolean();
            double[][] w = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i == j || (!directed && j < i)) {
                        continue;
                    }
                    if (rnd.nextInt(3) == 0) {
                        double v = weighted ? 1 + rnd.nextInt(20) : 1;
                        w[i][j] = v;
                        if (!directed) {
                            w[j][i] = v;
                        }
                    }
                }
            }
            if (directed) {
                // a directed matrix must be asymmetric somewhere for the parser to see direction
                boolean asym = false;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        asym |= w[i][j] != w[j][i];
                    }
                }
                if (!asym) {
                    continue;
                }
            }
            boolean named = rnd.nextBoolean();
            StringBuilder sb = new StringBuilder();
            if (named) {
                sb.append("  ");
                for (int j = 0; j < n; j++) {
                    sb.append((char) ('a' + j)).append(' ');
                }
                sb.append('\n');
            }
            for (int i = 0; i < n; i++) {
                if (named) {
                    sb.append((char) ('a' + i)).append(' ');
                }
                for (int j = 0; j < n; j++) {
                    sb.append((long) w[i][j]).append(' ');
                }
                sb.append('\n');
            }
            DetectedGraph g = MatrixParser.parseText(sb.toString());
            Set<String> expect = new TreeSet<>();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (w[i][j] == 0 || (!directed && j < i)) {
                        continue;
                    }
                    String a = named ? String.valueOf((char) ('a' + i)) : GraphLabels.of(i);
                    String b = named ? String.valueOf((char) ('a' + j)) : GraphLabels.of(j);
                    expect.add(a + (directed ? ">" : "-") + b + ":" + w[i][j]);
                }
            }
            assertEquals("iter " + iter + "\n" + sb, directed, g.directed());
            assertEquals("iter " + iter + "\n" + sb, expect, edgeSet(g));
            assertFalse("iter " + iter + " should not be flagged\n" + sb, flagged(g));
        }
    }
}

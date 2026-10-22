package com.graphlab.engine.imageimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.model.Node;
import java.util.List;
import org.junit.Test;

public class ReviewSessionTest {
    private static long count(ReviewSession s, ReviewSession.Kind k) {
        return s.flags().stream().filter(f -> f.kind() == k).count();
    }

    @Test
    public void demoHasFlagsOfEveryKindExceptDirection() {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        assertEquals(1, count(s, ReviewSession.Kind.NODE));
        assertEquals(1, count(s, ReviewSession.Kind.EDGE));
        assertEquals(3, count(s, ReviewSession.Kind.WEIGHT));
        assertEquals(1, count(s, ReviewSession.Kind.WARNING));
        assertFalse(s.isReady());
        assertFalse(s.problems().isEmpty()); // one weight is missing
    }

    @Test
    public void fixingEachFlagEmptiesTheList() throws Exception {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.renameNode(3, "D");
        s.confirmEdge(3);
        s.setWeight(2, 2);
        s.setWeight(4, 9);
        s.confirmWeight(6);
        s.dismissWarning(0);
        assertEquals(List.of(), s.flags());
        assertEquals(List.of(), s.problems());
        assertTrue(s.isReady());
        GraphDocument d = s.build("From photo");
        assertEquals(6, d.graph().nodeCount());
        assertEquals(7, d.graph().edgeCount());
        assertTrue(d.weighted());
        assertEquals(9.0, d.graph().edges().get(4).weight(), 0);
    }

    @Test
    public void aMissingWeightCannotBeAccepted() {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        try {
            s.confirmWeight(4);
            fail();
        } catch (IllegalStateException expected) {
            // ok
        }
        s.confirmAll();
        assertFalse(s.problems().isEmpty());
        try {
            s.build("x");
            fail();
        } catch (GraphFormatException expected) {
            assertTrue(expected.getMessage().contains("no weight"));
        }
    }

    @Test
    public void confirmAllClearsEveryFlagWhenNothingBlocks() throws Exception {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.setWeight(4, 5);
        s.confirmAll();
        assertEquals(List.of(), s.flags());
        assertEquals(7, s.build("x").graph().edgeCount());
    }

    @Test
    public void duplicateAndBadNamesBlockTheBuild() {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.setWeight(4, 1);
        s.renameNode(1, "A");
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("Two nodes are named")));
        s.renameNode(1, "");
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("no name")));
        s.renameNode(1, "ThisNameIsFarTooLong");
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("longer than")));
        s.renameNode(1, "B");
        assertEquals(List.of(), s.problems());
    }

    @Test
    public void deleteNodeRemovesItsEdges() {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.deleteNode(4); // E: edges 3, 4, 5
        assertEquals(5, s.nodes().size());
        assertEquals(4, s.edges().size());
        assertTrue(s.edges().stream().noneMatch(e -> e.from() == 4 || e.to() == 4));
    }

    @Test
    public void addEdgeRejectsDuplicatesAndLoops() {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        try {
            s.addEdge(1, 0, 3.0);
            fail("A-B already exists");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            s.addEdge(2, 2, 1.0);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        int id = s.addEdge(0, 5, 8.0);
        assertEquals(1.0, s.edges().stream().filter(e -> e.id() == id).findFirst().get().confidence(), 0);
    }

    @Test
    public void directionToggleMergesOppositeEdges() throws Exception {
        DetectedGraph d = TextGraphParser.parse("A -> B 1\nB -> A 2\nB -> C 3\n");
        ReviewSession s = new ReviewSession(d);
        assertTrue(s.directed());
        assertEquals(3, s.build("x").graph().edgeCount());
        s.setDirected(false);
        assertEquals(2, s.edges().size());
        assertEquals(2, s.build("x").graph().edgeCount());
        assertFalse(s.build("x").graph().isDirected());
    }

    @Test
    public void reverseEdgeSwapsEnds() {
        ReviewSession s = new ReviewSession(TextGraphParser.parse("A -> B 1\n"));
        s.reverseEdge(0);
        assertEquals(1, s.edges().get(0).from());
        assertEquals(0, s.edges().get(0).to());
    }

    @Test
    public void weightsOffMakesEveryWeightOne() throws Exception {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.setWeighted(false);
        assertEquals(0, count(s, ReviewSession.Kind.WEIGHT));
        GraphDocument d = s.build("x");
        assertFalse(d.weighted());
        assertTrue(d.graph().edges().stream().allMatch(e -> e.weight() == 1.0));
    }

    @Test
    public void picturePositionsAreScaledToRoomyEditorUnits() throws Exception {
        ReviewSession s = new ReviewSession(FakeDetections.demo());
        s.confirmAll();
        s.setWeight(4, 1);
        GraphDocument d = s.build("x");
        double closest = Double.MAX_VALUE;
        List<Node> ns = d.graph().nodes();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                closest = Math.min(closest, Math.hypot(ns.get(i).x() - ns.get(j).x(), ns.get(i).y() - ns.get(j).y()));
            }
        }
        assertTrue("closest " + closest, closest >= 60);
        // the layout keeps the picture's shape: A is left of C, and D is below A
        assertTrue(ns.get(0).x() < ns.get(2).x());
        assertTrue(ns.get(3).y() > ns.get(0).y());
    }

    @Test
    public void textGraphsGetACircleLayout() throws Exception {
        GraphDocument d = new ReviewSession(TextGraphParser.parse("A B 1\nB C 2\nC A 3\n")).build("x");
        assertEquals(3, d.graph().nodeCount());
        assertEquals(0.0, d.graph().nodes().get(0).x(), 1e-6);
    }

    @Test
    public void emptyGraphBlocks() {
        ReviewSession s = new ReviewSession(MatrixParser.parseText(""));
        assertFalse(s.problems().isEmpty());
    }

    @Test
    public void importerPicksMatrixOrList() {
        assertEquals(DetectedGraph.Source.MATRIX, GraphTextImporter.parse("0 4 2\n4 0 1\n2 1 0\n").source());
        assertEquals(DetectedGraph.Source.MATRIX, GraphTextImporter.parse("  A B C\nA 0 4 2\nB 4 0 1\nC 2 1 0\n").source());
        assertEquals(DetectedGraph.Source.EDGE_LIST, GraphTextImporter.parse("A B 4\nA C 2\nB C 1\n").source());
        assertEquals(DetectedGraph.Source.EDGE_LIST, GraphTextImporter.parse("1 2 5\n2 3 4\n1 3 1\n").source());
        assertEquals(DetectedGraph.Source.EDGE_LIST, GraphTextImporter.parse("A B\nB C\n").source());
        assertEquals(DetectedGraph.Source.EDGE_LIST, GraphTextImporter.parse("A->B 1\nB->C 2\nC->A 3\nA->D 4\n").source());
    }

    @Test
    public void importerWorksOnTokens() {
        String[][] grid = {{"", "A", "B", "C"}, {"A", "0", "4", "0"}, {"B", "4", "0", "9"}, {"C", "0", "9", "0"}};
        List<OcrToken> t = new java.util.ArrayList<>();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (!grid[r][c].isEmpty()) {
                    t.add(OcrToken.at(grid[r][c], 40 + c * 40, 30 + r * 30, .9));
                }
            }
        }
        DetectedGraph g = GraphTextImporter.parse(t);
        assertEquals(DetectedGraph.Source.MATRIX, g.source());
        assertEquals(2, g.edges().size());
        assertEquals("C", g.nodes().get(2).label());
        assertTrue(new ReviewSession(g).flags().isEmpty());
    }
}

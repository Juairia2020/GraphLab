package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.model.Graph;
import com.graphlab.engine.model.Node;
import com.graphlab.engine.model.SampleGraph;
import com.graphlab.engine.trace.Params;
import org.junit.Test;

/**
 * Plays the "build the sample graph by touch" demo without a phone: every tap and drag is a
 * screen position, converted and hit-tested exactly the way GraphView does it.
 */
public class EditorSessionTest {
    private static final double DENSITY = 2.75;           // a typical phone
    private static final double SLOP_PX = 24 * DENSITY;

    @Test
    public void buildingTheSampleGraphByTouchGivesTheSampleGraphAndTheRightAnswers() {
        GraphEditor editor = new GraphEditor(false, true);
        Viewport vp = new Viewport();
        vp.setDensity(DENSITY);
        vp.setSize(1080, 1700);
        vp.set(1.0, 150, 120);                                    // world (150,120) in the middle

        // Tap the six node positions (the sample, spread by 3), as screen points.
        Graph sample = SampleGraph.sixNodeUndirected();
        for (Node node : sample.nodes()) {
            double sx = vp.worldToScreenX(node.x() * 3);
            double sy = vp.worldToScreenY(node.y() * 3);
            assertEquals("empty spot", -1, HitTester.nodeAt(editor, vp, sx, sy, SLOP_PX, -1));
            double wx = vp.screenToWorldX(sx);
            double wy = vp.screenToWorldY(sy);
            assertTrue(editor.isFreeSpot(wx, wy));
            editor.addNode(wx, wy);
        }
        assertEquals(6, editor.nodeCount());
        assertEquals("F", editor.nodeAt(5).label());

        // Drag from node to node for every sample edge: find both ends by hit test, then connect.
        for (var edge : sample.edges()) {
            Node a = sample.node(edge.from());
            Node b = sample.node(edge.to());
            int from = HitTester.nodeAt(editor, vp, vp.worldToScreenX(a.x() * 3), vp.worldToScreenY(a.y() * 3), SLOP_PX, -1);
            int to = HitTester.nodeAt(editor, vp, vp.worldToScreenX(b.x() * 3), vp.worldToScreenY(b.y() * 3), SLOP_PX, from);
            assertEquals(edge.from(), from);
            assertEquals(edge.to(), to);
            assertEquals(GraphEditor.ConnectCheck.OK, editor.checkConnect(from, to));
            editor.addEdge(from, to, edge.weight());
        }
        assertEquals(9, editor.edgeCount());

        // The result is the sample graph (same ids, labels, weights; positions are the tapped ones).
        Graph built = editor.toGraph();
        assertEquals(sample.edges(), built.edges());
        assertEquals(sample.nodeCount(), built.nodeCount());

        // And the real algorithms give the documented answers on it.
        var dijkstra = Algorithms.byId("dijkstra").run(built, Params.from(SampleGraph.A)).result();
        assertEquals(20.0, dijkstra.distances().get(SampleGraph.E), 1e-9);
        var kruskal = Algorithms.byId("kruskal").run(built, Params.none()).result();
        assertEquals(33.0, kruskal.totalWeight(), 1e-9);
    }

    @Test
    public void tappingEveryEdgeMiddleFindsThatEdge() {
        Graph sample = SampleGraph.sixNodeUndirected();
        GraphEditor editor = GraphEditor.from(sample, true);
        editor.scalePositions(3);
        Viewport vp = new Viewport();
        vp.setDensity(DENSITY);
        vp.setSize(1080, 1700);
        vp.fit(editor.bounds(), EditorMetrics.NODE_RADIUS, EditorMetrics.FIT_MARGIN_DP * DENSITY);

        EdgeShape shape = new EdgeShape();
        float radius = (float) (EditorMetrics.NODE_RADIUS * vp.scale());
        for (int i = 0; i < editor.edgeCount(); i++) {
            Node from = editor.nodeAt(editor.edgeFromIndex(i));
            Node to = editor.nodeAt(editor.edgeToIndex(i));
            shape.set((float) vp.worldToScreenX(from.x()), (float) vp.worldToScreenY(from.y()),
                    (float) vp.worldToScreenX(to.x()), (float) vp.worldToScreenY(to.y()), 0f, radius);
            if (!shape.visible) {
                continue;
            }
            // Only count the middle if no node covers it (long edges); otherwise a tap there hits the node.
            if (HitTester.nodeAt(editor, vp, shape.midX, shape.midY, 24 * DENSITY, -1) >= 0) {
                continue;
            }
            assertEquals("edge " + i, editor.edgeAt(i).id(),
                    HitTester.edgeAt(editor, vp, shape.midX, shape.midY, 20 * DENSITY));
        }
    }

    @Test
    public void everyNodeOfTheFittedSampleIsInsideTheView() {
        GraphEditor editor = GraphEditor.from(SampleGraph.sixNodeUndirected(), true);
        editor.scalePositions(3);
        for (int[] size : new int[][] {{1080, 1700}, {1700, 1080}, {720, 1280}}) {   // portrait, landscape, small
            Viewport vp = new Viewport();
            vp.setDensity(DENSITY);
            vp.setSize(size[0], size[1]);
            vp.fit(editor.bounds(), EditorMetrics.NODE_RADIUS, EditorMetrics.FIT_MARGIN_DP * DENSITY);
            double r = EditorMetrics.NODE_RADIUS * vp.scale();
            for (int i = 0; i < editor.nodeCount(); i++) {
                Node n = editor.nodeAt(i);
                double sx = vp.worldToScreenX(n.x());
                double sy = vp.worldToScreenY(n.y());
                assertTrue(sx - r >= 0 && sx + r <= size[0]);
                assertTrue(sy - r >= 0 && sy + r <= size[1]);
            }
        }
    }
}

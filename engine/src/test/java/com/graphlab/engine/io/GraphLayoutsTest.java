package com.graphlab.engine.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GraphLayoutsTest {

    private static List<Node> nodes(double... xy) {
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < xy.length / 2; i++) {
            list.add(new Node(i, xy[2 * i], xy[2 * i + 1], "N" + i));
        }
        return list;
    }

    private static double closest(List<Node> list) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                best = Math.min(best, Math.hypot(list.get(i).x() - list.get(j).x(), list.get(i).y() - list.get(j).y()));
            }
        }
        return best;
    }

    @Test
    public void circleKeepsIdsAndLabelsAndSpreadsNodesApart() {
        List<Node> input = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            input.add(new Node(i * 3, 0, 0, "L" + i));
        }
        List<Node> out = GraphLayouts.circle(input);
        assertEquals(60, out.size());
        assertEquals(3, out.get(1).id());
        assertEquals("L1", out.get(1).label());
        assertTrue(closest(out) >= GraphLayouts.MIN_SPACING - 1);   // 60 nodes still do not overlap
        assertEquals(0.0, out.get(0).x(), 0.01);                    // starts at the top
        assertTrue(out.get(0).y() < 0);
    }

    @Test
    public void circleHandlesZeroAndOneNode() {
        assertTrue(GraphLayouts.circle(List.of()).isEmpty());
        assertEquals(1, GraphLayouts.circle(nodes(5, 5)).size());
    }

    @Test
    public void roomyLayoutsAreKeptExactly() {
        List<Node> input = nodes(0, 0, 120, 0, 60, 90);
        assertSame(input, GraphLayouts.fitForeign(input));
    }

    @Test
    public void tinyCoordinatesAreScaledUp() {
        List<Node> input = nodes(0, 0, 0.5, 0, 0.25, 0.4);
        List<Node> out = GraphLayouts.fitForeign(input);
        assertEquals(GraphLayouts.MIN_SPACING, closest(out), 1e-6);
        assertEquals(input.get(1).id(), out.get(1).id());
    }

    @Test
    public void sharedPositionsFallBackToACircle() {
        List<Node> out = GraphLayouts.fitForeign(nodes(0, 0, 0, 0, 50, 50));
        assertTrue(closest(out) >= GraphLayouts.MIN_SPACING - 1);
    }

    @Test
    public void absurdlyWideLayoutsFallBackToACircle() {
        List<Node> out = GraphLayouts.fitForeign(nodes(0, 0, 0.001, 0, 100000, 0));
        assertTrue(closest(out) >= GraphLayouts.MIN_SPACING - 1);
    }

    @Test
    public void singleNodesAreLeftAlone() {
        List<Node> input = nodes(7, 8);
        assertSame(input, GraphLayouts.fitForeign(input));
    }
}

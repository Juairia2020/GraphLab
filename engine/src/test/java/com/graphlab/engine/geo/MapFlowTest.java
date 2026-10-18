package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.model.Edge;
import java.util.List;
import org.junit.Test;

public class MapFlowTest {
    private static RouteMatrix matrix(String... names) {
        java.util.ArrayList<City> places = new java.util.ArrayList<>();
        for (String n : names) {
            places.add(Cities.byName(n));
        }
        return RouteService.estimate(places);
    }

    @Test
    public void flowEqualsTheMinimumCut() {
        MapFlow.Network net = MapFlow.solve(matrix("Khulna", "Dhaka", "Sylhet", "Rajshahi", "Chattogram"));
        double cut = 0;
        for (int id : net.cutEdgeIds()) {
            cut += net.graph().edge(id).weight();
        }
        assertTrue(net.value() > 0);
        assertEquals(net.value(), cut, 1e-9);
    }

    @Test
    public void everyLinkWorksBothWaysWithTheSameCapacity() {
        MapFlow.Network net = MapFlow.solve(matrix("Khulna", "Dhaka", "Sylhet", "Rajshahi"));
        List<Edge> edges = net.graph().edges();
        assertEquals(0, edges.size() % 2);
        for (int i = 0; i < edges.size(); i += 2) {
            assertEquals(edges.get(i).from(), edges.get(i + 1).to());
            assertEquals(edges.get(i).to(), edges.get(i + 1).from());
            assertEquals(edges.get(i).weight(), edges.get(i + 1).weight(), 0);
        }
    }

    @Test
    public void theSameInputGivesTheSameAnswer() {
        assertEquals(MapFlow.solve(matrix("Khulna", "Dhaka", "Sylhet")).value(), MapFlow.solve(matrix("Khulna", "Dhaka", "Sylhet")).value(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fewerThanThreePlacesIsRefused() {
        MapFlow.solve(matrix("Khulna", "Dhaka"));
    }
}

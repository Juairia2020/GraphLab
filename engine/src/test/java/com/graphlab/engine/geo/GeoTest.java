package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class GeoTest {
    @Test
    public void thirtyDistinctCitiesLoad() {
        List<City> all = Cities.all();
        assertEquals(30, all.size());
        Set<String> names = new HashSet<>();
        for (City c : all) {
            assertTrue(names.add(c.name()));
            assertTrue("inside Bangladesh box " + c, c.lat() > 20.5 && c.lat() < 26.7 && c.lon() > 88 && c.lon() < 92.7);
        }
        assertNotNull(Cities.byName("Khulna"));
        assertNotNull(Cities.byName("Sylhet"));
    }

    @Test
    public void haversineMatchesKnownDistances() {
        // one degree of latitude is 111.19 km on this sphere
        assertEquals(111.19, Geo.haversineKm(10, 50, 11, 50), 0.05);
        assertEquals(0.0, Geo.haversineKm(Cities.byName("Dhaka"), Cities.byName("Dhaka")), 1e-9);
        double dc = Geo.haversineKm(Cities.byName("Dhaka"), Cities.byName("Chattogram"));
        assertTrue("Dhaka-Chattogram straight line about 215 km: " + dc, dc > 205 && dc < 225);
        assertEquals(Geo.haversineKm(Cities.byName("Khulna"), Cities.byName("Sylhet")),
                Geo.haversineKm(Cities.byName("Sylhet"), Cities.byName("Khulna")), 1e-9);
    }

    @Test
    public void projectionKeepsDirectionsAndRoughScale() {
        double[] centre = Geo.project(23.0, 90.0, 23.0, 90.0);
        assertEquals(0, centre[0], 1e-9);
        assertEquals(0, centre[1], 1e-9);
        double[] north = Geo.project(24.0, 90.0, 23.0, 90.0);
        assertTrue("north is up (smaller y)", north[1] < 0);
        double[] east = Geo.project(23.0, 91.0, 23.0, 90.0);
        assertTrue(east[0] > 0);
        // 1 degree of longitude is shorter than 1 degree of latitude by cos(lat)
        assertEquals(Math.cos(Math.toRadians(23.0)), east[0] / 100.0, 1e-9);
    }
}

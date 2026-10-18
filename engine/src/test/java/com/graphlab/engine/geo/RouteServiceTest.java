package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RouteServiceTest {
    private static class MemoryCache implements MatrixCache {
        final Map<String, RouteMatrix> map = new HashMap<>();

        public RouteMatrix get(String key) {
            return map.get(key);
        }

        public void put(String key, RouteMatrix m) {
            map.put(key, m);
        }
    }

    private static class Counting implements MatrixSource {
        int calls;
        boolean fail;

        public RouteMatrix fetch(List<City> places) throws IOException {
            calls++;
            if (fail) {
                throw new IOException("offline");
            }
            int n = places.size();
            double[][] km = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    km[i][j] = i == j ? 0 : Geo.haversineKm(places.get(i), places.get(j)) * 1.25;
                }
            }
            return new RouteMatrix(places, km, "roads");
        }
    }

    private static List<City> cities(String... names) {
        List<City> out = new ArrayList<>();
        for (String n : names) {
            out.add(Cities.byName(n));
        }
        return out;
    }

    @Test
    public void aRepeatedQueryIsServedFromTheCacheWithNoSourceCall() {
        MemoryCache cache = new MemoryCache();
        Counting source = new Counting();
        RouteService service = new RouteService(cache, source);
        RouteMatrix first = service.matrix(cities("Khulna", "Dhaka", "Sylhet"));
        assertEquals(1, source.calls);
        RouteMatrix again = service.matrix(cities("Khulna", "Dhaka", "Sylhet"));
        assertEquals(1, source.calls);
        assertEquals(first.between(0, 2), again.between(0, 2), 0);
        assertEquals("roads", again.origin());
    }

    @Test
    public void theSameSetInAnotherOrderHitsTheCacheAndKeepsTheAskedOrder() {
        MemoryCache cache = new MemoryCache();
        Counting source = new Counting();
        RouteService service = new RouteService(cache, source);
        service.matrix(cities("Khulna", "Dhaka", "Sylhet"));
        RouteMatrix other = service.matrix(cities("Sylhet", "Khulna", "Dhaka"));
        assertEquals(1, source.calls);
        assertEquals("Sylhet", other.places().get(0).name());
        double expected = Geo.haversineKm(Cities.byName("Sylhet"), Cities.byName("Dhaka")) * 1.25;
        assertEquals(expected, other.between(0, 2), 1e-9);
    }

    @Test
    public void differentSetsHaveDifferentKeys() {
        assertNotEquals(RouteMatrix.key(cities("Khulna", "Dhaka")), RouteMatrix.key(cities("Khulna", "Sylhet")));
        assertEquals(RouteMatrix.key(cities("Khulna", "Dhaka")), RouteMatrix.key(cities("Dhaka", "Khulna")));
    }

    @Test
    public void whenOfflineTheEstimateIsUsedMarkedAndNotCached() {
        MemoryCache cache = new MemoryCache();
        Counting source = new Counting();
        source.fail = true;
        RouteService service = new RouteService(cache, source);
        RouteMatrix m = service.matrix(cities("Khulna", "Dhaka"));
        assertEquals("estimate", m.origin());
        assertNull(cache.get(RouteMatrix.key(m.places())));
        source.fail = false;
        assertEquals("roads", service.matrix(cities("Khulna", "Dhaka")).origin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void moreThanTenPlacesAreRefused() {
        new RouteService(new MemoryCache(), new Counting()).matrix(Cities.all().subList(0, 11));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aSinglePlaceIsRefused() {
        new RouteService(new MemoryCache(), new Counting()).matrix(cities("Khulna"));
    }
}

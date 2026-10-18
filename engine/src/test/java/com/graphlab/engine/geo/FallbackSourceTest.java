package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class FallbackSourceTest {
    private static final List<City> TWO = List.of(new City("A", 22.84, 89.54), new City("B", 24.89, 91.87));

    private static MatrixSource failing(String why) {
        return places -> {
            throw new IOException(why);
        };
    }

    private static MatrixSource working(double km) {
        return places -> new RouteMatrix(places, new double[][] {{0, km}, {km, 0}}, "roads");
    }

    @Test
    public void firstWorkingSourceWinsAndLaterOnesAreNotAsked() throws IOException {
        int[] asked = {0};
        MatrixSource second = places -> {
            asked[0]++;
            return working(9).fetch(places);
        };
        FallbackSource f = new FallbackSource(List.of("one", "two"), List.of(working(5), second));
        assertEquals(5, f.fetch(TWO).km()[0][1], 0);
        assertEquals(0, asked[0]);
    }

    @Test
    public void failureMovesOnToTheNextSource() throws IOException {
        FallbackSource f = new FallbackSource(List.of("proxy", "osrm"), List.of(failing("down"), working(7)));
        assertEquals(7, f.fetch(TWO).km()[0][1], 0);
    }

    @Test
    public void allFailingListsEveryReason() {
        FallbackSource f = new FallbackSource(List.of("proxy", "osrm"), List.of(failing("down"), failing("timeout")));
        try {
            f.fetch(TWO);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("proxy: down") && e.getMessage().contains("osrm: timeout"));
        }
    }

    @Test
    public void serviceReportsWhyItUsedTheEstimate() {
        RouteService s = new RouteService(new RouteServiceTestCache(), new FallbackSource(List.of("osrm"), List.of(failing("timeout"))));
        assertEquals("estimate", s.matrix(TWO).origin());
        assertTrue(s.lastFailure(), s.lastFailure().contains("osrm: timeout"));
        RouteService ok = new RouteService(new RouteServiceTestCache(), working(5)::fetch);
        assertEquals("roads", ok.matrix(TWO).origin());
        assertEquals("", ok.lastFailure());
    }

    private static final class RouteServiceTestCache implements MatrixCache {
        private final java.util.Map<String, RouteMatrix> map = new java.util.HashMap<>();

        public RouteMatrix get(String key) {
            return map.get(key);
        }

        public void put(String key, RouteMatrix m) {
            map.put(key, m);
        }
    }
}

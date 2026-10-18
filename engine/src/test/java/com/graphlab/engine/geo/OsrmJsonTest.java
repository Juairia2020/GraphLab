package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class OsrmJsonTest {
    private static final List<City> TWO = List.of(new City("A", 22.84, 89.54), new City("B", 24.89, 91.87));

    @Test
    public void pathPutsLongitudeFirstAndAsksForDistance() {
        assertEquals("89.54000,22.84000;91.87000,24.89000?annotations=distance", OsrmJson.path(TWO));
    }

    @Test
    public void metresBecomeKilometres() throws IOException {
        RouteMatrix m = OsrmJson.fromAnswer(TWO, "{\"code\":\"Ok\",\"distances\":[[0,312345.6],[310000,0]]}");
        assertEquals("roads", m.origin());
        assertEquals(312.3, m.km()[0][1], 1e-9);
        assertEquals(310.0, m.km()[1][0], 1e-9);
        assertEquals(0.0, m.km()[0][0], 0);
    }

    private static void refused(String json, String part) {
        try {
            OsrmJson.fromAnswer(TWO, json);
            fail("should be refused: " + json);
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(part));
        }
    }

    @Test
    public void badAnswersAreRefusedWithAReason() {
        refused("{\"code\":\"InvalidQuery\"}", "InvalidQuery");
        refused("{\"code\":\"Ok\"}", "no distances");
        refused("not json", "not JSON");
        refused("{\"code\":\"Ok\",\"distances\":[[0,1]]}", "1 rows");
        refused("{\"code\":\"Ok\",\"distances\":[[0,null],[5,0]]}", "no road between A and B");
        refused("{\"code\":\"Ok\",\"distances\":[[0],[5,0]]}", "2 distances");
    }
}

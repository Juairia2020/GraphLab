package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphlab.engine.preset.PresetBank;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class RouteMatrixJsonTest {
    private static final List<City> PLACES = List.of(Cities.byName("Khulna"), Cities.byName("Dhaka"), Cities.byName("Sylhet"));

    @Test
    public void aStoredMatrixReadsBackExactly() throws IOException {
        RouteMatrix m = RouteService.estimate(PLACES);
        RouteMatrix back = RouteMatrixJson.read(RouteMatrixJson.write(m));
        assertEquals(m.places(), back.places());
        assertEquals(m.origin(), back.origin());
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(m.between(i, j), back.between(i, j), 1e-9);
            }
        }
    }

    @Test
    public void theProxyRequestAndAnswerRoundTrip() throws IOException {
        assertTrue(RouteMatrixJson.request(PLACES).contains("\"Khulna\""));
        RouteMatrix m = RouteMatrixJson.fromAnswer(PLACES, "{\"km\":[[0,270.5,400],[272,0,230],[410,235,0]]}");
        assertEquals("roads", m.origin());
        assertEquals(230, m.between(1, 2), 0);
    }

    @Test
    public void unusableAnswersAreRefused() {
        String[] bad = {"not json", "{}", "{\"km\":[[0,1],[1,0]]}", "{\"km\":[[0,1,2],[1,0],[1,2,0]]}",
                "{\"km\":[[0,1,2],[1,0,null],[1,2,0]]}", "{\"km\":[[0,-1,2],[1,0,3],[1,2,0]]}"};
        for (String json : bad) {
            try {
                RouteMatrixJson.fromAnswer(PLACES, json);
                fail("should be refused: " + json);
            } catch (IOException expected) {
                // fine
            }
        }
    }

    @Test
    public void theFloodPresetCanBePlacedOnTheMap() {
        var preset = PresetBank.current().byId("flood-relief");
        assertTrue(preset.hasGeo());
        assertEquals(preset.graph().nodeCount(), preset.geo().size());
        for (City c : preset.geo()) {
            assertTrue(c.lat() > 22 && c.lat() < 23.5 && c.lon() > 89 && c.lon() < 90);
        }
    }
}

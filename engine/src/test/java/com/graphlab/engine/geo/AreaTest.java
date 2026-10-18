package com.graphlab.engine.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** The area the person picks for a Toolkit scenario: a centre and the places closest to it. */
public class AreaTest {

    private static double km(City a, City b) {
        return Geo.haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    @Test
    public void everyBundledCityGivesAnAreaWithItselfFirstAndNoRepeats() {
        for (City centre : Cities.all()) {
            for (int count : new int[] {1, 4, 6, 10}) {
                List<City> area = Cities.area(centre, count);
                assertEquals(count, area.size());
                assertEquals(centre, area.get(0));
                assertEquals(count, new HashSet<>(area).size());
            }
        }
    }

    @Test
    public void placesComeNearestFirstSoTheLastIsTheFarthest() {
        for (City centre : Cities.all()) {
            List<City> area = Cities.area(centre, 8);
            for (int i = 2; i < area.size(); i++) {
                assertTrue(km(centre, area.get(i - 1)) <= km(centre, area.get(i)) + 1e-9);
            }
            // nobody left out is closer than the farthest one taken
            double farthest = km(centre, area.get(area.size() - 1));
            for (City c : Cities.all()) {
                if (!area.contains(c)) {
                    assertTrue(km(centre, c) >= farthest - 1e-9);
                }
            }
        }
    }

    @Test
    public void theAreaDependsOnWhereTheCentreIsNotOnAFixedList() {
        City a = Cities.all().get(0);
        City b = Cities.all().get(Cities.all().size() - 1);
        assertTrue(!Cities.area(a, 5).equals(Cities.area(b, 5)));
    }

    @Test
    public void askingForMoreThanThereAreGivesAllOfThem() {
        assertEquals(Cities.all().size(), Cities.area(Cities.all().get(0), 500).size());
    }

    @Test
    public void zeroPlacesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Cities.area(Cities.all().get(0), 0));
    }
}

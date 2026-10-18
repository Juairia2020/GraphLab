package com.graphlab.engine.geo;

import java.util.Objects;

/** A place on the map. The name is also its identity in the cache key. */
public record City(String name, double lat, double lon) {
    public City {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A city needs a name");
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Latitude or longitude out of range for " + name);
        }
    }
}

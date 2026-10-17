package com.graphlab.engine.geo;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * One query, one matrix, then the cache. A repeated query for the same set of places (in any order) is answered from the
 * cache with no call to the source. When the source fails, the straight-line estimate is used, marked as such, and is not cached.
 */
public final class RouteService {
    /** Typical road detour over the straight line; only used for the offline estimate. */
    public static final double DETOUR = 1.3;

    private final MatrixCache cache;
    private final MatrixSource source;
    private int sourceCalls;
    private String lastFailure = "";

    public RouteService(MatrixCache cache, MatrixSource source) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.source = source;
    }

    /** Why the last query fell back to the estimate (empty when it did not, or when no source was set up). */
    public String lastFailure() {
        return lastFailure;
    }

    public int sourceCalls() {
        return sourceCalls;
    }

    /** The matrix for these places, in the order asked. */
    public RouteMatrix matrix(List<City> places) {
        if (places.size() < 2 || places.size() > RouteMatrix.MAX_PLACES) {
            throw new IllegalArgumentException("A query needs 2 to " + RouteMatrix.MAX_PLACES + " places");
        }
        lastFailure = "";
        String key = RouteMatrix.key(places);
        RouteMatrix hit = cache.get(key);
        if (hit != null) {
            return hit.reorder(places);
        }
        if (source != null) {
            try {
                sourceCalls++;
                RouteMatrix fresh = source.fetch(places);
                if (fresh.places().size() == places.size() && fresh.places().containsAll(places)) {
                    cache.put(key, fresh);
                    return fresh.reorder(places);
                }
                lastFailure = "The road service answered for different places.";
            } catch (IOException | RuntimeException e) {
                lastFailure = String.valueOf(e.getMessage());   // shown on the sheet; then the estimate is used
            }
        }
        return estimate(places);
    }

    public static RouteMatrix estimate(List<City> places) {
        int n = places.size();
        double[][] km = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                km[i][j] = i == j ? 0 : Geo.haversineKm(places.get(i), places.get(j)) * DETOUR;
            }
        }
        return new RouteMatrix(places, km, "estimate");
    }
}

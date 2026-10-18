package com.graphlab.engine.geo;

/** Map maths: the great-circle (haversine) distance and an equirectangular projection to canvas x and y. */
public final class Geo {
    public static final double EARTH_KM = 6371.0088;
    /** A road is never shorter than the straight line, so haversine never overestimates a road distance. */
    public static final double CANVAS_PER_DEGREE = 100.0;

    private Geo() {
    }

    public static double haversineKm(City a, City b) {
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * Equirectangular projection around a centre latitude: x grows east, y grows south (screen direction). One degree of
     * latitude is {@link #CANVAS_PER_DEGREE} units; longitude is shrunk by cos(centre latitude) so shapes stay true.
     */
    public static double[] project(double lat, double lon, double centreLat, double centreLon) {
        double x = (lon - centreLon) * Math.cos(Math.toRadians(centreLat)) * CANVAS_PER_DEGREE;
        double y = -(lat - centreLat) * CANVAS_PER_DEGREE;
        return new double[] {x, y};
    }
}

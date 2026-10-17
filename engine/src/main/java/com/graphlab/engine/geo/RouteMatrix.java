package com.graphlab.engine.geo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Road distances in km between every pair of places: {@code km[i][j]} is from {@code places[i]} to {@code places[j]}.
 * {@code origin} says where the numbers came from ("roads" from the Routes API, "estimate" from straight lines times a
 * detour factor when no key or network is available). At most {@link #MAX_PLACES} places (100 elements per query).
 */
public record RouteMatrix(List<City> places, double[][] km, String origin) {
    public static final int MAX_PLACES = 10;

    public RouteMatrix {
        places = List.copyOf(places);
        Objects.requireNonNull(origin, "origin");
        int n = places.size();
        if (n < 2 || n > MAX_PLACES) {
            throw new IllegalArgumentException("A query needs 2 to " + MAX_PLACES + " places, got " + n);
        }
        if (km.length != n) {
            throw new IllegalArgumentException("The matrix must be " + n + " by " + n);
        }
        double[][] copy = new double[n][];
        for (int i = 0; i < n; i++) {
            if (km[i].length != n) {
                throw new IllegalArgumentException("The matrix must be " + n + " by " + n);
            }
            copy[i] = km[i].clone();
            for (double v : copy[i]) {
                if (!(v >= 0)) {
                    throw new IllegalArgumentException("Distances must be 0 or more");
                }
            }
        }
        km = copy;
    }

    @Override
    public double[][] km() {
        double[][] out = new double[km.length][];
        for (int i = 0; i < km.length; i++) {
            out[i] = km[i].clone();
        }
        return out;
    }

    public double between(int i, int j) {
        return km[i][j];
    }

    /** The cache key: the same set of places gives the same key in any order. */
    public static String key(List<City> places) {
        List<String> names = new ArrayList<>();
        for (City c : places) {
            names.add(c.name());
        }
        names.sort(Comparator.naturalOrder());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", names).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String key() {
        return key(places);
    }

    /** The same distances with the places in another order (the same set). */
    public RouteMatrix reorder(List<City> order) {
        int n = order.size();
        if (n != places.size()) {
            throw new IllegalArgumentException("Not the same set of places");
        }
        int[] at = new int[n];
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < n; i++) {
            at[i] = places.indexOf(order.get(i));
            if (at[i] < 0 || !used.add(at[i])) {
                throw new IllegalArgumentException("Not the same set of places");
            }
        }
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                out[i][j] = km[at[i]][at[j]];
            }
        }
        return new RouteMatrix(order, out, origin);
    }
}

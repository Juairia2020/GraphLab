package com.graphlab.engine.geo;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The JSON of a matrix, used three ways: the cache row, the request to the proxy ({@code {"places":[{"name","lat","lon"}]}})
 * and the proxy's answer ({@code {"km":[[...]]}}, metres already turned into km, in the order asked).
 */
public final class RouteMatrixJson {
    private RouteMatrixJson() {
    }

    public static String request(List<City> places) {
        StringBuilder out = new StringBuilder("{\"places\":[");
        for (int i = 0; i < places.size(); i++) {
            City c = places.get(i);
            out.append(i == 0 ? "" : ",").append("{\"name\":").append(MiniJson.quote(c.name()))
                    .append(",\"lat\":").append(MiniJson.number(c.lat()))
                    .append(",\"lon\":").append(MiniJson.number(c.lon())).append('}');
        }
        return out.append("]}").toString();
    }

    /** The proxy's answer for the places that were asked. */
    public static RouteMatrix fromAnswer(List<City> places, String json) throws IOException {
        List<double[]> rows = rows(json, places.size());
        try {
            return new RouteMatrix(places, rows.toArray(new double[0][]), "roads");
        } catch (IllegalArgumentException e) {
            throw new IOException("The route answer is not usable: " + e.getMessage());
        }
    }

    public static String write(RouteMatrix m) {
        StringBuilder out = new StringBuilder("{\"origin\":").append(MiniJson.quote(m.origin())).append(",\"places\":[");
        for (int i = 0; i < m.places().size(); i++) {
            City c = m.places().get(i);
            out.append(i == 0 ? "" : ",").append("{\"name\":").append(MiniJson.quote(c.name()))
                    .append(",\"lat\":").append(MiniJson.number(c.lat()))
                    .append(",\"lon\":").append(MiniJson.number(c.lon())).append('}');
        }
        out.append("],\"km\":[");
        double[][] km = m.km();
        for (int i = 0; i < km.length; i++) {
            out.append(i == 0 ? "[" : ",[");
            for (int j = 0; j < km[i].length; j++) {
                out.append(j == 0 ? "" : ",").append(MiniJson.number(km[i][j]));
            }
            out.append(']');
        }
        return out.append("]}").toString();
    }

    public static RouteMatrix read(String json) throws IOException {
        try {
            Object root = MiniJson.parse(json);
            if (!(root instanceof Map)) {
                throw new IOException("A stored matrix must be a JSON object.");
            }
            Map<?, ?> top = (Map<?, ?>) root;
            if (!(top.get("places") instanceof List) || !(top.get("origin") instanceof String)) {
                throw new IOException("A stored matrix needs places and an origin.");
            }
            List<City> places = new ArrayList<>();
            for (Object raw : (List<?>) top.get("places")) {
                Map<?, ?> m = (Map<?, ?>) raw;
                places.add(new City((String) m.get("name"), ((Number) m.get("lat")).doubleValue(), ((Number) m.get("lon")).doubleValue()));
            }
            List<double[]> rows = rows(json, places.size());
            return new RouteMatrix(places, rows.toArray(new double[0][]), (String) top.get("origin"));
        } catch (GraphFormatException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            throw new IOException("A stored matrix is damaged: " + e.getMessage());
        }
    }

    private static List<double[]> rows(String json, int n) throws IOException {
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (GraphFormatException e) {
            throw new IOException("Not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map) || !(((Map<?, ?>) root).get("km") instanceof List)) {
            throw new IOException("The answer has no \"km\" matrix.");
        }
        List<?> raw = (List<?>) ((Map<?, ?>) root).get("km");
        if (raw.size() != n) {
            throw new IOException("The matrix has " + raw.size() + " rows, expected " + n + ".");
        }
        List<double[]> out = new ArrayList<>();
        for (Object row : raw) {
            if (!(row instanceof List) || ((List<?>) row).size() != n) {
                throw new IOException("Every row needs " + n + " numbers.");
            }
            double[] r = new double[n];
            for (int j = 0; j < n; j++) {
                Object v = ((List<?>) row).get(j);
                if (!(v instanceof Number)) {
                    throw new IOException("A distance is not a number (a route may be missing).");
                }
                r[j] = ((Number) v).doubleValue();
            }
            out.add(r);
        }
        return out;
    }
}

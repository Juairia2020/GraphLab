package com.graphlab.engine.geo;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The request and answer of an OSRM "table" service (free road routing, no key): the places go in the URL as
 * {@code lon,lat;lon,lat}, the answer is {@code {"code":"Ok","distances":[[metres...]]}}. A missing route is {@code null}.
 */
public final class OsrmJson {
    private OsrmJson() {
    }

    /** The part of the URL after {@code /table/v1/driving/}, plus the query string. */
    public static String path(List<City> places) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < places.size(); i++) {
            City c = places.get(i);
            out.append(i == 0 ? "" : ";").append(String.format(Locale.US, "%.5f,%.5f", c.lon(), c.lat()));
        }
        return out.append("?annotations=distance").toString();
    }

    /** @throws IOException when the answer is not "Ok", has the wrong size, or has a pair with no road */
    public static RouteMatrix fromAnswer(List<City> places, String json) throws IOException {
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (GraphFormatException e) {
            throw new IOException("The road service sent text that is not JSON.");
        }
        if (!(root instanceof Map)) {
            throw new IOException("The road service answer is not an object.");
        }
        Map<?, ?> top = (Map<?, ?>) root;
        Object code = top.get("code");
        if (!"Ok".equals(code)) {
            throw new IOException("The road service answered " + code + ".");
        }
        if (!(top.get("distances") instanceof List)) {
            throw new IOException("The road service answer has no distances.");
        }
        List<?> rows = (List<?>) top.get("distances");
        int n = places.size();
        if (rows.size() != n) {
            throw new IOException("The road service sent " + rows.size() + " rows, expected " + n + ".");
        }
        double[][] km = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (!(rows.get(i) instanceof List) || ((List<?>) rows.get(i)).size() != n) {
                throw new IOException("Every row needs " + n + " distances.");
            }
            for (int j = 0; j < n; j++) {
                Object v = ((List<?>) rows.get(i)).get(j);
                if (i == j) {
                    km[i][j] = 0;
                } else if (v instanceof Number && ((Number) v).doubleValue() >= 0) {
                    km[i][j] = Math.round(((Number) v).doubleValue() / 100.0) / 10.0;   // metres to km, one decimal
                } else {
                    throw new IOException("There is no road between " + places.get(i).name() + " and " + places.get(j).name() + ".");
                }
            }
        }
        return new RouteMatrix(places, km, "roads");
    }
}

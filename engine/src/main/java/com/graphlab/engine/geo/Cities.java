package com.graphlab.engine.geo;

import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The bundled place list for the picker ({@code /content/cities.json}). */
public final class Cities {
    public static final String RESOURCE = "/content/cities.json";
    private static volatile List<City> cached;

    private Cities() {
    }

    public static List<City> all() {
        List<City> list = cached;
        if (list == null) {
            synchronized (Cities.class) {
                if (cached == null) {
                    try (InputStream in = Cities.class.getResourceAsStream(RESOURCE)) {
                        if (in == null) {
                            throw new IllegalStateException("The bundled city file is missing: " + RESOURCE);
                        }
                        cached = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException | GraphFormatException e) {
                        throw new IllegalStateException("The bundled city file is damaged: " + e.getMessage(), e);
                    }
                }
                list = cached;
            }
        }
        return list;
    }

    public static City byName(String name) {
        for (City c : all()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /**
     * An area chosen by the person: {@code centre} first, then the places closest to it by straight-line distance, nearest
     * first, so the last one is the farthest in the area. Ties are broken by name so the answer never depends on file order.
     * Used by the Toolkit scenarios (flood relief, city bus), which take their places from what the person picks and not
     * from fixed data.
     *
     * @param count how many places in all, centre included; at least 1 and at most the number of places in {@code from}
     */
    public static List<City> area(City centre, int count, List<City> from) {
        if (count < 1) {
            throw new IllegalArgumentException("An area has at least 1 place, got " + count);
        }
        List<City> others = new ArrayList<>();
        for (City c : from) {
            if (!c.equals(centre)) {
                others.add(c);
            }
        }
        others.sort((a, b) -> {
            int d = Double.compare(Geo.haversineKm(centre.lat(), centre.lon(), a.lat(), a.lon()),
                    Geo.haversineKm(centre.lat(), centre.lon(), b.lat(), b.lon()));
            return d != 0 ? d : a.name().compareTo(b.name());
        });
        List<City> out = new ArrayList<>();
        out.add(centre);
        for (int i = 0; i < others.size() && out.size() < count; i++) {
            out.add(others.get(i));
        }
        return List.copyOf(out);
    }

    /** {@link #area(City, int, List)} over the bundled places. */
    public static List<City> area(City centre, int count) {
        return area(centre, count, all());
    }

    static List<City> parse(String text) throws GraphFormatException {
        Object root = MiniJson.parse(text);
        if (!(root instanceof Map) || !(((Map<?, ?>) root).get("cities") instanceof List)) {
            throw new GraphFormatException("The city file needs a \"cities\" list.");
        }
        List<City> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object raw : (List<?>) ((Map<?, ?>) root).get("cities")) {
            if (!(raw instanceof Map)) {
                throw new GraphFormatException("Every city must be an object.");
            }
            Map<?, ?> m = (Map<?, ?>) raw;
            if (!(m.get("name") instanceof String) || !(m.get("lat") instanceof Number) || !(m.get("lon") instanceof Number)) {
                throw new GraphFormatException("A city needs a name, a lat and a lon.");
            }
            City city;
            try {
                city = new City((String) m.get("name"), ((Number) m.get("lat")).doubleValue(), ((Number) m.get("lon")).doubleValue());
            } catch (IllegalArgumentException e) {
                throw new GraphFormatException(e.getMessage());
            }
            if (!names.add(city.name())) {
                throw new GraphFormatException("City listed twice: " + city.name());
            }
            out.add(city);
        }
        return List.copyOf(out);
    }
}

package com.graphlab.engine.preset;

import com.graphlab.engine.content.ContentException;
import com.graphlab.engine.geo.City;
import com.graphlab.engine.io.GraphDocument;
import com.graphlab.engine.io.GraphFormatException;
import com.graphlab.engine.io.GraphJson;
import com.graphlab.engine.io.MiniJson;
import com.graphlab.engine.model.Graph;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Toolkit presets, read from {@code /content/presets.json} (schemaVersion 1). Each preset has an id, title, story, unit,
 * tools, source, target and a GraphLab graph. A max-flow preset must be directed; the shortest-route and spanning-tree tools
 * need the target to be reachable (a spanning tree needs a connected undirected graph).
 */
public final class PresetBank {
    public static final int SCHEMA_VERSION = 1;
    public static final String RESOURCE = "/content/presets.json";

    private static volatile PresetBank current;

    private final List<Preset> presets;

    private PresetBank(List<Preset> presets) {
        this.presets = List.copyOf(presets);
    }

    public List<Preset> presets() {
        return presets;
    }

    public Preset byId(String id) {
        for (Preset p : presets) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static PresetBank current() {
        PresetBank bank = current;
        if (bank == null) {
            synchronized (PresetBank.class) {
                if (current == null) {
                    current = bundled();
                }
                bank = current;
            }
        }
        return bank;
    }

    public static PresetBank bundled() {
        try (InputStream in = PresetBank.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled preset file is missing: " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | ContentException e) {
            throw new IllegalStateException("The bundled preset file is damaged: " + e.getMessage(), e);
        }
    }

    public static PresetBank parse(String text) throws ContentException {
        Object root;
        try {
            root = MiniJson.parse(text);
        } catch (GraphFormatException e) {
            throw new ContentException("The preset file is not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map)) {
            throw new ContentException("The preset file must be a JSON object.");
        }
        Map<?, ?> top = (Map<?, ?>) root;
        int version = whole(top.get("schemaVersion"), "\"schemaVersion\"");
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new ContentException("The preset file has schemaVersion " + version + "; this app reads up to " + SCHEMA_VERSION + ".");
        }
        if (!(top.get("presets") instanceof List) || ((List<?>) top.get("presets")).isEmpty()) {
            throw new ContentException("\"presets\" is missing or empty.");
        }
        List<Preset> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Object raw : (List<?>) top.get("presets")) {
            if (!(raw instanceof Map)) {
                throw new ContentException("Every preset must be a JSON object.");
            }
            Map<?, ?> m = (Map<?, ?>) raw;
            String id = text(m, "id", "A preset");
            String where = "Preset \"" + id + "\"";
            if (!ids.add(id)) {
                throw new ContentException(where + " is listed twice.");
            }
            String title = text(m, "title", where);
            String story = text(m, "story", where);
            String unit = text(m, "unit", where);
            int source = whole(m.get("source"), where + " \"source\"");
            int target = whole(m.get("target"), where + " \"target\"");
            if (!(m.get("tools") instanceof List) || ((List<?>) m.get("tools")).isEmpty()) {
                throw new ContentException(where + " needs a non-empty \"tools\" list.");
            }
            List<PresetTool> tools = new ArrayList<>();
            for (Object t : (List<?>) m.get("tools")) {
                PresetTool tool = PresetTool.fromId(String.valueOf(t));
                if (tool == null) {
                    throw new ContentException(where + ": unknown tool \"" + t + "\".");
                }
                tools.add(tool);
            }
            List<City> geo = new ArrayList<>();
            if (m.get("geo") != null) {
                if (!(m.get("geo") instanceof List)) {
                    throw new ContentException(where + ": \"geo\" must be a list.");
                }
                for (Object g : (List<?>) m.get("geo")) {
                    if (!(g instanceof Map) || !(((Map<?, ?>) g).get("lat") instanceof Number) || !(((Map<?, ?>) g).get("lon") instanceof Number)) {
                        throw new ContentException(where + ": every geo place needs a lat and a lon.");
                    }
                    try {
                        geo.add(new City("p" + geo.size(), ((Number) ((Map<?, ?>) g).get("lat")).doubleValue(), ((Number) ((Map<?, ?>) g).get("lon")).doubleValue()));
                    } catch (IllegalArgumentException e) {
                        throw new ContentException(where + ": " + e.getMessage());
                    }
                }
            }
            GraphDocument doc;
            try {
                doc = GraphJson.fromObject(m.get("graph"));
            } catch (GraphFormatException e) {
                throw new ContentException(where + ": " + e.getMessage());
            }
            Graph graph = doc.graph();
            Preset preset;
            try {
                preset = new Preset(id, title, story, unit, graph, tools, source, target, geo);
            } catch (IllegalArgumentException e) {
                throw new ContentException(where + ": " + e.getMessage());
            }
            if (tools.contains(PresetTool.MAX_FLOW) && !graph.isDirected()) {
                throw new ContentException(where + ": max flow needs a directed graph.");
            }
            if (tools.contains(PresetTool.MST) && graph.isDirected()) {
                throw new ContentException(where + ": a spanning tree needs an undirected graph.");
            }
            for (PresetTool tool : tools) {
                if (tool == PresetTool.SHORTEST_ROUTE && !PresetRunner.run(preset, tool).reachable()) {
                    throw new ContentException(where + ": there is no route from the start to the end.");
                }
                if (tool == PresetTool.MAX_FLOW && PresetRunner.run(preset, tool).value() <= 0) {
                    throw new ContentException(where + ": no flow can reach the end.");
                }
            }
            out.add(preset);
        }
        return new PresetBank(out);
    }

    private static int whole(Object value, String what) throws ContentException {
        if (!(value instanceof Number) || ((Number) value).doubleValue() != Math.rint(((Number) value).doubleValue())) {
            throw new ContentException(what + " must be a whole number.");
        }
        return ((Number) value).intValue();
    }

    private static String text(Map<?, ?> m, String key, String where) throws ContentException {
        Object v = m.get(key);
        if (!(v instanceof String) || ((String) v).isBlank()) {
            throw new ContentException(where + " needs a text \"" + key + "\".");
        }
        return (String) v;
    }
}

package com.graphlab.engine.geo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Tries each road-distance source in order and returns the first answer. If all fail, the error lists every reason. */
public final class FallbackSource implements MatrixSource {
    private final List<MatrixSource> sources;
    private final List<String> names;

    public FallbackSource(List<String> names, List<MatrixSource> sources) {
        if (names.size() != sources.size() || sources.isEmpty()) {
            throw new IllegalArgumentException("One name for each source, and at least one source");
        }
        this.names = new ArrayList<>(names);
        this.sources = new ArrayList<>(sources);
    }

    @Override
    public RouteMatrix fetch(List<City> places) throws IOException {
        StringBuilder reasons = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            try {
                return sources.get(i).fetch(places);
            } catch (IOException | RuntimeException e) {
                reasons.append(reasons.length() == 0 ? "" : " ").append(names.get(i)).append(": ").append(e.getMessage());
            }
        }
        throw new IOException(reasons.toString());
    }
}

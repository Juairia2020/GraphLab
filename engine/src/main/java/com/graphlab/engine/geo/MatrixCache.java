package com.graphlab.engine.geo;

/** Stores matrices by {@link RouteMatrix#key()}. The phone keeps them in SQLite; the engine only knows this interface. */
public interface MatrixCache {
    /** The stored matrix, or null. Its places may be in any order. */
    RouteMatrix get(String key);

    void put(String key, RouteMatrix matrix);
}

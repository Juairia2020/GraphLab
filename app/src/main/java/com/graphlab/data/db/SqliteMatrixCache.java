package com.graphlab.data.db;

import androidx.annotation.NonNull;
import com.graphlab.engine.geo.MatrixCache;
import com.graphlab.engine.geo.RouteMatrix;
import com.graphlab.engine.geo.RouteMatrixJson;
import java.io.IOException;

/** The road-distance cache in SQLite (MS14). Call it only from the disk executor. A damaged row counts as a miss. */
public final class SqliteMatrixCache implements MatrixCache {
    private final GraphRepository repository;

    public SqliteMatrixCache(@NonNull GraphRepository repository) {
        this.repository = repository;
    }

    @Override
    public RouteMatrix get(String key) {
        String json = repository.routeMatrix(key);
        if (json == null) {
            return null;
        }
        try {
            return RouteMatrixJson.read(json);
        } catch (IOException damaged) {
            return null;
        }
    }

    @Override
    public void put(String key, RouteMatrix matrix) {
        repository.putRouteMatrix(key, RouteMatrixJson.write(matrix), System.currentTimeMillis());
    }
}

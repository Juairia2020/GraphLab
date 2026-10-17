package com.graphlab.engine.geo;

import java.io.IOException;
import java.util.List;

/** Where road distances come from: the proxy in front of the Routes API on a phone, a fake in tests. One call, one matrix. */
public interface MatrixSource {
    RouteMatrix fetch(List<City> places) throws IOException;
}

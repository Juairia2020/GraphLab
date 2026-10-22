package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import com.graphlab.engine.geo.City;
import com.graphlab.engine.geo.MatrixSource;
import com.graphlab.engine.geo.OsrmJson;
import com.graphlab.engine.geo.RouteMatrix;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Road distances from the public OSRM demo server: free, no key, no account. It is meant for light use, so GraphLab asks
 * once per set of places and the answer is cached (see {@link com.graphlab.engine.geo.RouteService}). Blocking: use the disk executor.
 */
public final class OsrmClient implements MatrixSource {
    private static final String BASE = "https://router.project-osrm.org/table/v1/driving/";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_ANSWER = 200_000;

    @Override
    public RouteMatrix fetch(@NonNull List<City> places) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(BASE + OsrmJson.path(places)).openConnection();
        try {
            http.setRequestMethod("GET");
            http.setConnectTimeout(TIMEOUT_MS);
            http.setReadTimeout(TIMEOUT_MS);
            http.setRequestProperty("User-Agent", "GraphLab-student-app/1.0");
            int code = http.getResponseCode();
            if (code != 200) {
                throw new IOException("the server answered HTTP " + code);
            }
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > MAX_ANSWER) {
                        throw new IOException("the answer is too large");
                    }
                }
                return OsrmJson.fromAnswer(places, buffer.toString("UTF-8"));
            }
        } catch (java.net.UnknownHostException e) {
            throw new IOException("no internet connection");
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("the server did not answer in time");
        } finally {
            http.disconnect();
        }
    }
}

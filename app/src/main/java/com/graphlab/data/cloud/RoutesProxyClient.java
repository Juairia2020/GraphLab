package com.graphlab.data.cloud;

import androidx.annotation.NonNull;
import com.graphlab.BuildConfig;
import com.graphlab.engine.geo.City;
import com.graphlab.engine.geo.MatrixSource;
import com.graphlab.engine.geo.RouteMatrix;
import com.graphlab.engine.geo.RouteMatrixJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Asks the route proxy for one distance matrix (MS14). The Routes API key lives in the proxy, never in the APK. One call per
 * matrix; {@link com.graphlab.engine.geo.RouteService} calls this only after a cache miss. Blocking: use the disk executor.
 */
public final class RoutesProxyClient implements MatrixSource {
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_ANSWER = 200_000;

    public static boolean isConfigured() {
        return BuildConfig.ROUTES_PROXY_URL.startsWith("https://");
    }

    @Override
    public RouteMatrix fetch(@NonNull List<City> places) throws IOException {
        if (!isConfigured()) {
            throw new IOException("The route proxy is not set up (routesProxyUrl).");
        }
        HttpURLConnection http = (HttpURLConnection) new URL(BuildConfig.ROUTES_PROXY_URL).openConnection();
        try {
            http.setRequestMethod("POST");
            http.setConnectTimeout(TIMEOUT_MS);
            http.setReadTimeout(TIMEOUT_MS);
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = http.getOutputStream()) {
                out.write(RouteMatrixJson.request(places).getBytes(StandardCharsets.UTF_8));
            }
            if (http.getResponseCode() != 200) {
                throw new IOException("The route proxy answered " + http.getResponseCode());
            }
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > MAX_ANSWER) {
                        throw new IOException("The route answer is too large.");
                    }
                }
                return RouteMatrixJson.fromAnswer(places, buffer.toString("UTF-8"));
            }
        } finally {
            http.disconnect();
        }
    }
}

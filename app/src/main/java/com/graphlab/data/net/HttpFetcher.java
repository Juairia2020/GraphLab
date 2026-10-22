package com.graphlab.data.net;

import androidx.annotation.NonNull;
import com.graphlab.engine.net.WebAddress;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads a small text file over https with {@link HttpURLConnection}: no extra library. It must
 * run off the main thread (the app uses {@code AppExecutors.network}). The address is checked
 * first, the wait is limited and so is the size of the answer, so a slow or huge server cannot
 * hang or flood the app. Every failure is an IOException whose message can be shown to the user.
 */
public final class HttpFetcher {
    public static final int TIMEOUT_MS = 10_000;
    public static final int MAX_BYTES = 600_000;

    private HttpFetcher() {
    }

    @NonNull
    public static String get(@NonNull String address) throws IOException {
        final String safe;
        try {
            safe = WebAddress.requireHttps(address);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(safe).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "GraphLab");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("The server answered " + code + " instead of 200.");
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > MAX_BYTES) {
                        throw new IOException("The answer is larger than " + MAX_BYTES + " bytes.");
                    }
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}

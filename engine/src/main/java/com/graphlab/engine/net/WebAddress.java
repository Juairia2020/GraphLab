package com.graphlab.engine.net;

import java.net.URI;
import java.net.URISyntaxException;

/** Checks an address before the app sends a request to it. Only plain https addresses are accepted. */
public final class WebAddress {
    public static final int MAX_LENGTH = 300;

    private WebAddress() {
    }

    /**
     * Returns the address without surrounding spaces.
     *
     * @throws IllegalArgumentException if it is not an https address with a host, or has a user name or password in it
     */
    public static String requireHttps(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("The web address is empty.");
        }
        String trimmed = address.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The web address is longer than " + MAX_LENGTH + " characters.");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("\"" + trimmed + "\" is not a valid web address.");
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only https:// addresses are allowed, but this one is \"" + trimmed + "\".");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("The web address has no host name.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("A web address with a user name or password is not allowed.");
        }
        return trimmed;
    }

    /** True while the address still holds the placeholder from the README, so no request should be made. */
    public static boolean isPlaceholder(String address) {
        return address == null || address.isBlank() || address.contains("YOUR_GITHUB_NAME");
    }
}

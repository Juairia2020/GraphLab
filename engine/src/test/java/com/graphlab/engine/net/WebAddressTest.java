package com.graphlab.engine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebAddressTest {
    @Test
    public void acceptsAnHttpsAddressAndTrimsIt() {
        assertEquals("https://example.org/a/b.json", WebAddress.requireHttps("  https://example.org/a/b.json \n"));
        assertEquals("HTTPS://Example.org", WebAddress.requireHttps("HTTPS://Example.org"));
    }

    @Test
    public void refusesEverythingElse() {
        for (String bad : new String[] {null, "", "   ", "http://example.org/x", "ftp://example.org", "file:///etc/passwd",
                "example.org/x", "https://", "https:///path", "https://user:pw@example.org/x", "https://exa mple.org",
                "javascript:alert(1)", "https://example.org/" + "a".repeat(WebAddress.MAX_LENGTH)}) {
            assertThrows("should refuse: " + bad, IllegalArgumentException.class, () -> WebAddress.requireHttps(bad));
        }
    }

    @Test
    public void theMessageNamesTheProblem() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> WebAddress.requireHttps("http://example.org"))
                .getMessage().contains("https"));
    }

    @Test
    public void recognisesThePlaceholderFromTheReadme() {
        assertTrue(WebAddress.isPlaceholder(null));
        assertTrue(WebAddress.isPlaceholder(""));
        assertTrue(WebAddress.isPlaceholder("https://raw.githubusercontent.com/YOUR_GITHUB_NAME/graphlab-samples/main/sample-library.json"));
        assertFalse(WebAddress.isPlaceholder("https://raw.githubusercontent.com/someone/graphlab-samples/main/sample-library.json"));
    }
}

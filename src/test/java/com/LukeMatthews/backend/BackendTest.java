package com.LukeMatthews.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackendTest {

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertDoesNotThrow(() -> Backend.of("http://backend-1:80"));
        assertDoesNotThrow(() -> Backend.of("https://example.com"));
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException.class, () -> Backend.of("htp://x"));
    }

    @Test
    void rejectsMissingScheme() {
        assertThrows(IllegalArgumentException.class, () -> Backend.of("localhost:9001"));
    }

    @Test
    void rejectsHostnameWithUnderscore() {
        // URI parser can't extract a host, so getHost() is null
        assertThrows(IllegalArgumentException.class, () -> Backend.of("http://backend_3:80"));
    }
}
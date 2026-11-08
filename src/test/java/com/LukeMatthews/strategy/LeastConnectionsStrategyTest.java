package com.LukeMatthews.strategy;

import com.LukeMatthews.backend.Backend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeastConnectionsStrategyTest {
    private static Backend withConnections(String url, int active) {
        Backend backend = Backend.of(url);
        for (int i = 0; i < active; i++) {
            backend.connectionOpened();
        }
        return backend;
    }

    @Test
    void picksTheBackendWithTheFewestActiveConnections() {
        var a = withConnections("http://a:80", 2);
        var b = withConnections("http://b:80", 0);
        var c = withConnections("http://c:80", 1);

        var chosen = new LeastConnectionsStrategy().select(List.of(a, b, c));

        assertSame(b, chosen.orElseThrow());
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidates() {
        assertTrue(new LeastConnectionsStrategy().select(List.of()).isEmpty());
    }

    @Test
    void rotatesAmongBackendsThatAreTied() {
        var strategy = new LeastConnectionsStrategy();
        var a = Backend.of("http://a:80");
        var b = Backend.of("http://b:80");
        var c = Backend.of("http://c:80");
        var backends = List.of(a, b, c);

        assertSame(a, strategy.select(backends).orElseThrow());
        assertSame(b, strategy.select(backends).orElseThrow());
        assertSame(c, strategy.select(backends).orElseThrow());
        assertSame(a, strategy.select(backends).orElseThrow());
    }

    @Test
    void onlyRotatesAmongTheLeastLoadedBackends() {
        var strategy = new LeastConnectionsStrategy();
        var a = withConnections("http://a:80", 0);
        var b = withConnections("http://b:80", 0);
        var busy = withConnections("http://busy:80", 5);
        var backends = List.of(a, b, busy);

        assertSame(a, strategy.select(backends).orElseThrow());
        assertSame(b, strategy.select(backends).orElseThrow());
        assertSame(a, strategy.select(backends).orElseThrow());
    }

    @Test
    void keepsLoadEvenWhenConnectionsAreNeverReleased() {
        var strategy = new LeastConnectionsStrategy();
        var backends = List.of(
                Backend.of("http://a:80"), Backend.of("http://b:80"), Backend.of("http://c:80"));

        for (int i = 0; i < 9; i++) {
            strategy.select(backends).orElseThrow().connectionOpened();
        }

        backends.forEach(backend -> assertEquals(3, backend.activeConnections()));
    }

    @Test
    void returnsToABackendOnceItsConnectionsAreReleased() {
        var strategy = new LeastConnectionsStrategy();
        var busy = withConnections("http://busy:80", 1);
        var idle = withConnections("http://idle:80", 1);
        var backends = List.of(busy, idle);

        idle.connectionOpened();                          // idle now has 2, busy has 1
        assertSame(busy, strategy.select(backends).orElseThrow());

        busy.connectionOpened();
        busy.connectionOpened();                          // busy now has 3, idle has 2
        assertSame(idle, strategy.select(backends).orElseThrow());

        busy.connectionClosed();
        busy.connectionClosed();                          // busy back to 1
        assertSame(busy, strategy.select(backends).orElseThrow());
    }
}
package com.LukeMatthews.proxy;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.LukeMatthews.health.HealthPolicy;
import com.LukeMatthews.strategy.LoadBalancingStrategy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHandlerRetryTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer liveServer;
    private HttpServer proxyServer;

    @BeforeEach
    void startLiveBackend() throws IOException {
        liveServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        liveServer.createContext("/", exchange -> {
            byte[] body = "alive".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        liveServer.start();
    }

    @AfterEach
    void stopServers() {
        liveServer.stop(0);
        if (proxyServer != null) {
            proxyServer.stop(0);
        }
    }

    private Backend live() {
        return Backend.of("http://127.0.0.1:" + liveServer.getAddress().getPort());
    }

    /** A backend whose port was open a moment ago, so connections are refused. */
    private static Backend dead() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return Backend.of("http://127.0.0.1:" + port);
    }

    private URI startProxy(List<Backend> backends, int maxAttempts) throws IOException {
        LoadBalancingStrategy firstCandidate = candidates -> candidates.stream().findFirst();
        var handler = new ProxyHandler(new BackendPool(backends), firstCandidate,
                new HealthPolicy(1, 1), maxAttempts);

        proxyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxyServer.createContext("/", handler);
        proxyServer.start();
        return URI.create("http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/");
    }

    private HttpResponse<String> send(URI uri, String method) throws Exception {
        HttpRequest.BodyPublisher body = method.equals("POST")
                ? HttpRequest.BodyPublishers.ofString("payload")
                : HttpRequest.BodyPublishers.noBody();
        return client.send(HttpRequest.newBuilder(uri).method(method, body).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getIsRetriedOnAnotherBackendWhenConnectionFails() throws Exception {
        URI proxy = startProxy(List.of(dead(), live()), 3);

        HttpResponse<String> response = send(proxy, "GET");

        assertEquals(200, response.statusCode());
        assertEquals("alive", response.body());
    }

    @Test
    void postIsNotRetried() throws Exception {
        URI proxy = startProxy(List.of(dead(), live()), 3);

        HttpResponse<String> response = send(proxy, "POST");

        assertEquals(502, response.statusCode());
    }

    @Test
    void returns502WhenEveryAttemptFails() throws Exception {
        URI proxy = startProxy(List.of(dead(), dead()), 3);

        assertEquals(502, send(proxy, "GET").statusCode());
    }

    @Test
    void stopsAfterMaxAttempts() throws Exception {
        // Two dead backends come first, with only 2 attempts live is not reached.
        URI proxy = startProxy(List.of(dead(), dead(), live()), 2);

        assertEquals(502, send(proxy, "GET").statusCode());
    }

    @Test
    void returns503WhenNoBackendIsHealthy() throws Exception {
        Backend only = live();
        only.markUnhealthy();
        URI proxy = startProxy(List.of(only), 3);

        assertEquals(503, send(proxy, "GET").statusCode());
    }

    @Test
    void failedBackendIsReportedToHealthPolicy() throws Exception {
        Backend dead = dead();
        URI proxy = startProxy(List.of(dead, live()), 3);

        send(proxy, "GET");

        assertFalse(dead.isHealthy());
    }
    @Test
    void recordsPerBackendStatsForEachAttempt() throws Exception {
        Backend dead = dead();
        Backend live = live();
        URI proxy = startProxy(List.of(dead, live), 3);

        send(proxy, "GET");

        assertEquals(1, dead.stats().snapshot().connectionErrors());
        assertEquals(0, dead.stats().snapshot().responses());
        assertEquals(1, live.stats().snapshot().responses());
        assertEquals(0, live.stats().snapshot().connectionErrors());
    }
}
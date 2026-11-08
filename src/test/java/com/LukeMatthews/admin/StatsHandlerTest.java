package com.LukeMatthews.admin;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsHandlerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private URI start(List<Backend> backends, String strategy) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stats", new StatsHandler(new BackendPool(backends), strategy));
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stats");
    }

    @Test
    void reportsEachBackendWithItsStateAndCounters() throws Exception {
        Backend a = Backend.of("http://a:80");
        Backend b = Backend.of("http://b:80");
        b.markUnhealthy();
        a.connectionOpened();
        a.stats().recordResponse(200, 4_000_000L);
        b.stats().recordConnectionError();

        URI uri = start(List.of(a, b), "round-robin");
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                BodyHandlers.ofString());
        String body = response.body();

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(body.contains("\"strategy\": \"round-robin\""));
        assertTrue(body.contains("\"healthyBackends\": 1"));
        assertTrue(body.contains("\"totalBackends\": 2"));
        assertTrue(body.contains("\"url\": \"http://a:80\""));
        assertTrue(body.contains("\"healthy\": false"));
        assertTrue(body.contains("\"activeConnections\": 1"));
        assertTrue(body.contains("\"connectionErrors\": 1"));
        assertTrue(body.contains("\"avgLatencyMs\": 4.00"));
    }

    @Test
    void rejectsMethodsOtherThanGet() throws Exception {
        URI uri = start(List.of(Backend.of("http://a:80")), "round-robin");

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void escapesSpecialCharactersInStrings() {
        String json = StatsHandler.render("a\"b\\c", 0, List.of());

        assertTrue(json.contains("\"strategy\": \"a\\\"b\\\\c\""));
    }

    @Test
    void rendersAnEmptyBackendListAsAnEmptyArray() {
        String json = StatsHandler.render("round-robin", 5, List.of());

        assertTrue(json.contains("\"backends\": []"));
        assertTrue(json.contains("\"uptimeSeconds\": 5"));
    }
}
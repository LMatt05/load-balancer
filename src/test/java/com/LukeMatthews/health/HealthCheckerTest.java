package com.LukeMatthews.health;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckerTest {
    private HttpServer server;
    private HealthChecker checker;
    private final AtomicInteger status = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void cleanUp() {
        if (checker != null) {
            checker.close();
        }
        server.stop(0);
    }

    private HealthChecker checkerFor(Backend backend) {
        return new HealthChecker(new BackendPool(List.of(backend)), new HealthPolicy(1, 1),
                "/", Duration.ofMillis(50), Duration.ofSeconds(1));
    }

    @Test
    void marksBackendUnhealthyOnServerErrorsAndRecoversAfterwards() throws Exception {
        Backend backend = Backend.of("http://127.0.0.1:" + server.getAddress().getPort());
        checker = checkerFor(backend);
        checker.start();

        status.set(500);
        awaitCondition(() -> !backend.isHealthy());

        status.set(200);
        awaitCondition(backend::isHealthy);
    }

    @Test
    void marksBackendUnhealthyWhenConnectionIsRefused() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        Backend backend = Backend.of("http://127.0.0.1:" + closedPort);
        checker = checkerFor(backend);
        checker.start();

        awaitCondition(() -> !backend.isHealthy());
    }

    @Test
    void treatsClientErrorsAsHealthy() throws Exception {
        status.set(404);
        Backend backend = Backend.of("http://127.0.0.1:" + server.getAddress().getPort());
        checker = checkerFor(backend);
        checker.start();

        Thread.sleep(300);   // several probe cycles at a 50ms interval

        assertTrue(backend.isHealthy());
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Condition not met within 5 seconds");
    }
}
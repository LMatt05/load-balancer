package com.LukeMatthews.backend;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class BackendStatsTest {
    private static final long MS = 1_000_000L;

    @Test
    void startsAtZero() {
        var snapshot = new BackendStats().snapshot();

        assertEquals(0, snapshot.responses());
        assertEquals(0, snapshot.serverErrors());
        assertEquals(0, snapshot.connectionErrors());
        assertEquals(0.0, snapshot.avgLatencyMs());
        assertEquals(0.0, snapshot.maxLatencyMs());
    }

    @Test
    void tracksCountAverageAndMaxLatency() {
        var stats = new BackendStats();
        stats.recordResponse(200, 10 * MS);
        stats.recordResponse(200, 30 * MS);

        var snapshot = stats.snapshot();

        assertEquals(2, snapshot.responses());
        assertEquals(20.0, snapshot.avgLatencyMs(), 0.001);
        assertEquals(30.0, snapshot.maxLatencyMs(), 0.001);
    }

    @Test
    void countsOnlyServerErrorsAsServerErrors() {
        var stats = new BackendStats();
        stats.recordResponse(500, MS);
        stats.recordResponse(503, MS);
        stats.recordResponse(404, MS);
        stats.recordResponse(200, MS);

        var snapshot = stats.snapshot();

        assertEquals(4, snapshot.responses());
        assertEquals(2, snapshot.serverErrors());
    }

    @Test
    void connectionErrorsDoNotAffectResponsesOrLatency() {
        var stats = new BackendStats();
        stats.recordConnectionError();
        stats.recordConnectionError();

        var snapshot = stats.snapshot();

        assertEquals(2, snapshot.connectionErrors());
        assertEquals(0, snapshot.responses());
        assertEquals(0.0, snapshot.avgLatencyMs());
    }

    @Test
    void staysAccurateUnderConcurrentUpdates() {
        var stats = new BackendStats();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1000; i++) {
                executor.submit(() -> stats.recordResponse(200, MS));
            }
        }

        var snapshot = stats.snapshot();
        assertEquals(1000, snapshot.responses());
        assertEquals(1.0, snapshot.avgLatencyMs(), 0.001);
    }
}
package com.LukeMatthews.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategiesTest {
    @Test
    void createsRoundRobin() {
        assertInstanceOf(RoundRobinStrategy.class, Strategies.create("round-robin"));
    }

    @Test
    void createsLeastConnectionsIgnoringCaseAndWhitespace() {
        assertInstanceOf(LeastConnectionsStrategy.class, Strategies.create("  Least-Connections "));
    }

    @Test
    void rejectsUnknownNamesWithAHelpfulMessage() {
        var error = assertThrows(IllegalArgumentException.class, () -> Strategies.create("random"));

        assertTrue(error.getMessage().contains("round-robin"));
        assertTrue(error.getMessage().contains("least-connections"));
    }
}
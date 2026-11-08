package com.LukeMatthews.health;

import com.LukeMatthews.backend.Backend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthPolicyTest {
    private final HealthPolicy policy = new HealthPolicy(2, 2);
    private final Backend backend = Backend.of("http://a:80");

    @Test
    void staysHealthyBelowFailureThreshold() {
        policy.onFailure(backend);

        assertTrue(backend.isHealthy());
    }

    @Test
    void becomesUnhealthyAfterConsecutiveFailures() {
        policy.onFailure(backend);
        policy.onFailure(backend);

        assertFalse(backend.isHealthy());
    }

    @Test
    void successBetweenFailuresResetsTheStreak() {
        policy.onFailure(backend);
        policy.onSuccess(backend);
        policy.onFailure(backend);

        assertTrue(backend.isHealthy());
    }

    @Test
    void recoversAfterConsecutiveSuccesses() {
        backend.markUnhealthy();

        policy.onSuccess(backend);
        assertFalse(backend.isHealthy());

        policy.onSuccess(backend);
        assertTrue(backend.isHealthy());
    }

    @Test
    void failureDuringRecoveryRestartsTheCount() {
        backend.markUnhealthy();

        policy.onSuccess(backend);
        policy.onFailure(backend);
        policy.onSuccess(backend);
        assertFalse(backend.isHealthy());

        policy.onSuccess(backend);
        assertTrue(backend.isHealthy());
    }

    @Test
    void rejectsThresholdsBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new HealthPolicy(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new HealthPolicy(2, 0));
    }
}
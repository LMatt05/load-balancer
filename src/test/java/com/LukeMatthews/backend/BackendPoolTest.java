package com.LukeMatthews.backend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendPoolTest {

    @Test
    void healthyExcludesBackendsMarkedUnhealthy() {
        var a = Backend.of("http://a:80");
        var b = Backend.of("http://b:80");
        var pool = new BackendPool(List.of(a,b));

        b.markUnhealthy();

        assertEquals(List.of(a), pool.healthy());
        assertEquals(2, pool.all().size());
    }

    @Test
    void backendRejoinsHealthyListOnceHealthyAgain(){
        var a = Backend.of("http://a:80");
        var pool = new BackendPool(List.of(a));

        a.markUnhealthy();
        assertTrue(pool.healthy().isEmpty());

        a.markHealthy();
        assertEquals(List.of(a), pool.healthy());
    }

}
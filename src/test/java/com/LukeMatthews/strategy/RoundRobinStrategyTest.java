package com.LukeMatthews.strategy;

import com.LukeMatthews.backend.Backend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {
    private final Backend a = Backend.of("http://a:80");
    private final Backend b = Backend.of("http://b:80");
    private final Backend c = Backend.of("http://c:80");

    @Test
    void cyclesThroughBackendsinOrder(){
        var strategy = new RoundRobinStrategy();
        var backends = List.of(a,b,c);

        assertSame(a, strategy.select(backends).orElseThrow());
        assertSame(b, strategy.select(backends).orElseThrow());
        assertSame(c, strategy.select(backends).orElseThrow());
        assertSame(a, strategy.select(backends).orElseThrow());
    }

    @Test
    void returnsEmptyWhenNoCandidates(){
        var strategy = new RoundRobinStrategy();

        assertTrue(strategy.select(List.of()).isEmpty());
    }

    @Test
    void singleBackendAlwaysChosen(){
        var strategy = new RoundRobinStrategy();

        for (int i = 0; i<5; i++){
            assertSame(a, strategy.select(List.of(a)).orElseThrow());
        }
    }

    @Test
    void distributesEvenlyUnderConcurrentLoad() throws Exception{
        var strategy = new RoundRobinStrategy();
        var backends = List.of(a,b,c);
        Map<Backend, AtomicInteger> counts = new ConcurrentHashMap<>();
        backends.forEach(be -> counts.put(be, new AtomicInteger()));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0,3000)
                    .mapToObj(i -> executor.submit(() ->
                            counts.get(strategy.select(backends).orElseThrow()).incrementAndGet()))
                    .toList();
            for (var future : futures) {
                future.get();
            }
        }
        backends.forEach(be -> assertEquals(1000, counts.get(be).get()));
    }
}
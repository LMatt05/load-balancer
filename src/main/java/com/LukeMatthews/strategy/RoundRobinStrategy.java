package com.LukeMatthews.strategy;

import com.LukeMatthews.backend.Backend;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Optional<Backend> select(List<Backend> candidates){
        if (candidates.isEmpty()){
            return Optional.empty();
        }
        // floorMod keeps index valid even after counter overflows to negative value
        int index = Math.floorMod(counter.getAndIncrement(), candidates.size());
        return Optional.of(candidates.get(index));
    }
}

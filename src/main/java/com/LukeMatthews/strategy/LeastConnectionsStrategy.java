package com.LukeMatthews.strategy;

import com.LukeMatthews.backend.Backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Chooses backend with fewest active connecctions, rotating among ties. */
public class LeastConnectionsStrategy implements LoadBalancingStrategy{
    private final AtomicInteger tieBreaker = new AtomicInteger();

    @Override
    public Optional<Backend> select(List<Backend> candidates){
        if(candidates.isEmpty()){
            return Optional.empty();
        }

        // Read each count once so the comparison is consistent within call
        int[] counts = new int[candidates.size()];
        int min = Integer.MAX_VALUE;
        for (int i = 0; i< counts.length; i++){
            counts[i] = candidates.get(i).activeConnections();
            min = Math.min(min, counts[i]);
        }

        List<Backend> leastLoaded = new ArrayList<>();
        for (int i = 0; i < counts.length; i++){
            if(counts[i] == min) {
                leastLoaded.add(candidates.get(i));
            }
        }
        int index = Math.floorMod(tieBreaker.getAndIncrement(), leastLoaded.size());
        return Optional.of(leastLoaded.get(index));
    }
}

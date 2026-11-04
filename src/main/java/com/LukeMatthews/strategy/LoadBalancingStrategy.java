package com.LukeMatthews.strategy;

import com.LukeMatthews.backend.Backend;

import java.util.List;
import java.util.Optional;

public interface LoadBalancingStrategy {
    /**
     * Chooses on backend from the given candidates.
     *
     * @param candidates backends elgible to receive the request (filtered for health)
     * @return the chosen backend, or empty if no candidates
     */
    Optional<Backend> select(List<Backend> candidates);
}

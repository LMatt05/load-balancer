package com.LukeMatthews.health;

import com.LukeMatthews.backend.Backend;

/** Decides when a backend is healthy or unhealhty, based on consecutive results. */
public record HealthPolicy(int unhealthyThreshold, int healthyThreshold) {
    public HealthPolicy{
        if(unhealthyThreshold < 1 || healthyThreshold < 1 ){
            throw new IllegalArgumentException("Thresholds must be at least 1");
        }
    }

    public static HealthPolicy defaults(){
        return new HealthPolicy(2,2);
    }

    public void onFailure(Backend backend){
        if(backend.recordFailure() >= unhealthyThreshold){
            backend.markUnhealthy();
        }
    }

    public void onSuccess(Backend backend){
        if (backend.isHealthy()){
            backend.resetFailures();
            return;
        }
        if(backend.recordSuccess() >= healthyThreshold){
            backend.markHealthy();
        }
    }
}

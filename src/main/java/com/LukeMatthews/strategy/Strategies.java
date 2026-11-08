package com.LukeMatthews.strategy;

import java.util.Locale;

/** creates a strategy from configuration name. */
public final class Strategies {
    private Strategies(){
    }

    public static LoadBalancingStrategy create(String name){
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "round-robin" -> new RoundRobinStrategy();
            case "least-connections" -> new LeastConnectionsStrategy();
            default -> throw new IllegalArgumentException(
                    "Unkown strategy '" + name + "'. Supported: round-robin, least-connections"
            );
        };
    }
}

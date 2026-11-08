package com.LukeMatthews.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record BalancerConfig(int port, List<String> backendUrls, String healthPath, Duration healthInterval) {

    public static BalancerConfig fromEnvironment() {
        Map<String, String> env = System.getenv();

        int port = Integer.parseInt(env.getOrDefault("BALANCER_PORT", "8080"));

        String raw = env.get("BALANCER_BACKENDS");
        if (raw == null || raw.isBlank()){
            throw new IllegalStateException("BALANCER_BACKENDS must be set, e.g http://localhost:9001,http://localhost:9002");
        }
        List<String> urls = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        String healthPath = env.getOrDefault("BALANCER_HEALTH_PATH", "/");
        if(!healthPath.startsWith("/")){
            throw new IllegalStateException("BALANCER_HEALTH_PATH must start with '/'");
        }

        long intervalMs = Long.parseLong(env.getOrDefault("BALANCER_HEALTH_INTERVAL_MS", "3000"));
        if (intervalMs <= 0){
            throw new IllegalStateException("BALANCER_HEALTH_INTERVAL_MS must be positive");
        }

        return new BalancerConfig(port, urls, healthPath, Duration.ofMillis(intervalMs));

    }
}

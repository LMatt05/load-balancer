package com.LukeMatthews.config;

import java.util.Arrays;
import java.util.List;

public record BalancerConfig(int port, List<String> backendUrls) {

    public static BalancerConfig fromEnvironment() {
        int port = Integer.parseInt(System.getenv().getOrDefault("BALANCER_PORT", "8080"));

        String raw = System.getenv("BALANCER_BACKENDS");
        if (raw == null || raw.isBlank()){
            throw new IllegalStateException("BALANCER_BACKENDS must be set, e.g http://localhost:9001,http://localhost:9002");
        }
        List<String> urls = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return new BalancerConfig(port, urls);

    }
}

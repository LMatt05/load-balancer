package com.LukeMatthews.admin;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.LukeMatthews.backend.BackendStats;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Serves a JSON snapshot of balancer and per-backend stats. */
public final class StatsHandler implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(StatsHandler.class.getName());

    private final BackendPool pool;
    private final String strategyName;
    private final Instant startedAt = Instant.now();

    public StatsHandler(BackendPool pool, String strategyName) {
        this.pool = pool;
        this.strategyName = strategyName;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
            byte[] body = render(strategyName, uptimeSeconds, pool.all()).getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "I/O error serving stats: {0}", e.toString());
        }
    }

    static String render(String strategyName, long uptimeSeconds, List<Backend> backends) {
        long healthy = backends.stream().filter(Backend::isHealthy).count();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"strategy\": ").append(quote(strategyName)).append(",\n");
        json.append("  \"uptimeSeconds\": ").append(uptimeSeconds).append(",\n");
        json.append("  \"healthyBackends\": ").append(healthy).append(",\n");
        json.append("  \"totalBackends\": ").append(backends.size()).append(",\n");
        json.append("  \"backends\": [");
        for (int i = 0; i < backends.size(); i++) {
            Backend backend = backends.get(i);
            BackendStats.Snapshot stats = backend.stats().snapshot();

            json.append(i == 0 ? "\n" : ",\n");
            json.append("    {\n");
            json.append("      \"url\": ").append(quote(backend.uri().toString())).append(",\n");
            json.append("      \"healthy\": ").append(backend.isHealthy()).append(",\n");
            json.append("      \"activeConnections\": ").append(backend.activeConnections()).append(",\n");
            json.append("      \"responses\": ").append(stats.responses()).append(",\n");
            json.append("      \"serverErrors\": ").append(stats.serverErrors()).append(",\n");
            json.append("      \"connectionErrors\": ").append(stats.connectionErrors()).append(",\n");
            json.append("      \"avgLatencyMs\": ").append(format(stats.avgLatencyMs())).append(",\n");
            json.append("      \"maxLatencyMs\": ").append(format(stats.maxLatencyMs())).append("\n");
            json.append("    }");
        }
        json.append(backends.isEmpty() ? "]\n" : "\n  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

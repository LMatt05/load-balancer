package com.LukeMatthews.proxy;

import com.LukeMatthews.health.HealthPolicy;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.LukeMatthews.strategy.LoadBalancingStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class ProxyHandler implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(ProxyHandler.class.getName());

    /** Headers that apply to a single connection must not be forwarded by a proxy */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    /** Header the JDK HttpClient sets itself and refuses to be set */
    private static final Set<String> MANAGED_BY_CLIENT = Set.of("host", "content-length", "expect");

    private static final Set<String> RETRYABLE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final BackendPool pool;
    private final LoadBalancingStrategy strategy;
    private final HealthPolicy policy;
    private final int maxAttempts;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ProxyHandler(BackendPool pool, LoadBalancingStrategy strategy, HealthPolicy policy, int maxAttempts){
        if (maxAttempts < 1){
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.pool = pool;
        this.strategy = strategy;
        this.policy = policy;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void handle(HttpExchange exchange){
        try (exchange) {
            proxy(exchange);
        } catch (IOException e){
            LOG.log(System.Logger.Level.DEBUG, "I/O error while proxying: {0}", e.toString());
        }
    }

    private void proxy(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();

        int attempts = RETRYABLE_METHODS.contains(exchange.getRequestMethod()) ? maxAttempts: 1;
        Set<Backend> tried = new HashSet<>();

        for (int attempt = 1; attempt <= attempts; attempt++){
            List<Backend> candidates = pool.healthy().stream()
                    .filter(backend -> !tried.contains(backend))
                    .toList();

            Optional<Backend> chosen = strategy.select(candidates);
            if(chosen.isEmpty()){
                break;
            }
            Backend backend = chosen.get();
            tried.add(backend);

            backend.connectionOpened();
            try{
                HttpResponse<InputStream> response;
                try{
                    HttpRequest request = buildRequest(exchange, backend, body);
                    long started = System.nanoTime();
                    response = client.send(request, BodyHandlers.ofInputStream());
                    backend.stats().recordResponse(response.statusCode(),System.nanoTime() - started);
                } catch (IllegalArgumentException e){
                    LOG.log(System.Logger.Level.WARNING, "Could not build request for {0}: {1}", backend.uri(), e.toString());
                    respond(exchange, 400, "Bad request");
                    return;
                } catch(IOException e) {
                    LOG.log(System.Logger.Level.WARNING, "Attmept {0}/{1} to {2} failed: {3}", attempt, attempts, backend.uri(), e.toString());
                    backend.stats().recordConnectionError();
                    policy.onFailure(backend);
                    continue;
                } catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    respond(exchange, 502, "Bad gateway");
                    return;
                }
                policy.onSuccess(backend);
                writeResponse(exchange, response);
                return;
            } finally{
                backend.connectionClosed();
            }
        }
        if (tried.isEmpty()){
            respond(exchange, 503, "No healthy backends available");
        } else {
            respond(exchange, 502, "Bad gateway");
        }
    }
    private HttpRequest buildRequest(HttpExchange exchange, Backend backend, byte[] body) throws IOException {
        URI target = backend.resolve(exchange.getRequestURI().toString());

        HttpRequest.BodyPublisher publisher =
                body.length == 0 ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(REQUEST_TIMEOUT)
                .method(exchange.getRequestMethod(), publisher);

        exchange.getRequestHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower) || MANAGED_BY_CLIENT.contains(lower)
                    || lower.equals("x-forwarded-for") || lower.equals("x-forwarded-host")) {
                return;
            }
            values.forEach(value -> builder.header(name, value));
        });

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String existing = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        builder.header("X-Forwarded-For", existing == null ? clientIp : existing + ", " + clientIp);

        String originalHost = exchange.getRequestHeaders().getFirst("Host");
        if(originalHost != null){
            builder.header("X-Forwarded-Host", originalHost);
        }
        return builder.build();
    }

    private void writeResponse(HttpExchange exchange, HttpResponse<InputStream> response) throws IOException{
        Headers out = exchange.getResponseHeaders();
        response.headers().map().forEach((name, values) ->{
            String lower = name.toLowerCase(Locale.ROOT);
            if(lower.startsWith(":") || HOP_BY_HOP.contains(lower) || lower.equals("content-length")){
                return;
            }
            values.forEach(value -> out.add(name, value));
        });

        int status = response.statusCode();
        boolean noBody = exchange.getRequestMethod().equals("HEAD")
                || status == 204 || status == 304 || status / 100 == 1;

        // -1 = no body, 0 = chunked (length unknown), >0 = fixed length
        long length = noBody ? -1
                : response.headers().firstValueAsLong("content-length")
                        .stream().map(l -> l == 0 ? -1L : l).findFirst().orElse(0L);

        exchange.sendResponseHeaders(status, length);

        try(InputStream in = response.body()){
            if(length != -1){
                try(OutputStream os = exchange.getResponseBody()){
                    in.transferTo(os);
                }
            }
        }
    }

    private static void respond (HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = (message+ "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()){
            os.write(bytes);
        }
    }
}

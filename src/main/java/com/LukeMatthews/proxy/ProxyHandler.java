package com.LukeMatthews.proxy;

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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ProxyHandler implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(ProxyHandler.class.getName());

    /** Headers that apply to a single connection must not be forwarded by a proxy */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    /** Header the JDK HttpClient sets itself and refuses to be set */
    private static final Set<String> MANAGED_BY_CLIENT = Set.of("host", "content-length", "expect");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final BackendPool pool;
    private final LoadBalancingStrategy strategy;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ProxyHandler(BackendPool pool, LoadBalancingStrategy strategy){
        this.pool = pool;
        this.strategy = strategy;
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
        Optional<Backend> chosen = strategy.select(pool.healthy());
        if(chosen.isEmpty()){
            respond(exchange, 503, "No healthy backends available");
            return;
        }
        Backend backend = chosen.get();

        backend.connectionOpened();
        try{
            HttpResponse<InputStream> response;
            try{
                response = client.send(buildRequest(exchange, backend), BodyHandlers.ofInputStream());
            } catch (IllegalArgumentException e){
                LOG.log(System.Logger.Level.WARNING, "Could not build request for {0}: {1}", backend.uri(), e.toString());
                respond(exchange, 400, "Bad request");
                return;
            } catch(IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Request to {0} failed: {1}", backend.uri(), e.toString());
                respond(exchange, 502, "Bad gateway");
                return;
            } catch(InterruptedException e){
                Thread.currentThread().interrupt();
                respond(exchange, 502, "Bad gateway");
                return;
            }
            writeResponse(exchange, response);
        } finally{
            backend.connectionClosed();
        }
    }
    private HttpRequest buildRequest(HttpExchange exchange, Backend backend) throws IOException {
        String base = backend.uri().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI target = URI.create(base + exchange.getRequestURI());

        byte[] body = exchange.getRequestBody().readAllBytes();
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

package com.LukeMatthews.health;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically checks every backend and reports results to HealthPolicy. */
public final class HealthChecker implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HealthChecker.class.getName());

    private final BackendPool pool;
    private final HealthPolicy policy;
    private final String path;
    private final Duration interval;
    private final Duration timeout;
    private final HttpClient client;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "health-checker");
                thread.setDaemon(true);
                return thread;
            });

    public HealthChecker(BackendPool pool, HealthPolicy policy, String path,
                         Duration interval, Duration timeout){
        this.pool = pool;
        this.policy = policy;
        this.path = path;
        this.interval = interval;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
    public void start(){
        scheduler.scheduleWithFixedDelay(this::checkAll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void checkAll(){
        // check all in parallel so slow backends dont delay.
        // closing executor waits for all checks to finish.
        try(var executor = Executors.newVirtualThreadPerTaskExecutor()){
            for (Backend backend : pool.all()){
                executor.submit(() -> probe(backend));
            }
        }
    }

    private void probe(Backend backend){
        try{
            HttpRequest request = HttpRequest.newBuilder(backend.resolve(path))
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if(response.statusCode() < 500){
                policy.onSuccess(backend);
            }else{
                LOG.log(System.Logger.Level.DEBUG, "Probe to {0} returned {1}",
                        backend.uri(), response.statusCode());
                policy.onFailure(backend);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Probe to {0} failed: {1}", backend.uri(), e.toString());
            policy.onFailure(backend);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close(){
        scheduler.shutdownNow();
    }
}

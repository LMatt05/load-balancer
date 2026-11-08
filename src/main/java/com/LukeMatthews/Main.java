package com.LukeMatthews;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.LukeMatthews.config.BalancerConfig;
import com.LukeMatthews.health.HealthChecker;
import com.LukeMatthews.health.HealthPolicy;
import com.LukeMatthews.proxy.ProxyHandler;
import com.LukeMatthews.strategy.LoadBalancingStrategy;
import com.LukeMatthews.strategy.Strategies;
import com.sun.net.httpserver.HttpServer;
import com.LukeMatthews.admin.StatsHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException{
        BalancerConfig config = BalancerConfig.fromEnvironment();

        BackendPool pool = new BackendPool(
                config.backendUrls().stream().map(Backend::of).toList());
        HealthPolicy policy = HealthPolicy.defaults();

        HealthChecker checker = new HealthChecker(
                pool, policy, config.healthPath(), config.healthInterval(), Duration.ofSeconds(2));
        checker.start();


        LoadBalancingStrategy strategy = Strategies.create(config.strategyName());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(pool, strategy, policy, config.maxAttempts()));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        HttpServer adminServer = HttpServer.create(new InetSocketAddress(config.adminPort()), 0);
        adminServer.createContext("/stats", new StatsHandler(pool, config.strategyName()));
        adminServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        adminServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->{
            checker.close();
            server.stop(1);
            adminServer.stop(1);
        }));
        LOG.log(System.Logger.Level.INFO,
                "Balancer listening on port {0} with {1} backends, strategy {2}", config.port(), pool.all().size(), config.strategyName());
    }
}
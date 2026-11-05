package com.LukeMatthews;

import com.LukeMatthews.backend.Backend;
import com.LukeMatthews.backend.BackendPool;
import com.LukeMatthews.config.BalancerConfig;
import com.LukeMatthews.proxy.ProxyHandler;
import com.LukeMatthews.strategy.RoundRobinStrategy;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException{
        BalancerConfig config = BalancerConfig.fromEnvironment();

        BackendPool pool = new BackendPool(
                config.backendUrls().stream().map(Backend::of).toList());

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(pool, new RoundRobinStrategy()));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        LOG.log(System.Logger.Level.INFO,
                "Balancer listening on port {0} with {1} backends", config.port(), pool.all().size());
    }
}
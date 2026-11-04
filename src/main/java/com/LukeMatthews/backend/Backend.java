package com.LukeMatthews.backend;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** a single upstream server, with the mutable state the balancer tracks about it. */
public final class Backend {
    private final URI uri;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicInteger activeConnections = new AtomicInteger();

    public Backend(URI uri){
        this.uri = Objects.requireNonNull(uri, "uri");
    }
    public static Backend of(String url){
        return new Backend(URI.create(url));
    }
    public URI uri(){
        return uri;
    }

    public boolean isHealthy(){
        return  healthy.get();
    }

    public void markHealthy(){
        healthy.set(true);
    }

    public void markUnhealthy(){
        healthy.set(false);
    }

    public int activeConnections() {
        return activeConnections.get();
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }
    public void connectionClosed(){
        activeConnections.decrementAndGet();
    }

    @Override
    public String toString(){
        return "Backend[" + uri + ", healthy=" + isHealthy() + "]";
    }
}

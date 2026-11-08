package com.LukeMatthews.backend;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** a single upstream server, with the mutable state the balancer tracks about it. */
public final class Backend {

    private static final System.Logger LOG = System.getLogger(Backend.class.getName());

    private final URI uri;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger();

    public Backend(URI uri){
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if(uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))){
            throw new IllegalArgumentException("Invalid backend URL: " + uri);
        }
        this.uri = uri;
    }
    public static Backend of(String url){
        return new Backend(URI.create(url));
    }
    public URI uri(){
        return uri;
    }

    /** full URL for a path (plus optional query) on this backend, e.g. "/health". */
    public URI resolve(String pathAndQuery){
        String base = uri.toString();
        if(base.endsWith("/")){
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + pathAndQuery);
    }

    public boolean isHealthy(){
        return  healthy.get();
    }

    /** @return  true if state changed */
    public boolean markHealthy(){
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        boolean changed = healthy.compareAndSet(false, true);
        if(changed){
            LOG.log(System.Logger.Level.INFO, "Backend {0} is healthy again", uri);
        }
        return changed;
    }

    /** @return true if the state changed */
    public boolean markUnhealthy(){
        consecutiveSuccesses.set(0);
        boolean changed = healthy.compareAndSet(true, false);
        if(changed){
            LOG.log(System.Logger.Level.WARNING, "Backend {0} marked unhealthy", uri);
        }
        return changed;
    }

    /** Records a failure and returns the current run of conescutive failure. */
    public int recordFailure(){
        consecutiveSuccesses.set(0);
        return consecutiveFailures.incrementAndGet();
    }

    /** Records a success while recovering and returns the current run of consecutive successes */
    public int recordSuccess(){
        consecutiveFailures.set(0);
        return consecutiveSuccesses.incrementAndGet();
    }

    public void resetFailures() {
        consecutiveFailures.set(0);
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

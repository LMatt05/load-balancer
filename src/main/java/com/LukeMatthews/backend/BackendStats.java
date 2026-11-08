package com.LukeMatthews.backend;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe request counters and latency figures for one backend. */
public final class BackendStats {

    private final LongAdder responses = new LongAdder();
    private final LongAdder serverErrors = new LongAdder();
    private final LongAdder connectionErrors = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong();

    /** Records a response received from the backend, whatever its status. */
    public void recordResponse(int statusCode, long latencyNanos){
        responses.increment();
        if(statusCode >= 500){
            serverErrors.increment();
        }
        totalLatencyNanos.add(latencyNanos);
        maxLatencyNanos.accumulateAndGet(latencyNanos, Math::max);
    }

    /** Records a failed attempt where no response was received at all. */
    public void recordConnectionError() {
        connectionErrors.increment();
    }

    /** A point in time view. fields are read separately, so figures can differ by in progress updates. */
    public Snapshot snapshot(){
        long count = responses.sum();
        double avgMs = count == 0 ? 0 : totalLatencyNanos.sum() / (double) count / 1_000_000.0;
        double maxMs = maxLatencyNanos.get() / 1_000_000.0;
        return new Snapshot(count, serverErrors.sum(), connectionErrors.sum(), avgMs, maxMs);
    }

    public record  Snapshot (long responses, long serverErrors, long connectionErrors, double avgLatencyMs, double maxLatencyMs){}
}

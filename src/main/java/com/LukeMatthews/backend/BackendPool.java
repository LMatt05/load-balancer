package com.LukeMatthews.backend;

import java.util.List;

/** fixed set of configured backends. Health is tracked on each backend*/
public final class BackendPool {
    private final List<Backend> backends;

    public BackendPool(List<Backend> backends){
        this.backends = List.copyOf(backends);
    }

    public List<Backend> all(){
        return  backends;
    }

    /** snapshot of the backends that are currently healthy. */
    public List<Backend> healthy(){
        return backends.stream().filter(Backend::isHealthy).toList();
    }
}

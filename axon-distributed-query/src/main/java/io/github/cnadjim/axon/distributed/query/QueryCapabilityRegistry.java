package io.github.cnadjim.axon.distributed.query;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class QueryCapabilityRegistry {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void register(String queryName) {
        counters.computeIfAbsent(queryName, name -> new AtomicInteger()).incrementAndGet();
    }

    public void unregister(String queryName) {
        counters.computeIfPresent(queryName, (name, counter) -> {
            int remaining = counter.decrementAndGet();
            return remaining <= 0 ? null : counter;
        });
    }

    public boolean isSupportedLocally(String queryName) {
        return counters.containsKey(queryName);
    }

    /** Immutable snapshot of the currently subscribed query names. */
    public Set<String> snapshot() {
        return Set.copyOf(counters.keySet());
    }
}


package io.github.cnadjim.axon.distributed.query;

import io.github.cnadjim.axon.distributed.query.message.QueryCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpringCloudQueryRouter {

    private static final Logger logger = LoggerFactory.getLogger(SpringCloudQueryRouter.class);

    private final DiscoveryClient discoveryClient;
    private final Registration localRegistration;
    private final HttpQueryBusConnector connector;
    private final Duration capabilityCacheTtl;
    private final ConcurrentHashMap<String, CachedCapabilities> cache = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobin = new AtomicInteger();

    public SpringCloudQueryRouter(DiscoveryClient discoveryClient,
                                  Registration localRegistration,
                                   HttpQueryBusConnector connector) {
        this.discoveryClient = discoveryClient;
        this.localRegistration = localRegistration;
        this.connector = connector;
        this.capabilityCacheTtl = Duration.ofSeconds(5);
    }

    /** Picks one remote instance capable of handling the given query (round-robin). */
    public Optional<ServiceInstance> findDestination(String queryName) {
        List<ServiceInstance> capable = findAllDestinations(queryName);
        if (capable.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(roundRobin.getAndIncrement(), capable.size());
        return Optional.of(capable.get(index));
    }

    /** All remote instances capable of handling the given query - used for {@code scatterGather}. */
    public List<ServiceInstance> findAllDestinations(String queryName) {
        List<ServiceInstance> result = new ArrayList<>();
        for (String serviceId : discoveryClient.getServices()) {
            for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                if (isLocalInstance(instance)) {
                    continue;
                }
                if (capabilitiesOf(instance).queryNames().contains(queryName)) {
                    result.add(instance);
                }
            }
        }
        return result;
    }

    private boolean isLocalInstance(ServiceInstance instance) {
        return localRegistration != null
                && localRegistration.getServiceId().equals(instance.getServiceId())
                && localRegistration.getHost().equals(instance.getHost())
                && localRegistration.getPort() == instance.getPort();
    }

    private QueryCapabilities capabilitiesOf(ServiceInstance instance) {
        String cacheKey = instance.getServiceId() + "@" + instance.getHost() + ":" + instance.getPort();
        CachedCapabilities cached = cache.get(cacheKey);
        if (cached != null && cached.isValid(capabilityCacheTtl)) {
            return cached.capabilities;
        }
        try {
            QueryCapabilities fetched = connector.fetchCapabilities(instance);
            if (fetched == null) {
                fetched = new QueryCapabilities(instance.getServiceId(), "unknown", java.util.Set.of());
            }
            cache.put(cacheKey, new CachedCapabilities(fetched, Instant.now()));
            return fetched;
        } catch (Exception e) {
            logger.warn("Could not fetch query capabilities from {} ({}): {}. Excluding it from this lookup.",
                    instance.getServiceId(), cacheKey, e.getMessage());
            cache.remove(cacheKey);
            return new QueryCapabilities(instance.getServiceId(), "unknown", java.util.Set.of());
        }
    }

    private record CachedCapabilities(QueryCapabilities capabilities, Instant fetchedAt) {
        boolean isValid(Duration ttl) {
            return Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}


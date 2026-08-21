package io.github.cnadjim.axon.distributed.query.message;

import java.util.Set;

/**
 * Advertises which query names a service instance can handle locally, so remote callers can
 * decide whether to route a query to it (PLAN.md §11.4).
 */
public record QueryCapabilities(String serviceId, String instanceId, Set<String> queryNames) {
}


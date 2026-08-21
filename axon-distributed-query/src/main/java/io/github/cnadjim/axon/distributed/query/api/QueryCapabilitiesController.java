package io.github.cnadjim.axon.distributed.query.api;

import io.github.cnadjim.axon.distributed.query.QueryCapabilityRegistry;
import io.github.cnadjim.axon.distributed.query.message.QueryCapabilities;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the query names this instance can handle locally, so remote callers can discover
 * whether to route a query here (PLAN.md §11.4).
 */
@RestController
public class QueryCapabilitiesController {

    public static final String CAPABILITIES_ENDPOINT = "/query-capabilities";

    private final QueryCapabilityRegistry registry;
    private final Registration registration;

    public QueryCapabilitiesController(QueryCapabilityRegistry registry, Registration registration) {
        this.registry = registry;
        this.registration = registration;
    }

    @GetMapping(path = CAPABILITIES_ENDPOINT)
    public QueryCapabilities capabilities() {
        return new QueryCapabilities(registration.getServiceId(), registration.getInstanceId(), registry.snapshot());
    }
}


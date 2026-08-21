package io.github.cnadjim.axon.distributed.autoconfigure.springcloud;

import java.util.LinkedHashMap;
import java.util.Map;


public final class AxonDistributedSpringCloudDefaults {

    private AxonDistributedSpringCloudDefaults() {
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("axon.distributed.enabled", true);
        defaults.put("axon.distributed.spring-cloud.enabled", true);
        defaults.put("axon.distributed.spring-cloud.mode", "rest");
        defaults.put("axon.distributed.spring-cloud.rest-mode-url", "/command-capabilities");
        return defaults;
    }
}

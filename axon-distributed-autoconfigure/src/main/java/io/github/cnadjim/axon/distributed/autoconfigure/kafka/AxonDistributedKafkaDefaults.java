package io.github.cnadjim.axon.distributed.autoconfigure.kafka;

import org.axonframework.extensions.kafka.KafkaProperties;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AxonDistributedKafkaDefaults {

    private AxonDistributedKafkaDefaults() {
    }

    public static Map<String, Object> defaults(String applicationName) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("axon.kafka.enabled", true);
        defaults.put("axon.kafka.default-topic", KafkaProperties.DEFAULT_TOPIC);
        defaults.put("axon.kafka.client-id", applicationName);
        defaults.put("axon.eventhandling.processors.profile.mode", "tracking");
        defaults.put("axon.eventhandling.processors.profile.source", "streamableKafkaMessageSource");
        defaults.put("axon.kafka.producer.event-processor-mode", "tracking");
        defaults.put("axon.kafka.consumer.auto-offset-reset", "earliest");
        return defaults;
    }
}

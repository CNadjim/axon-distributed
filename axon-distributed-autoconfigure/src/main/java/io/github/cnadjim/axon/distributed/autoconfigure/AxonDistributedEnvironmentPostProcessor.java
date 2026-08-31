package io.github.cnadjim.axon.distributed.autoconfigure;

import org.axonframework.extensions.kafka.KafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;


public class AxonDistributedEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "axonDistributedDefaultProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

        String applicationName = environment.getProperty("spring.application.name", "axon-distributed-application");

        Map<String, Object> defaultProperties = new LinkedHashMap<>();

        defaultProperties.put("axon.axonserver.enabled", false);
        defaultProperties.put("axon.serializer.general", "jackson");
        defaultProperties.put("axon.serializer.messages", "jackson");
        defaultProperties.put("axon.serializer.event", "jackson");
        defaultProperties.put("axon.update-check.disabled", true);

        defaultProperties.put("axon.distributed.enabled", true);
        defaultProperties.put("axon.distributed.spring-cloud.enabled", true);
        defaultProperties.put("axon.distributed.spring-cloud.mode", "rest");
        defaultProperties.put("axon.distributed.spring-cloud.rest-mode-url", "/command-capabilities");

        defaultProperties.put("axon.kafka.enabled", true);
        defaultProperties.put("axon.kafka.default-topic", KafkaProperties.DEFAULT_TOPIC);
        defaultProperties.put("axon.kafka.client-id", applicationName);
        defaultProperties.put("axon.eventhandling.processors.profile.mode", "tracking");
        defaultProperties.put("axon.eventhandling.processors.profile.source", "streamableKafkaMessageSource");
        defaultProperties.put("axon.kafka.producer.event-processor-mode", "tracking");
        defaultProperties.put("axon.kafka.consumer.auto-offset-reset", "earliest");

        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.remove(PROPERTY_SOURCE_NAME);
        }
        propertySources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaultProperties));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}

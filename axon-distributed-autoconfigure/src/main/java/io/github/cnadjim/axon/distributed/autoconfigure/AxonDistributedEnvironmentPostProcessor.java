package io.github.cnadjim.axon.distributed.autoconfigure;

import io.github.cnadjim.axon.distributed.autoconfigure.kafka.AxonDistributedKafkaDefaults;
import io.github.cnadjim.axon.distributed.autoconfigure.springcloud.AxonDistributedSpringCloudDefaults;
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
    private static final String ENABLED_PROPERTY = "axon.starter.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE);

        if (!enabled) {
            return;
        }

        String applicationName = environment.getProperty("spring.application.name", "axon-distributed-application");

        Map<String, Object> defaultProperties = new LinkedHashMap<>();

        defaultProperties.put("axon.axonserver.enabled", false);
        defaultProperties.put("axon.serializer.general", "jackson");
        defaultProperties.put("axon.serializer.messages", "jackson");
        defaultProperties.put("axon.serializer.event", "jackson");
        defaultProperties.put("axon.update-check.disabled", true);

        defaultProperties.putAll(AxonDistributedSpringCloudDefaults.defaults());
        defaultProperties.putAll(AxonDistributedKafkaDefaults.defaults(applicationName));

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

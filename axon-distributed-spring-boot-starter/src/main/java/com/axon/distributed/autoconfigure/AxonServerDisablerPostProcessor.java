package com.axon.distributed.autoconfigure;

import com.axon.distributed.AxonDistributedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class AxonServerDisablerPostProcessor implements EnvironmentPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AxonServerDisablerPostProcessor.class);

    private static final String FORCED_PROPERTY_SOURCE_NAME = "axonDistributedForcedProperties";
    private static final String DEFAULT_PROPERTY_SOURCE_NAME = "axonDistributedDefaultProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean distributedEnabled = environment.getProperty("axon.distributed.enabled", Boolean.class, true);

        if (distributedEnabled) {
            // Propriétés forcées — priorité maximale, l'utilisateur ne peut pas les overrider.
            // axon.axonserver.enabled=false est une exigence fondamentale du mode distribué.
            Map<String, Object> forcedProperties = new HashMap<>();
            forcedProperties.put("axon.axonserver.enabled", "false");
            forcedProperties.put("disable-axoniq-console-message", "true");
            environment.getPropertySources().addFirst(new MapPropertySource(FORCED_PROPERTY_SOURCE_NAME, forcedProperties));

            // Propriétés par défaut — priorité minimale, l'utilisateur peut les overrider.
            Map<String, Object> defaultProperties = new HashMap<>();
            defaultProperties.put("axon.serializer.general", "jackson");
            defaultProperties.put("axon.serializer.events", "jackson");
            defaultProperties.put("axon.serializer.messages", "jackson");

            // Mapping Event Bus -> AMQP (pour AMQPAutoConfiguration / SpringAMQPPublisher).
            // La valeur vient de la propriété utilisateur ou du défaut de AxonDistributedProperties.
            String eventBusExchange = environment.getProperty("axon.distributed.event-bus.exchange", AxonDistributedProperties.EventBusProperties.DEFAULT_EXCHANGE);
            defaultProperties.put("axon.amqp.exchange", eventBusExchange);

            environment.getPropertySources().addLast(new MapPropertySource(DEFAULT_PROPERTY_SOURCE_NAME, defaultProperties));
            logger.info("Axon Server disabled by AxonDistributed starter (axon.axonserver.enabled=false)");
        }
    }
}
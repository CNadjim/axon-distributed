package io.github.cnadjim.axon.distributed.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class SchedulerPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "schedulerProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean distributedEnabled = environment.getProperty("axon.distributed.enabled", Boolean.class, true);

        if (distributedEnabled) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("logging.level.com.github.kagkarlsson.scheduler", "INFO");
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }
}

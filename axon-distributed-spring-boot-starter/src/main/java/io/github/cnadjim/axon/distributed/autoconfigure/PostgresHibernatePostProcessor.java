package io.github.cnadjim.axon.distributed.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class PostgresHibernatePostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "postgresHibernateProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean distributedEnabled = environment.getProperty("axon.distributed.enabled", Boolean.class, true);

        if (distributedEnabled) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.jpa.hibernate.ddl-auto", "validate");
            properties.put("spring.jpa.properties.hibernate.format_sql", "true");
            properties.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            properties.put("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", "true");
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }
}

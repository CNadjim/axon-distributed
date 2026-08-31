package io.github.cnadjim.axon.distributed.autoconfigure;

import org.flywaydb.core.api.Location;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;


@AutoConfiguration(before = FlywayAutoConfiguration.class)
public class AxonDistributedFlywayMigrationAutoConfiguration {
    private static final String AXON_MIGRATION_LOCATION = "classpath:db/migration/axon";

    @Bean
    public FlywayConfigurationCustomizer axonFlywayConfigurationCustomizer() {
        return configuration -> {
            Location[] currentLocations = configuration.getLocations();
            Location[] newLocations = Arrays.copyOf(currentLocations, currentLocations.length + 1);
            newLocations[currentLocations.length] = new Location(AXON_MIGRATION_LOCATION);
            configuration.locations(newLocations);
        };
    }
}

package io.github.cnadjim.axon.distributed.autoconfigure;

import org.flywaydb.core.api.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Configuration automatique pour ajouter les migrations Axon à Flyway
 *
 * Cette configuration ajoute automatiquement le chemin classpath:db/migration/axon
 * aux locations Flyway existantes, permettant ainsi d'initialiser les tables
 * Axon (event store, saga store, token store, etc.) sans que les services
 * applicatifs aient à le configurer manuellement.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnProperty(prefix = "axon.distributed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayAxonMigrationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FlywayAxonMigrationAutoConfiguration.class);
    private static final String AXON_MIGRATION_LOCATION = "classpath:db/migration/axon";

    @Bean
    public FlywayConfigurationCustomizer axonFlywayConfigurationCustomizer() {
        return configuration -> {
            // Récupérer les locations existantes
            Location[] currentLocations = configuration.getLocations();

            // Créer un nouveau tableau avec une place supplémentaire
            Location[] newLocations = Arrays.copyOf(currentLocations, currentLocations.length + 1);

            // Ajouter le chemin des migrations Axon
            newLocations[currentLocations.length] = new Location(AXON_MIGRATION_LOCATION);

            configuration.locations(newLocations);

            logger.info("Added Axon migration location: {}", AXON_MIGRATION_LOCATION);
            logger.debug("Total Flyway locations: {}", Arrays.toString(newLocations));
        };
    }
}

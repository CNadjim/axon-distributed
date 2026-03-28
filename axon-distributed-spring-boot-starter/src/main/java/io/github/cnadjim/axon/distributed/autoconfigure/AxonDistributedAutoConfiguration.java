package io.github.cnadjim.axon.distributed.autoconfigure;

import io.github.cnadjim.axon.distributed.AxonDistributedProperties;
import io.github.cnadjim.axon.distributed.config.*;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonDbSchedulerAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.UUID;

@AutoConfiguration(before = {
        AxonAutoConfiguration.class,
        AxonDbSchedulerAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "axon.distributed", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AxonDistributedProperties.class)
@Import({
        RouterConfiguration.class,
        QueryBusConfiguration.class,
        EventBusConfiguration.class,
        RabbitMqConfiguration.class,
        CommandBusConfiguration.class,
        DeadlineManagerConfiguration.class,
        ServiceDiscoveryConfiguration.class,
})
public class AxonDistributedAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AxonDistributedAutoConfiguration.class);

    public AxonDistributedAutoConfiguration() {
        logger.info("Axon Distributed Framework Auto-Configuration enabled");
    }

    @Bean("instanceId")
    public String instanceId() {
        return UUID.randomUUID().toString();
    }

    @Bean("serviceName")
    public String serviceName(@Value("${spring.application.name:axon-service}") String serviceName) {
        return serviceName;
    }

    @Bean("memberName")
    public String memberName(@Qualifier("serviceName") String serviceName,
                             @Qualifier("instanceId") String instanceId) {
        return String.format("%s-%s", serviceName, instanceId);
    }
}

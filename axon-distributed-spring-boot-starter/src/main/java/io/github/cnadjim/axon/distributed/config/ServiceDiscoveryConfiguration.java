package com.axon.distributed.config;

import com.axon.distributed.AxonDistributedProperties;
import com.axon.distributed.discovery.InMemoryMemberRegistry;
import com.axon.distributed.discovery.MemberRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for Service Discovery using RabbitMQ.
 * <p>
 * Enables:
 * - ServiceHeartbeatPublisher: Publishes periodic heartbeats to announce presence
 * - ServiceRegistry: Maintains a registry of active services
 * - InstanceHealthMonitor: Monitors health and cleans up stale instances
 * <p>
 * Can be disabled by setting axon.distributed.service-discovery.enabled=false
 */
@Configuration
@EnableScheduling
public class ServiceDiscoveryConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryConfiguration.class);

    public ServiceDiscoveryConfiguration() {
        logger.info("Service Discovery configuration enabled - RabbitMQ heartbeat mechanism activated");
    }

    @Bean("heartbeatExchange")
    public Exchange heartbeatExchange(AxonDistributedProperties properties) {
        final ExchangeBuilder exchangeBuilder = new ExchangeBuilder(
                properties.getServiceDiscovery().getExchange(),
                ExchangeTypes.FANOUT
        );

        exchangeBuilder.durable(true);

        return exchangeBuilder.build();
    }

    @Bean("heartbeatQueueName")
    public String heartbeatQueueName(@Qualifier("memberName") String memberName,
                                     AxonDistributedProperties properties) {
        return String.format("%s.%s", properties.getServiceDiscovery().getExchange(), memberName);
    }


    @Bean("heartbeatQueue")
    public Queue heartbeatQueue(@Qualifier("heartbeatQueueName") String heartbeatQueueName) {
        return new Queue(heartbeatQueueName, false, true, true);
    }


    @Bean("heartbeatBinding")
    public Binding heartbeatBinding(@Qualifier("heartbeatQueue") Queue heartbeatQueue,
                                    @Qualifier("heartbeatExchange") Exchange heartbeatExchange) {
        return BindingBuilder.bind(heartbeatQueue)
                .to(heartbeatExchange)
                .with("")
                .noargs();
    }

    @Primary
    @Bean("memberRegistry")
    public MemberRegistry memberRegistry(RabbitTemplate rabbitTemplate,
                                         @Qualifier("memberName") String memberName,
                                         AxonDistributedProperties axonDistributedProperties) {
        return new InMemoryMemberRegistry(
                axonDistributedProperties.getServiceDiscovery().getExchange(),
                memberName,
                axonDistributedProperties.getServiceDiscovery().getStaleThreshold(),
                rabbitTemplate
        );
    }

}

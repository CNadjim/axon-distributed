package com.axon.distributed.config;

import com.axon.distributed.command.SpringCommandBusConnector;
import com.axon.distributed.discovery.MemberRegistry;
import com.axon.distributed.AxonDistributedProperties;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Distributed Command Bus with RabbitMQ.
 */
@Configuration
public class CommandBusConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CommandBusConfiguration.class);

    @Bean("commandExchange")
    public DirectExchange commandExchange(AxonDistributedProperties properties) {
        final ExchangeBuilder exchangeBuilder = new ExchangeBuilder(
                properties.getCommandBus().getExchange(),
                ExchangeTypes.DIRECT
        );
        exchangeBuilder.durable(true);
        return exchangeBuilder.build();
    }

    @Bean("commandQueueName")
    public String commandQueueName(@Qualifier("memberName") String memberName,
                                   AxonDistributedProperties properties) {
        return String.format("%s.%s", properties.getCommandBus().getExchange(), memberName);
    }

    @Bean("commandQueue")
    public Queue commandQueue(@Qualifier("commandQueueName") String commandQueueName) {
        return new Queue(commandQueueName, false, true, true);
    }

    @Bean("commandBinding")
    public Binding commandBinding(@Qualifier("commandQueue") Queue commandQueue,
                                  @Qualifier("commandExchange") DirectExchange commandExchange,
                                  @Qualifier("memberName") String memberName) {
        return BindingBuilder
                .bind(commandQueue)
                .to(commandExchange)
                .with(memberName);
    }

    @Bean
    public RoutingStrategy routingStrategy() {
        return AnnotationRoutingStrategy.defaultStrategy();
    }

    @Bean
    public SimpleCommandBus commandBus(@Qualifier("axonTransactionManager") TransactionManager transactionManager,
                                       org.axonframework.config.Configuration axonConfiguration,
                                       DuplicateCommandHandlerResolver duplicateCommandHandlerResolver) {
        SimpleCommandBus commandBus = SimpleCommandBus.builder()
                .transactionManager(transactionManager)
                .duplicateCommandHandlerResolver(duplicateCommandHandlerResolver)
                .spanFactory(axonConfiguration.getComponent(CommandBusSpanFactory.class))
                .messageMonitor(axonConfiguration.messageMonitor(CommandBus.class, "commandBus"))
                .build();

        commandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));

        return commandBus;
    }

    /**
     * Creates RabbitMQ CommandBusConnector for distributed command handling.
     */
    @Bean
    public CommandBusConnector springCommandBusConnector(
            MemberRegistry memberRegistry,
            SimpleCommandBus localSegment,
            Serializer serializer,
            RabbitTemplate rabbitTemplate,
            AxonDistributedProperties properties) {

        long timeout = properties.getCommandBus().getTimeout();

        logger.info("Creating RabbitMQCommandBusConnector with localSegment: {}, timeout: {}ms", localSegment, timeout);

        return SpringCommandBusConnector.builder()
                .memberRegistry(memberRegistry)
                .localCommandBus(localSegment)
                .rabbitTemplate(rabbitTemplate)
                .commandExchange(properties.getCommandBus().getExchange())
                .replyTimeout(properties.getCommandBus().getTimeout())
                .serializer(serializer)
                .build();
    }


    /**
     * Creates the DistributedCommandBus that integrates with Axon's CommandGateway.
     */
    @Bean
    @Primary
    public DistributedCommandBus distributedCommandBus(CommandRouter router,
                                                       CommandBusConnector connector) {

        logger.info("Creating DistributedCommandBus with connector and router");

        DistributedCommandBus distributedCommandBus = DistributedCommandBus.builder()
                .connector(connector)
                .commandRouter(router)
                .build();

        logger.info("DistributedCommandBus successfully configured");
        return distributedCommandBus;
    }
}

package io.github.cnadjim.axon.distributed.config;

import io.github.cnadjim.axon.distributed.AxonDistributedProperties;
import com.rabbitmq.client.Channel;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.extensions.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.extensions.amqp.eventhandling.spring.SpringAMQPMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AMQP infrastructure required by the Axon AMQP extension.
 * <p>
 * This configuration creates the RabbitMQ exchange, queue, and binding that are NOT
 * provided by AMQPAutoConfiguration, as well as the SpringAMQPMessageSource for consuming events.
 * <p>
 * AMQPAutoConfiguration already provides:
 * - SpringAMQPPublisher (publishes events to RabbitMQ)
 * - AMQPMessageConverter (serializes/deserializes events)
 * - RoutingKeyResolver (determines routing keys)
 */
@Configuration
public class EventBusConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EventBusConfiguration.class);

    /**
     * Configures all event processors to use SubscribingEventProcessor with AMQP source.
     * TrackingEventProcessor is NOT compatible with SpringAMQPMessageSource.
     */
    @Bean
    public ConfigurerModule amqpEventProcessorConfigurerModule(@Qualifier("amqpMessageSource") SpringAMQPMessageSource amqpMessageSource,
                                                               @Qualifier("axonTransactionManager") TransactionManager axonTransactionManager) {
        return configurer -> configurer.eventProcessing(processing ->
                processing.registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                    logger.debug("Creating SubscribingEventProcessor '{}' with AMQP source", name);
                    return SubscribingEventProcessor.builder()
                            .name(name)
                            .eventHandlerInvoker(eventHandlerInvoker)
                            .transactionManager(axonTransactionManager)
                            .messageSource(amqpMessageSource)
                            .build();
                })
        );
    }

    /**
     * Creates a FanoutExchange for event distribution.
     * All events are broadcast to all bound queues.
     */
    @Bean("eventExchange")
    public FanoutExchange eventExchange(AxonDistributedProperties props) {
        return ExchangeBuilder.fanoutExchange(props.getEventBus().getExchange())
                .durable(true)
                .build();
    }

    /**
     * Creates a durable queue for this service to consume events.
     * Queue name follows the pattern: {exchange}.{serviceName}
     */
    @Bean("eventQueue")
    public Queue eventQueue(@Qualifier("serviceName") String serviceName,
                            AxonDistributedProperties props) {
        String queueName = props.getEventBus().getExchange() + "." + serviceName;
        return QueueBuilder.durable(queueName).build();
    }

    /**
     * Binds the event queue to the fanout exchange.
     */
    @Bean("eventBinding")
    public Binding eventBinding(@Qualifier("eventQueue") Queue eventQueue,
                                @Qualifier("eventExchange") FanoutExchange eventExchange) {
        return BindingBuilder.bind(eventQueue).to(eventExchange);
    }

    /**
     * Message source with @RabbitListener (official Axon approach).
     * <p>
     * This source consumes messages from the event queue and distributes them
     * to Axon's event processors.
     */
    @Bean("amqpMessageSource")
    public SpringAMQPMessageSource amqpMessageSource(AMQPMessageConverter converter) {
        return new SpringAMQPMessageSource(converter) {

            @Override
            @RabbitListener(queues = "#{eventQueue.name}")
            public void onMessage(Message message, Channel channel) {
                super.onMessage(message, channel);
            }
        };
    }
}

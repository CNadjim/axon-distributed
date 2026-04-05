package io.github.cnadjim.axon.distributed.config;

import io.github.cnadjim.axon.distributed.AxonDistributedProperties;
import io.github.cnadjim.axon.distributed.AxonDistributedProperties.DeadLetterProperties;
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
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * Configuration for AMQP infrastructure required by the Axon AMQP extension.
 * <p>
 * This configuration creates the RabbitMQ exchange, queue, and binding that are NOT
 * provided by AMQPAutoConfiguration, as well as the SpringAMQPMessageSource for consuming events.
 * <p>
 * Resilience features:
 * <ul>
 *   <li>Retry with exponential backoff (configurable via {@code axon.distributed.dead-letter.*})</li>
 *   <li>Dead Letter Queue (DLQ): after retries are exhausted, failed messages are republished
 *       to a dedicated DLQ exchange/queue for inspection and manual reprocessing</li>
 *   <li>Messages are never requeued by RabbitMQ on failure, preventing infinite retry loops</li>
 * </ul>
 * <p>
 * AMQPAutoConfiguration already provides:
 * - SpringAMQPPublisher (publishes events to RabbitMQ)
 * - AMQPMessageConverter (serializes/deserializes events)
 * - RoutingKeyResolver (determines routing keys)
 */
@Configuration
public class EventBusConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EventBusConfiguration.class);
    private static final String DLQ_SUFFIX = ".dlq";

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

    // ── Main event exchange / queue / binding ──────────────────────────────

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
     * Queue name follows the pattern: {exchange}.{serviceName}.
     * <p>
     * Dead-lettering is configured so that messages rejected after retry exhaustion
     * are automatically routed to the DLQ exchange.
     */
    @Bean("eventQueue")
    public Queue eventQueue(@Qualifier("serviceName") String serviceName,
                            AxonDistributedProperties props) {
        String queueName = props.getEventBus().getExchange() + "." + serviceName;
        String dlqExchange = props.getDeadLetter().getExchange();
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlqExchange)
                .deadLetterRoutingKey(queueName + DLQ_SUFFIX)
                .build();
    }

    /**
     * Binds the event queue to the fanout exchange.
     */
    @Bean("eventBinding")
    public Binding eventBinding(@Qualifier("eventQueue") Queue eventQueue,
                                @Qualifier("eventExchange") FanoutExchange eventExchange) {
        return BindingBuilder.bind(eventQueue).to(eventExchange);
    }

    // ── Dead Letter Queue infrastructure ───────────────────────────────────

    /**
     * Direct exchange for dead-lettered event messages.
     */
    @Bean("eventDlqExchange")
    public DirectExchange eventDlqExchange(AxonDistributedProperties props) {
        return ExchangeBuilder.directExchange(props.getDeadLetter().getExchange())
                .durable(true)
                .build();
    }

    /**
     * Durable queue that stores messages which failed processing after all retry attempts.
     */
    @Bean("eventDlqQueue")
    public Queue eventDlqQueue(@Qualifier("serviceName") String serviceName,
                               AxonDistributedProperties props) {
        String queueName = props.getEventBus().getExchange() + "." + serviceName + DLQ_SUFFIX;
        return QueueBuilder.durable(queueName).build();
    }

    /**
     * Binds the DLQ queue to the DLQ exchange using the dead-letter routing key.
     */
    @Bean("eventDlqBinding")
    public Binding eventDlqBinding(@Qualifier("eventDlqQueue") Queue eventDlqQueue,
                                   @Qualifier("eventDlqExchange") DirectExchange eventDlqExchange,
                                   @Qualifier("serviceName") String serviceName,
                                   AxonDistributedProperties props) {
        String routingKey = props.getEventBus().getExchange() + "." + serviceName + DLQ_SUFFIX;
        return BindingBuilder.bind(eventDlqQueue).to(eventDlqExchange).with(routingKey);
    }

    // ── Retry + Recovery ───────────────────────────────────────────────────

    /**
     * After all retry attempts are exhausted, republishes the failed message
     * to the DLQ exchange instead of silently discarding or endlessly requeuing it.
     * <p>
     * The original exception stacktrace and headers are preserved on the republished message.
     */
    @Bean("eventMessageRecoverer")
    public RepublishMessageRecoverer eventMessageRecoverer(AmqpTemplate amqpTemplate,
                                                           @Qualifier("serviceName") String serviceName,
                                                           AxonDistributedProperties props) {
        String dlqExchangeName = props.getDeadLetter().getExchange();
        String routingKey = props.getEventBus().getExchange() + "." + serviceName + DLQ_SUFFIX;
        logger.info("Event DLQ configured: exchange='{}', routingKey='{}'", dlqExchangeName, routingKey);
        return new RepublishMessageRecoverer(amqpTemplate, dlqExchangeName, routingKey);
    }

    /**
     * Retry interceptor with exponential backoff for the event listener container.
     * Configurable via {@code axon.distributed.dead-letter.*} properties.
     * <p>
     * Uses {@link RetryInterceptorBuilder} which natively integrates with
     * Spring AMQP's {@link RepublishMessageRecoverer}.
     */
    @Bean("eventRetryInterceptor")
    public RetryOperationsInterceptor eventRetryInterceptor(@Qualifier("eventMessageRecoverer") RepublishMessageRecoverer recoverer,
                                                            AxonDistributedProperties props) {
        DeadLetterProperties dlProps = props.getDeadLetter();

        logger.info("Event retry configured: maxRetries={}, initialInterval={}ms, multiplier={}, maxInterval={}ms",
                dlProps.getMaxRetries(), dlProps.getInitialInterval(),
                dlProps.getMultiplier(), dlProps.getMaxInterval());

        return RetryInterceptorBuilder.stateless()
                .maxAttempts(dlProps.getMaxRetries())
                .backOffOptions(
                        dlProps.getInitialInterval(),
                        dlProps.getMultiplier(),
                        dlProps.getMaxInterval()
                )
                .recoverer(recoverer)
                .build();
    }

    // ── Listener container factory ─────────────────────────────────────────

    /**
     * Custom listener container factory for event queues.
     * <ul>
     *   <li>defaultRequeueRejected = false: prevents RabbitMQ from endlessly requeuing failed messages</li>
     *   <li>Retry advice with exponential backoff and DLQ recovery</li>
     *   <li>prefetchCount = 1: ensures ordered processing per consumer</li>
     * </ul>
     */
    @Bean("eventListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory eventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("eventRetryInterceptor") RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }

    // ── AMQP Message Source ────────────────────────────────────────────────

    /**
     * Message source with @RabbitListener (official Axon approach).
     * <p>
     * Uses the custom {@code eventListenerContainerFactory} which provides:
     * <ul>
     *   <li>Retry with exponential backoff</li>
     *   <li>Dead Letter Queue recovery after retry exhaustion</li>
     *   <li>No requeue on rejection (prevents infinite loops)</li>
     * </ul>
     */
    @Bean("amqpMessageSource")
    public SpringAMQPMessageSource amqpMessageSource(AMQPMessageConverter converter) {
        return new SpringAMQPMessageSource(converter) {

            @Override
            @RabbitListener(queues = "#{eventQueue.name}", containerFactory = "eventListenerContainerFactory")
            public void onMessage(Message message, Channel channel) {
                logger.trace("Received event message: id={}", message.getMessageProperties().getMessageId());
                super.onMessage(message, channel);
            }
        };
    }
}

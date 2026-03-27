package com.axon.distributed.config;

import com.axon.distributed.AxonDistributedProperties;
import com.axon.distributed.discovery.MemberRegistry;
import com.axon.distributed.query.DistributedQueryBus;
import com.axon.distributed.router.CustomRouter;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.*;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class QueryBusConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueryBusConfiguration.class);

    @Bean("queryExchangeName")
    public String queryExchangeName(AxonDistributedProperties properties) {
        return properties.getQueryBus().getExchange();
    }

    @Bean("queryExchange")
    public DirectExchange queryExchange(@Qualifier("queryExchangeName") String queryExchangeName) {
        final ExchangeBuilder exchangeBuilder = new ExchangeBuilder(
                queryExchangeName,
                ExchangeTypes.DIRECT
        );
        exchangeBuilder.durable(true);
        return exchangeBuilder.build();
    }

    @Bean("queryQueueName")
    public String queryQueueName(@Qualifier("memberName") String memberName,
                                 @Qualifier("queryExchangeName") String queryExchangeName) {
        return String.format("%s.%s", queryExchangeName, memberName);
    }

    @Bean("queryQueue")
    public Queue queryQueue(@Qualifier("queryQueueName") String queryQueueName) {
        return new Queue(queryQueueName, false, true, true);
    }

    @Bean("queryBinding")
    public Binding queryBinding(@Qualifier("queryQueue") Queue commandQueue,
                                @Qualifier("queryExchange") DirectExchange commandExchange,
                                @Qualifier("memberName") String memberName) {
        return BindingBuilder
                .bind(commandQueue)
                .to(commandExchange)
                .with(memberName);
    }

    @Bean
    public SimpleQueryBus queryBus(org.axonframework.config.Configuration axonConfiguration,
                                   TransactionManager transactionManager) {
        SimpleQueryBus simpleQueryBus = SimpleQueryBus.builder()
                .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                .transactionManager(transactionManager)
                .errorHandler(axonConfiguration.getComponent(QueryInvocationErrorHandler.class, () -> LoggingQueryInvocationErrorHandler.builder().build()))
                .queryUpdateEmitter(axonConfiguration.getComponent(QueryUpdateEmitter.class))
                .spanFactory(axonConfiguration.getComponent(QueryBusSpanFactory.class))
                .build();

        simpleQueryBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));

        return simpleQueryBus;
    }

    @Bean
    @Primary
    public DistributedQueryBus distributedQueryBus(CustomRouter router,
                                                   SimpleQueryBus localSegment,
                                                   Serializer serializer,
                                                   RabbitTemplate rabbitTemplate,
                                                   MemberRegistry memberRegistry,
                                                   AxonDistributedProperties properties) {
        return DistributedQueryBus.builder()
                .localQueryBus(localSegment)
                .queryExchange(properties.getQueryBus().getExchange())
                .memberRegistry(memberRegistry)
                .router(router)
                .serializer(serializer)
                .rabbitTemplate(rabbitTemplate)
                .build();
    }
}

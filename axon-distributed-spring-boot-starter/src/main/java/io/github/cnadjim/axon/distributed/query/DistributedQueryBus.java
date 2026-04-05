package io.github.cnadjim.axon.distributed.query;

import io.github.cnadjim.axon.distributed.discovery.MemberRegistry;
import io.github.cnadjim.axon.distributed.messaging.SpringDispatchQueryMessage;
import io.github.cnadjim.axon.distributed.messaging.SpringReplyQueryMessage;
import io.github.cnadjim.axon.distributed.router.CustomRouter;

import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.*;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.axonframework.common.BuilderUtils.assertNonNull;

public class DistributedQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(DistributedQueryBus.class);

    private final QueryBus localQueryBus;
    private final String queryExchange;
    private final MemberRegistry memberRegistry;
    private final CustomRouter router;
    private final Executor executor;
    private final RabbitTemplate rabbitTemplate;
    private final Serializer serializer;
    private final long replyTimeout;

    protected DistributedQueryBus(Builder builder) {
        builder.validate();
        this.localQueryBus = builder.localQueryBus;
        this.queryExchange = builder.queryExchange;
        this.memberRegistry = builder.memberRegistry;
        this.router = builder.router;
        this.executor = builder.executor;
        this.rabbitTemplate = builder.rabbitTemplate;
        this.serializer = builder.serializer;
        this.replyTimeout = builder.replyTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    @RabbitListener(queues = "#{queryQueueName}")
    public <Q, R> SpringReplyQueryMessage<R> onQuery(SpringDispatchQueryMessage<Q, R> dispatchMessage) {
        QueryMessage<Q, R> queryMessage;
        try {
            queryMessage = dispatchMessage.getQueryMessage(serializer);
        } catch (Exception exception) {
            logger.error("Could not deserialize query", exception);

            ResponseType<Object> fallback = ResponseTypes.instanceOf(Object.class);
            QueryResponseMessage<Object> response = GenericQueryResponseMessage.asResponseMessage(
                    fallback.responseMessagePayloadType(),
                    exception
            );
            // queryIdentifier inconnu => "UNKNOWN"
            //noinspection unchecked
            return (SpringReplyQueryMessage<R>) createReply("UNKNOWN", response, fallback);
        }

        try {
            logger.debug("Processing query: {} locally", queryMessage.getQueryName());
            CompletableFuture<QueryResponseMessage<R>> responseFuture = localQueryBus.query(queryMessage);

            QueryResponseMessage<R> response = responseFuture.join();
            return createReply(queryMessage.getIdentifier(), response, queryMessage.getResponseType());

        } catch (Exception exception) {
            logger.error("Error processing query: {}", queryMessage.getQueryName(), exception);

            QueryResponseMessage<R> response = GenericQueryResponseMessage.asResponseMessage(
                    queryMessage.getResponseType().responseMessagePayloadType(),
                    exception
            );
            return createReply(queryMessage.getIdentifier(), response, queryMessage.getResponseType());
        }
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(@Nonnull QueryMessage<Q, R> queryMessage) {
        String queryName = queryMessage.getQueryName();

        Optional<Member> optionalMember = router.findDestination(queryMessage);

        if (optionalMember.isEmpty()) {
            logger.warn("No member found to handle query: {}", queryName);
            NoHandlerForQueryException noHandlerForQueryException = new NoHandlerForQueryException(queryMessage);
            CompletableFuture<QueryResponseMessage<R>> future = new CompletableFuture<>();
            future.completeExceptionally(noHandlerForQueryException);
            return future;
        }

        Member member = optionalMember.get();

        if (member.local()) {
            return localQueryBus.query(queryMessage);
        }

        return CompletableFuture.supplyAsync(
                () -> sendRemoteQuery(member, queryMessage).getQueryResponseMessage(serializer),
                executor
        );
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(@Nonnull QueryMessage<Q, R> query,
                                                                long timeout,
                                                                @Nonnull TimeUnit unit) {
        // Pas implémenté pour l’instant
        return Stream.empty();
    }

    private <Q, R> SpringReplyQueryMessage<R> sendRemoteQuery(Member destination, QueryMessage<Q, R> queryMessage) {
        String queryIdentifier = queryMessage.getIdentifier();
        String routingKey = destination.name();

        SpringDispatchQueryMessage<Q, R> dispatchMessage = new SpringDispatchQueryMessage<>(queryMessage, serializer);

        logger.debug("Sending query {} to member {} via routing key: {}",
                queryMessage.getQueryName(), destination.name(), routingKey);

        SpringReplyQueryMessage<R> replyMessage = rabbitTemplate.convertSendAndReceiveAsType(
                queryExchange,
                routingKey,
                dispatchMessage,
                new ParameterizedTypeReference<>() {}
        );

        if (replyMessage == null) {
            QueryExecutionException timeoutException = new QueryExecutionException(
                    "Query RPC timed out after " + replyTimeout,
                    new TimeoutException("replyTimeout"),
                    null
            );

            QueryResponseMessage<R> queryResponseMessage = GenericQueryResponseMessage.asResponseMessage(
                    queryMessage.getResponseType().responseMessagePayloadType(),
                    timeoutException
            );

            return new SpringReplyQueryMessage<>(queryIdentifier, queryResponseMessage, serializer, queryMessage.getResponseType());
        }

        return replyMessage;
    }

    private <R> SpringReplyQueryMessage<R> createReply(String queryIdentifier,
                                                       QueryResponseMessage<R> queryResponseMessage,
                                                       ResponseType<R> responseType) {
        try {
            return new SpringReplyQueryMessage<>(queryIdentifier, queryResponseMessage, serializer, responseType);
        } catch (Exception exception) {
            logger.warn("Could not serialize query reply [{}]. Sending back error.", queryResponseMessage, exception);

            ResponseType<Object> fallback = ResponseTypes.instanceOf(Object.class);
            QueryResponseMessage<Object> errorResponse = GenericQueryResponseMessage.asResponseMessage(
                    fallback.responseMessagePayloadType(),
                    exception
            );
            //noinspection unchecked
            return (SpringReplyQueryMessage<R>) new SpringReplyQueryMessage<>("UNKNOWN", errorResponse, serializer, fallback);
        }
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return localQueryBus.queryUpdateEmitter();
    }

    @Override
    public <R> Registration subscribe(@Nonnull String queryName,
                                      @Nonnull Type responseType,
                                      @Nonnull MessageHandler<? super QueryMessage<?, R>> handler) {
        memberRegistry.addQueryCapability(queryName);
        return localQueryBus.subscribe(queryName, responseType, handler);
    }

    @Override
    public Registration registerDispatchInterceptor(@Nonnull MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return localQueryBus.registerDispatchInterceptor(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(@Nonnull MessageHandlerInterceptor<? super QueryMessage<?, ?>> handlerInterceptor) {
        return localQueryBus.registerHandlerInterceptor(handlerInterceptor);
    }

    public static class Builder {

        private QueryBus localQueryBus;
        private RabbitTemplate rabbitTemplate;
        private Serializer serializer;
        private MemberRegistry memberRegistry;
        private CustomRouter router;
        private String queryExchange;
        private long replyTimeout = 5000;
        private Executor executor = DirectExecutor.INSTANCE;
        @SuppressWarnings("unused")
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;

        public Builder localQueryBus(QueryBus localQueryBus) {
            assertNonNull(localQueryBus, "Local QueryBus may not be null");
            this.localQueryBus = localQueryBus;
            return this;
        }

        public Builder rabbitTemplate(RabbitTemplate rabbitTemplate) {
            assertNonNull(rabbitTemplate, "RabbitTemplate may not be null");
            this.rabbitTemplate = rabbitTemplate;
            return this;
        }

        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        public Builder memberRegistry(MemberRegistry memberRegistry) {
            assertNonNull(memberRegistry, "MemberRegistry may not be null");
            this.memberRegistry = memberRegistry;
            return this;
        }

        public Builder router(CustomRouter customRouter) {
            assertNonNull(customRouter, "CustomRouter may not be null");
            this.router = customRouter;
            return this;
        }

        public Builder queryExchange(String queryExchange) {
            assertNonNull(queryExchange, "QueryExchange may not be null");
            this.queryExchange = queryExchange;
            return this;
        }

        public Builder replyTimeout(long replyTimeout) {
            this.replyTimeout = replyTimeout;
            return this;
        }

        public Builder executor(Executor executor) {
            assertNonNull(executor, "Executor may not be null");
            this.executor = executor;
            return this;
        }

        public Builder spanFactory(SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        public DistributedQueryBus build() {
            return new DistributedQueryBus(this);
        }

        protected void validate() {
            assertNonNull(localQueryBus, "The local QueryBus is a hard requirement");
            assertNonNull(rabbitTemplate, "The RabbitTemplate is a hard requirement");
            assertNonNull(serializer, "The Serializer is a hard requirement");
            assertNonNull(memberRegistry, "The MemberRegistry is a hard requirement");
            assertNonNull(router, "The CustomRouter is a hard requirement");
            assertNonNull(queryExchange, "The QueryExchange is a hard requirement");
        }
    }
}

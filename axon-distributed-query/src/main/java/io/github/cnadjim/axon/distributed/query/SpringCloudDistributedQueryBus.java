package io.github.cnadjim.axon.distributed.query;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.lang.NonNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class SpringCloudDistributedQueryBus implements QueryBus {

    private static final Logger logger = LoggerFactory.getLogger(SpringCloudDistributedQueryBus.class);

    private final QueryBus localQueryBus;
    private final QueryCapabilityRegistry capabilityRegistry;
    private final SpringCloudQueryRouter router;
    private final HttpQueryBusConnector connector;
    private final Executor executor;

    public SpringCloudDistributedQueryBus(QueryBus localQueryBus, QueryCapabilityRegistry capabilityRegistry,
                                          SpringCloudQueryRouter router, HttpQueryBusConnector connector,
                                          Executor executor) {
        this.localQueryBus = localQueryBus;
        this.capabilityRegistry = capabilityRegistry;
        this.router = router;
        this.connector = connector;
        this.executor = executor;
    }

    @Override
    public <R> Registration subscribe(@NonNull String queryName,
                                      @NonNull Type responseType,
                                      @NonNull MessageHandler<? super QueryMessage<?, R>> handler) {
        capabilityRegistry.register(queryName);
        Registration localRegistration = localQueryBus.subscribe(queryName, responseType, handler);
        return () -> {
            capabilityRegistry.unregister(queryName);
            return localRegistration.cancel();
        };
    }

    @Override
    public <Q, R> CompletableFuture<QueryResponseMessage<R>> query(@NonNull QueryMessage<Q, R> queryMessage) {
        String queryName = queryMessage.getQueryName();

        if (capabilityRegistry.isSupportedLocally(queryName)) {
            return localQueryBus.query(queryMessage);
        }

        Optional<ServiceInstance> destination = router.findDestination(queryName);
        if (destination.isEmpty()) {
            logger.warn("No local or remote handler found for query: {}", queryName);
            CompletableFuture<QueryResponseMessage<R>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new NoHandlerForQueryException(queryMessage));
            return failed;
        }

        return CompletableFuture.supplyAsync(() -> connector.send(destination.get(), queryMessage), executor);
    }

    @Override
    public <Q, R> Stream<QueryResponseMessage<R>> scatterGather(QueryMessage<Q, R> query, long timeout, TimeUnit unit) {
        String queryName = query.getQueryName();
        Stream<QueryResponseMessage<R>> localStream = capabilityRegistry.isSupportedLocally(queryName)
                ? localQueryBus.scatterGather(query, timeout, unit)
                : Stream.empty();

        List<ServiceInstance> remoteInstances = router.findAllDestinations(queryName);
        List<CompletableFuture<QueryResponseMessage<R>>> remoteFutures = remoteInstances.stream()
                .map(instance -> CompletableFuture.supplyAsync(() -> connector.send(instance, query), executor))
                .toList();

        Stream<QueryResponseMessage<R>> remoteStream = remoteFutures.stream().map(future -> {
            try {
                return future.get(timeout, unit);
            } catch (Exception e) {
                logger.warn("scatterGather: a remote instance did not answer in time for query {}", queryName);
                return null;
            }
        }).filter(java.util.Objects::nonNull);

        return Stream.concat(localStream, remoteStream);
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return localQueryBus.queryUpdateEmitter();
    }

    @Override
    public Registration registerDispatchInterceptor(@NonNull MessageDispatchInterceptor<? super QueryMessage<?, ?>> dispatchInterceptor) {
        return localQueryBus.registerDispatchInterceptor(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(@NonNull MessageHandlerInterceptor<? super QueryMessage<?, ?>> handlerInterceptor) {
        return localQueryBus.registerHandlerInterceptor(handlerInterceptor);
    }

}


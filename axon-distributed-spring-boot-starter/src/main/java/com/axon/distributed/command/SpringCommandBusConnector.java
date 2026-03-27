package com.axon.distributed.command;

import com.axon.distributed.discovery.MemberRegistry;
import com.axon.distributed.messaging.SpringDispatchCommandMessage;
import com.axon.distributed.messaging.SpringReplyCommandMessage;
import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback; // Import nécessaire
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.axonframework.common.BuilderUtils.assertNonNull;

public class SpringCommandBusConnector implements CommandBusConnector {
    private static final Logger logger = LoggerFactory.getLogger(SpringCommandBusConnector.class);

    private final CommandBus localCommandBus;
    private final RabbitTemplate rabbitTemplate;
    private final Serializer serializer;
    private final String commandExchange;
    private final Executor executor;
    private final SpanFactory spanFactory;
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();
    private final MemberRegistry memberRegistry;
    private final long replyTimeout;

    protected SpringCommandBusConnector(Builder builder) {
        builder.validate();
        this.localCommandBus = builder.localCommandBus;
        this.rabbitTemplate = builder.rabbitTemplate;
        this.serializer = builder.serializer;
        this.executor = builder.executor;
        this.spanFactory = builder.spanFactory;
        this.replyTimeout = builder.replyTimeout;
        this.commandExchange = builder.commandExchange;
        this.memberRegistry = builder.memberRegistry;
    }

    public static Builder builder() {
        return new Builder();
    }

    @StartHandler(phase = Phase.EXTERNAL_CONNECTIONS)
    public void start() {
        shutdownLatch.initialize();
    }

    /**
     * Handler RabbitMQ synchrone utilisant FutureCallback.
     */
    @RabbitListener(queues = "#{commandQueueName}")
    public <C, R> SpringReplyCommandMessage<R> handle(SpringDispatchCommandMessage<C> dispatchMessage) {
        CommandMessage<C> commandMessage;

        try {
            commandMessage = dispatchMessage.getCommandMessage(serializer);
        } catch (Exception e) {
            logger.error("Could not deserialize command", e);
            return createReply("UNKNOWN", asCommandResultMessage(e));
        }

        Span span = spanFactory.createChildHandlerSpan(() -> "SpringCommandBusConnector.handle", commandMessage);

        return span.runSupplier(() -> {
            try {
                logger.debug("Processing command: {} locally", commandMessage.getCommandName());

                // Utilisation du FutureCallback standard d'Axon
                FutureCallback<C, R> futureCallback = new FutureCallback<>();
                localCommandBus.dispatch(commandMessage, futureCallback);

                // Attente du résultat (bloquant pour le thread Rabbit)
                CommandResultMessage<? extends R> result = futureCallback.get();

                //noinspection unchecked
                return createReply(commandMessage.getIdentifier(), (CommandResultMessage<R>) result);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Command dispatch interrupted", e);
                return createReply(commandMessage.getIdentifier(), asCommandResultMessage(e));
            } catch (ExecutionException e) {
                logger.error("Error executing command", e);
                return createReply(commandMessage.getIdentifier(), asCommandResultMessage(e.getCause()));
            } catch (Exception exception) {
                logger.error("Unexpected error processing command: {}", commandMessage.getCommandName(), exception);
                span.recordException(exception);
                return createReply(commandMessage.getIdentifier(), asCommandResultMessage(exception));
            }
        });
    }

    @Override
    public <C> void send(Member destination, @Nonnull CommandMessage<? extends C> commandMessage) {
        shutdownLatch.ifShuttingDown("SpringCommandBusConnector is shutting down, no new commands will be sent.");

        if (destination.local()) {
            localCommandBus.dispatch(commandMessage);
        } else {
            executor.execute(() -> sendRemotely(destination, commandMessage));
        }
    }

    @Override
    public <C, R> void send(Member destination, @Nonnull CommandMessage<C> commandMessage,
                            @Nonnull CommandCallback<? super C, R> callback) {
        shutdownLatch.ifShuttingDown("SpringCommandBusConnector is shutting down, no new commands will be sent.");
        //noinspection resource
        ShutdownLatch.ActivityHandle activityHandle = shutdownLatch.registerActivity();

        if (destination.local()) {
            CommandCallback<C, R> wrapper = (cm, crm) -> {
                try {
                    callback.onResult(cm, crm);
                } finally {
                    activityHandle.end();
                }
            };

            localCommandBus.dispatch(commandMessage, wrapper);

        } else {
            executor.execute(() -> {
                try {
                    SpringReplyCommandMessage<R> replyMessage = sendRemotely(destination, commandMessage);
                    callback.onResult(commandMessage, replyMessage.getCommandResultMessage(serializer));
                } catch (Exception exception) {
                    callback.onResult(commandMessage, asCommandResultMessage(new CommandDispatchException("An exception occurred while dispatching a command or its result", exception)));
                } finally {
                    activityHandle.end();
                }
            });
        }
    }

    @Override
    public CompletableFuture<Void> initiateShutdown() {
        return shutdownLatch.initiateShutdown();
    }

    private <C, R> SpringReplyCommandMessage<R> sendRemotely(Member destination, CommandMessage<? extends C> commandMessage) {
        String commandIdentifier = commandMessage.getIdentifier();
        String routingKey = destination.name();
        SpringDispatchCommandMessage<C> dispatchMessage = new SpringDispatchCommandMessage<>(commandMessage, serializer);

        logger.debug("Sending command {} to member {} via routing key: {}",
                commandMessage.getCommandName(), destination.name(), routingKey);

        SpringReplyCommandMessage<R> replyMessage = rabbitTemplate.convertSendAndReceiveAsType(
                commandExchange,
                routingKey,
                dispatchMessage,
                new ParameterizedTypeReference<>() {
                }
        );

        if (replyMessage == null) {
            final CommandExecutionException timeoutException = new CommandExecutionException(
                    "Command RPC timed out after " + replyTimeout,
                    new TimeoutException("replyTimeout")
            );
            final CommandResultMessage<R> commandResultMessage = GenericCommandResultMessage.asCommandResultMessage(timeoutException);
            return new SpringReplyCommandMessage<>(commandIdentifier, commandResultMessage, serializer);
        } else {
            return replyMessage;
        }
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>> handler) {
        memberRegistry.addCommandCapability(commandName);
        return localCommandBus.subscribe(commandName, handler);
    }

    @Override
    public Optional<CommandBus> localSegment() {
        return Optional.of(localCommandBus);
    }

    private <R> SpringReplyCommandMessage<R> createReply(String commandIdentifier, CommandResultMessage<R> commandResultMessage) {
        try {
            return new SpringReplyCommandMessage<>(commandIdentifier, commandResultMessage, serializer);
        } catch (Exception exception) {
            logger.warn("Could not serialize command reply [{}]. Sending back error.", commandResultMessage, exception);
            return new SpringReplyCommandMessage<>(commandIdentifier, GenericCommandResultMessage.asCommandResultMessage(exception), serializer);
        }
    }

    @Override
    public Registration registerHandlerInterceptor(@Nonnull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return localCommandBus.registerHandlerInterceptor(handlerInterceptor);
    }

    public static class Builder {

        private CommandBus localCommandBus;
        private RabbitTemplate rabbitTemplate;
        private Serializer serializer;
        private MemberRegistry memberRegistry;
        private String commandExchange;
        private long replyTimeout;
        private Executor executor = DirectExecutor.INSTANCE;
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;

        public Builder localCommandBus(CommandBus localCommandBus) {
            assertNonNull(localCommandBus, "Local CommandBus may not be null");
            this.localCommandBus = localCommandBus;
            return this;
        }

        public Builder memberRegistry(MemberRegistry memberRegistry) {
            assertNonNull(memberRegistry, "Member registry may not be null");
            this.memberRegistry = memberRegistry;
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

        public Builder replyTimeout(long replyTimeout) {
            assertNonNull(replyTimeout, "replyTimeout may not be null");
            this.replyTimeout = replyTimeout;
            return this;
        }

        public Builder commandExchange(String commandExchange) {
            assertNonNull(commandExchange, "commandExchange may not be null");
            this.commandExchange = commandExchange;
            return this;
        }

        public SpringCommandBusConnector build() {
            return new SpringCommandBusConnector(this);
        }

        protected void validate() {
            assertNonNull(localCommandBus, "The local CommandBus is a hard requirement and should be provided");
            assertNonNull(rabbitTemplate, "The RabbitTemplate is a hard requirement and should be provided");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
            assertNonNull(replyTimeout, "The replyTimeout is a hard requirement and should be provided");
            assertNonNull(commandExchange, "The commandExchange is a hard requirement and should be provided");
            assertNonNull(memberRegistry, "The memberRegistry is a hard requirement and should be provided");
        }
    }
}

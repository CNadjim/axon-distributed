package io.github.cnadjim.axon.distributed.query.api;

import io.github.cnadjim.axon.distributed.query.message.DispatchQueryMessage;
import io.github.cnadjim.axon.distributed.query.message.ReplyQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Receives a {@link DispatchQueryMessage} from a remote caller, executes it against the local
 * {@link QueryBus} only (never the distributed one - this endpoint IS the local segment) and
 * returns a {@link ReplyQueryMessage} (PLAN.md §11.4).
 */
@RestController
public class QueryTransportController {

    private static final Logger logger = LoggerFactory.getLogger(QueryTransportController.class);

    private final QueryBus localQueryBus;
    private final Serializer serializer;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    public static final String QUERY_BUS_CONNECTOR_ENDPOINT = "/spring-query-bus-connector/query";

    public QueryTransportController(QueryBus localQueryBus, Serializer serializer) {
        this.localQueryBus = localQueryBus;
        this.serializer = serializer;
    }

    @PostMapping(QUERY_BUS_CONNECTOR_ENDPOINT)
    public ReplyQueryMessage handle(@RequestBody DispatchQueryMessage dispatchMessage) {
        return process(dispatchMessage);
    }

    private <Q, R> ReplyQueryMessage process(DispatchQueryMessage dispatchMessage) {
        QueryMessage<Q, R> queryMessage;
        try {
            queryMessage = dispatchMessage.getQueryMessage(serializer);
        } catch (Exception exception) {
            logger.error("Could not deserialize incoming query {}", dispatchMessage.getQueryName(), exception);
            QueryResponseMessage<Object> response = GenericQueryResponseMessage.asResponseMessage(Object.class, exception);
            return new ReplyQueryMessage(dispatchMessage.getQueryIdentifier(), response, serializer, null);
        }

        try {
            QueryResponseMessage<R> response = localQueryBus.query(queryMessage).get(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return new ReplyQueryMessage(queryMessage.getIdentifier(), response, serializer, queryMessage.getResponseType());
        } catch (TimeoutException | java.util.concurrent.ExecutionException | InterruptedException exception) {
            logger.warn("Error processing query {} locally", queryMessage.getQueryName(), exception);
            Throwable cause = (exception instanceof java.util.concurrent.ExecutionException) ? exception.getCause() : exception;
            QueryResponseMessage<R> response = GenericQueryResponseMessage.asResponseMessage(
                    queryMessage.getResponseType().responseMessagePayloadType(),
                    cause instanceof Exception ? (Exception) cause : new TimeoutException(cause.getMessage())
            );
            return new ReplyQueryMessage(queryMessage.getIdentifier(), response, serializer, queryMessage.getResponseType());
        }
    }
}


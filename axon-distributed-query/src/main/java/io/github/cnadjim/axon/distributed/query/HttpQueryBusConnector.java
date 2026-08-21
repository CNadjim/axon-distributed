package io.github.cnadjim.axon.distributed.query;

import io.github.cnadjim.axon.distributed.query.message.DispatchQueryMessage;
import io.github.cnadjim.axon.distributed.query.message.QueryCapabilities;
import io.github.cnadjim.axon.distributed.query.message.ReplyQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import static io.github.cnadjim.axon.distributed.query.api.QueryCapabilitiesController.CAPABILITIES_ENDPOINT;


public class HttpQueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(HttpQueryBusConnector.class);

    private final RestTemplate restTemplate;
    private final Serializer serializer;

    public HttpQueryBusConnector(RestTemplate restTemplate,
                                 Serializer serializer) {
        this.restTemplate = restTemplate;
        this.serializer = serializer;
    }

    @SuppressWarnings("unchecked")
    public <Q, R> QueryResponseMessage<R> send(ServiceInstance instance, QueryMessage<Q, R> queryMessage) {
        DispatchQueryMessage dispatchMessage = new DispatchQueryMessage(queryMessage, serializer);
        URI uri = buildUri(instance);

        try {
            ReplyQueryMessage reply = restTemplate.postForObject(uri, dispatchMessage, ReplyQueryMessage.class);
            if (reply == null) {
                return exceptionalResponse(
                        queryMessage,
                        new QueryExecutionException("Remote query handler returned an empty reply", null, null)
                );
            }
            return (QueryResponseMessage<R>) reply.getQueryResponseMessage(serializer);
        } catch (ResourceAccessException e) {
            logger.warn("Timeout or connection error dispatching query {} to {}: {}",
                    queryMessage.getQueryName(), uri, e.getMessage());
            return exceptionalResponse(queryMessage, new QueryExecutionException("Query dispatch to " + instance.getServiceId() + " failed", new TimeoutException(e.getMessage()), null));
        } catch (RestClientException e) {
            logger.warn("Remote error dispatching query {} to {}: {}", queryMessage.getQueryName(), uri, e.getMessage());
            return exceptionalResponse(queryMessage, new QueryExecutionException("Remote query handler at " + instance.getServiceId() + " reported an error", e, null));
        }
    }

    public QueryCapabilities fetchCapabilities(ServiceInstance instance) {
        URI uri = buildUri(instance);
        return restTemplate.getForObject(uri, QueryCapabilities.class);
    }

    private <R> QueryResponseMessage<R> exceptionalResponse(QueryMessage<?, R> queryMessage, Exception exception) {
        return GenericQueryResponseMessage.asResponseMessage(
                queryMessage.getResponseType().responseMessagePayloadType(), exception);
    }

    private URI buildUri(ServiceInstance instance) {
        try {
            return new URI(instance.getUri().toString() + CAPABILITIES_ENDPOINT);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not build URI for service instance " + instance, e);
        }
    }
}


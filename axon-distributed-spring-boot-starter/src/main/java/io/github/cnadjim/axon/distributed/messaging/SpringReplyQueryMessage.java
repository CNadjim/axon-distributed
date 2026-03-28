package io.github.cnadjim.axon.distributed.messaging;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;

/**
 * Spring Message representing a reply to a dispatched query.
 */
public class SpringReplyQueryMessage<R> extends ReplyQueryMessage implements Serializable {

    public SpringReplyQueryMessage(String queryIdentifier,
                                   QueryResponseMessage<R> queryResponseMessage,
                                   Serializer serializer,
                                   ResponseType<R> responseType) {
        super(queryIdentifier, queryResponseMessage, serializer, responseType);
    }

    @SuppressWarnings("unused")
    private SpringReplyQueryMessage() {
        // Used for de-/serialization
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueryResponseMessage<R> getQueryResponseMessage(Serializer serializer) {
        return (QueryResponseMessage<R>) super.getQueryResponseMessage(serializer);
    }
}

package com.axon.distributed.messaging;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring message that contains a QueryMessage that needs to be dispatched on a remote query bus segment.
 */
public class SpringDispatchQueryMessage<Q, R> extends DispatchQueryMessage implements Serializable {

    public SpringDispatchQueryMessage(QueryMessage<?, ?> queryMessage, Serializer serializer) {
        super(queryMessage, serializer);
    }

    @SuppressWarnings("unused")
    private SpringDispatchQueryMessage() {
        // Used for de-/serialization
    }

    @Override
    public QueryMessage<Q, R> getQueryMessage(Serializer serializer) {
        // Désérialisation du payload
        SimpleSerializedObject<byte[]> serializedPayload = new SimpleSerializedObject<>(
                this.serializedPayload, byte[].class, payloadType, payloadRevision
        );

        Q payload = serializer.deserialize(serializedPayload);

        // Désérialisation des méta-données
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(
                this.serializedMetaData, byte[].class
        );
        final MetaData metaData = serializer.deserialize(serializedMetaData);

        // Désérialisation du ResponseType (spécifique aux Query)
        SimpleSerializedObject<byte[]> serializedResponseType = new SimpleSerializedObject<>(
                this.serializedResponseType, byte[].class, responseTypeType, responseTypeRevision
        );
        ResponseType<R> responseType = serializer.deserialize(serializedResponseType);

        // Reconstruction du message générique
        GenericMessage<Q> genericMessage = new GenericMessage<>(queryIdentifier, payload, metaData);
        return new GenericQueryMessage<>(genericMessage, queryName, responseType);
    }
}

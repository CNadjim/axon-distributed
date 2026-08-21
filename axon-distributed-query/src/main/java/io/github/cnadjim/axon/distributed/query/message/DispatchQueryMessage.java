package io.github.cnadjim.axon.distributed.query.message;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Wire representation of a {@link QueryMessage} sent over the internal HTTP query transport
 * (PLAN.md §11.2). Adapted from {@code quizup-axon-starter}'s RabbitMQ-based
 * {@code SpringDispatchQueryMessage} - the payload/metadata/response-type are serialized via
 * the Axon {@link Serializer} (Jackson), while this envelope itself is (de)serialized as plain
 * JSON by Spring's HTTP message converters.
 */
public class DispatchQueryMessage implements Serializable {

    private String queryIdentifier;
    private byte[] serializedMetaData;
    private String payloadType;
    private String payloadRevision;
    private byte[] serializedPayload;
    private String queryName;

    // Response type - crucial for the remote side to select the right handler and convert the result.
    private String responseTypeType;
    private String responseTypeRevision;
    private byte[] serializedResponseType;

    @SuppressWarnings("unused")
    private DispatchQueryMessage() {
        // Used for JSON deserialization
    }

    public DispatchQueryMessage(QueryMessage<?, ?> queryMessage, Serializer serializer) {
        this.queryIdentifier = queryMessage.getIdentifier();
        this.queryName = queryMessage.getQueryName();

        SerializedObject<byte[]> metaData = queryMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();

        SerializedObject<byte[]> payload = queryMessage.serializePayload(serializer, byte[].class);
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.serializedPayload = payload.getData();

        ResponseType<?> forSerialization = queryMessage.getResponseType().forSerialization();
        SerializedObject<byte[]> responseType = serializer.serialize(forSerialization, byte[].class);
        this.responseTypeType = responseType.getType().getName();
        this.responseTypeRevision = responseType.getType().getRevision();
        this.serializedResponseType = responseType.getData();
    }

    public <Q, R> QueryMessage<Q, R> getQueryMessage(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedPayloadObject = new SimpleSerializedObject<>(
                this.serializedPayload, byte[].class, this.payloadType, this.payloadRevision
        );
        Q payload = serializer.deserialize(serializedPayloadObject);

        SerializedMetaData<byte[]> serializedMetaDataObject = new SerializedMetaData<>(
                this.serializedMetaData, byte[].class
        );
        MetaData metaData = serializer.deserialize(serializedMetaDataObject);

        SimpleSerializedObject<byte[]> serializedResponseTypeObject = new SimpleSerializedObject<>(
                this.serializedResponseType, byte[].class, this.responseTypeType, this.responseTypeRevision
        );
        ResponseType<R> responseType = serializer.deserialize(serializedResponseTypeObject);

        GenericMessage<Q> genericMessage = new GenericMessage<>(this.queryIdentifier, payload, metaData);
        return new GenericQueryMessage<>(genericMessage, this.queryName, responseType);
    }

    public String getQueryIdentifier() {
        return queryIdentifier;
    }

    public byte[] getSerializedMetaData() {
        return serializedMetaData;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public String getPayloadRevision() {
        return payloadRevision;
    }

    public byte[] getSerializedPayload() {
        return serializedPayload;
    }

    public String getQueryName() {
        return queryName;
    }

    public String getResponseTypeType() {
        return responseTypeType;
    }

    public String getResponseTypeRevision() {
        return responseTypeRevision;
    }

    public byte[] getSerializedResponseType() {
        return serializedResponseType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DispatchQueryMessage other)) {
            return false;
        }
        return Objects.equals(queryIdentifier, other.queryIdentifier)
                && Arrays.equals(serializedMetaData, other.serializedMetaData)
                && Objects.equals(payloadType, other.payloadType)
                && Objects.equals(payloadRevision, other.payloadRevision)
                && Arrays.equals(serializedPayload, other.serializedPayload)
                && Objects.equals(queryName, other.queryName)
                && Objects.equals(responseTypeType, other.responseTypeType)
                && Objects.equals(responseTypeRevision, other.responseTypeRevision)
                && Arrays.equals(serializedResponseType, other.serializedResponseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryIdentifier, Arrays.hashCode(serializedMetaData), payloadType, payloadRevision,
                Arrays.hashCode(serializedPayload), queryName, responseTypeType, responseTypeRevision,
                Arrays.hashCode(serializedResponseType));
    }

    @Override
    public String toString() {
        return "DispatchQueryMessage{queryIdentifier='" + queryIdentifier + "', queryName='" + queryName + "'}";
    }
}


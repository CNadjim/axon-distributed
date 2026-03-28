package com.axon.distributed.messaging;

import java.util.Arrays;
import java.util.Objects;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

public abstract class DispatchQueryMessage {
    protected String queryIdentifier;
    protected byte[] serializedMetaData;
    protected String payloadType;
    protected String payloadRevision;
    protected byte[] serializedPayload;
    protected String queryName;

    // Champs spécifiques à la Query : Sérialisation du ResponseType
    protected String responseTypeType;
    protected String responseTypeRevision;
    protected byte[] serializedResponseType;

    protected DispatchQueryMessage() {
    }

    protected DispatchQueryMessage(QueryMessage<?, ?> queryMessage, Serializer serializer) {
        this.queryIdentifier = queryMessage.getIdentifier();
        this.queryName = queryMessage.getQueryName();

        // Sérialisation des MetaData
        SerializedObject<byte[]> metaData = queryMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();

        // Sérialisation du Payload
        SerializedObject<byte[]> payload = queryMessage.serializePayload(serializer, byte[].class);
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.serializedPayload = payload.getData();

        // Sérialisation du ResponseType (Crucial pour le QueryBus)
        ResponseType<?> forSerialization = queryMessage.getResponseType().forSerialization();
        SerializedObject<byte[]> responseType = serializer.serialize(forSerialization, byte[].class);
        this.responseTypeType = responseType.getType().getName();
        this.responseTypeRevision = responseType.getType().getRevision();
        this.serializedResponseType = responseType.getData();
    }

    public QueryMessage<?, ?> getQueryMessage(Serializer serializer) {
        // Désérialisation du Payload
        SimpleSerializedObject<byte[]> serializedPayload = new SimpleSerializedObject<>(
                this.serializedPayload, byte[].class, this.payloadType, this.payloadRevision
        );
        Object payload = serializer.deserialize(serializedPayload);

        // Désérialisation des MetaData
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(
                this.serializedMetaData, byte[].class
        );
        MetaData metaData = serializer.deserialize(serializedMetaData);

        // Désérialisation du ResponseType
        SimpleSerializedObject<byte[]> serializedResponseType = new SimpleSerializedObject<>(
                this.serializedResponseType, byte[].class, this.responseTypeType, this.responseTypeRevision
        );
        ResponseType<?> responseType = serializer.deserialize(serializedResponseType);

        return new GenericQueryMessage<>(
                new GenericMessage<>(this.queryIdentifier, payload, metaData),
                this.queryName,
                responseType
        );
    }

    public String getQueryIdentifier() {
        return this.queryIdentifier;
    }

    public byte[] getSerializedMetaData() {
        return this.serializedMetaData;
    }

    public String getPayloadType() {
        return this.payloadType;
    }

    public String getPayloadRevision() {
        return this.payloadRevision;
    }

    public byte[] getSerializedPayload() {
        return this.serializedPayload;
    }

    public String getQueryName() {
        return this.queryName;
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
    public int hashCode() {
        return Objects.hash(
                queryIdentifier,
                Arrays.hashCode(serializedMetaData),
                payloadType,
                payloadRevision,
                Arrays.hashCode(serializedPayload),
                queryName,
                responseTypeType,
                responseTypeRevision,
                Arrays.hashCode(serializedResponseType)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            DispatchQueryMessage other = (DispatchQueryMessage) obj;
            return Objects.equals(this.queryIdentifier, other.queryIdentifier) &&
                    Arrays.equals(this.serializedMetaData, other.serializedMetaData) &&
                    Objects.equals(this.payloadType, other.payloadType) &&
                    Objects.equals(this.payloadRevision, other.payloadRevision) &&
                    Arrays.equals(this.serializedPayload, other.serializedPayload) &&
                    Objects.equals(this.queryName, other.queryName) &&
                    Objects.equals(this.responseTypeType, other.responseTypeType) &&
                    Objects.equals(this.responseTypeRevision, other.responseTypeRevision) &&
                    Arrays.equals(this.serializedResponseType, other.serializedResponseType);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "DispatchQueryMessage{" +
                "queryIdentifier='" + queryIdentifier + '\'' +
                ", serializedMetaData=" + Arrays.toString(serializedMetaData) +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", serializedPayload=" + Arrays.toString(serializedPayload) +
                ", queryName='" + queryName + '\'' +
                ", responseTypeType='" + responseTypeType + '\'' +
                ", responseTypeRevision='" + responseTypeRevision + '\'' +
                ", serializedResponseType=" + Arrays.toString(serializedResponseType) +
                '}';
    }
}

package io.github.cnadjim.axon.distributed.messaging;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.axonframework.common.AxonException;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;
import org.axonframework.messaging.RemoteNonTransientHandlingException;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

public abstract class ReplyQueryMessage implements Serializable {
    protected String queryIdentifier;
    protected byte[] serializedMetaData;

    protected String payloadType;
    protected String payloadRevision;
    protected byte[] serializedPayload;

    protected String exceptionType;
    protected String exceptionRevision;
    protected byte[] serializedException;

    protected String responseTypeType;
    protected String responseTypeRevision;
    protected byte[] serializedResponseType;

    protected ReplyQueryMessage() {
    }

    public ReplyQueryMessage(String queryIdentifier,
                             QueryResponseMessage<?> queryResponseMessage,
                             Serializer serializer,
                             ResponseType<?> responseType) {

        this.queryIdentifier = queryIdentifier;

        // MetaData
        SerializedObject<byte[]> metaData = queryResponseMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();

        // Exception (peut être "null" selon impl Axon / serializer)
        SerializedObject<byte[]> exception = queryResponseMessage.serializeExceptionResult(serializer, byte[].class);
        this.serializedException = exception.getData();
        this.exceptionType = exception.getType().getName();
        this.exceptionRevision = exception.getType().getRevision();

        // ✅ ResponseType (forSerialization recommandé par Axon)
        ResponseType<?> forSerialization = (responseType == null) ? null : responseType.forSerialization();
        if (forSerialization != null) {
            SerializedObject<byte[]> rt = serializer.serialize(forSerialization, byte[].class);
            this.serializedResponseType = rt.getData();
            this.responseTypeType = rt.getType().getName();
            this.responseTypeRevision = rt.getType().getRevision();
        }

        // Payload : si multiInstances, sérialiser en tableau typé (QuestionResponse[]) pour préserver le type élément
        SerializedObject<byte[]> payloadSerialized;
        try {
            Object payload = queryResponseMessage.getPayload(); // peut throw si exceptional selon impl
            Object transportPayload = adaptPayloadForTransport(payload, responseType);
            payloadSerialized = serializer.serialize(transportPayload, byte[].class);
        } catch (Exception ignored) {
            payloadSerialized = queryResponseMessage.serializePayload(serializer, byte[].class);
        }

        this.serializedPayload = payloadSerialized.getData();
        this.payloadType = payloadSerialized.getType().getName();
        this.payloadRevision = payloadSerialized.getType().getRevision();
    }

    public QueryResponseMessage<?> getQueryResponseMessage(Serializer serializer) {
        Object rawPayload = this.deserializePayload(serializer);
        RemoteExceptionDescription exceptionDescription = this.deserializeException(serializer);

        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        MetaData metaData = serializer.deserialize(serializedMetaData);

        if (exceptionDescription != null) {
            QueryExecutionException queryExecutionException = new QueryExecutionException(
                    "The remote query handler threw an exception",
                    this.convertToRemoteException(exceptionDescription),
                    rawPayload
            );
            return new GenericQueryResponseMessage<>(queryExecutionException, metaData);
        }

        ResponseType<?> responseType = this.deserializeResponseType(serializer);
        Object finalPayload = (responseType == null) ? rawPayload : responseType.convert(rawPayload);

        return new GenericQueryResponseMessage<>(finalPayload, metaData);
    }

    private static Object adaptPayloadForTransport(Object payload, ResponseType<?> responseType) {
        if (payload == null || responseType == null) {
            return payload;
        }

        // Cas critique : multipleInstancesOf(X) => on veut transporter X[] pour garder X au runtime
        if (responseType instanceof MultipleInstancesResponseType<?>) {
            Class<?> elementType = responseType.getExpectedResponseType();
            if (elementType == null) {
                return payload;
            }

            // Déjà un tableau : parfait (MultipleInstancesResponseType.convert gère arrays)
            if (payload.getClass().isArray()) {
                return payload;
            }

            // Collection => convertir en tableau typé
            if (payload instanceof Collection<?> collection) {
                Object array = Array.newInstance(elementType, collection.size());
                int i = 0;
                for (Object o : collection) {
                    Array.set(array, i++, o);
                }
                return array;
            }
        }

        return payload;
    }

    private AxonException convertToRemoteException(RemoteExceptionDescription exceptionDescription) {
        return exceptionDescription.isPersistent()
                ? new RemoteNonTransientHandlingException(exceptionDescription)
                : new RemoteHandlingException(exceptionDescription);
    }

    public String getQueryIdentifier() {
        return this.queryIdentifier;
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

    public String getExceptionType() {
        return this.exceptionType;
    }

    public String getExceptionRevision() {
        return this.exceptionRevision;
    }

    public byte[] getSerializedException() {
        return this.serializedException;
    }

    public byte[] getSerializedMetaData() {
        return this.serializedMetaData;
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
                payloadType,
                payloadRevision,
                Arrays.hashCode(serializedPayload),
                exceptionType,
                exceptionRevision,
                Arrays.hashCode(serializedException),
                Arrays.hashCode(serializedMetaData),
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
            ReplyQueryMessage other = (ReplyQueryMessage) obj;
            return Objects.equals(this.queryIdentifier, other.queryIdentifier) &&
                    Objects.equals(this.payloadType, other.payloadType) &&
                    Objects.equals(this.payloadRevision, other.payloadRevision) &&
                    Arrays.equals(this.serializedPayload, other.serializedPayload) &&
                    Objects.equals(this.exceptionType, other.exceptionType) &&
                    Objects.equals(this.exceptionRevision, other.exceptionRevision) &&
                    Arrays.equals(this.serializedException, other.serializedException) &&
                    Arrays.equals(this.serializedMetaData, other.serializedMetaData) &&
                    Objects.equals(this.responseTypeType, other.responseTypeType) &&
                    Objects.equals(this.responseTypeRevision, other.responseTypeRevision) &&
                    Arrays.equals(this.serializedResponseType, other.serializedResponseType);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ReplyQueryMessage{" +
                "queryIdentifier='" + queryIdentifier + '\'' +
                ", serializedMetaData=" + Arrays.toString(serializedMetaData) +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", serializedPayload=" + Arrays.toString(serializedPayload) +
                ", exceptionType='" + exceptionType + '\'' +
                ", exceptionRevision='" + exceptionRevision + '\'' +
                ", serializedException=" + Arrays.toString(serializedException) +
                ", responseTypeType='" + responseTypeType + '\'' +
                ", responseTypeRevision='" + responseTypeRevision + '\'' +
                ", serializedResponseType=" + Arrays.toString(serializedResponseType) +
                '}';
    }

    private Object deserializePayload(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(
                this.serializedPayload, byte[].class, this.payloadType, this.payloadRevision
        ));
    }

    private RemoteExceptionDescription deserializeException(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(
                this.serializedException, byte[].class, this.exceptionType, this.exceptionRevision
        ));
    }

    private ResponseType<?> deserializeResponseType(Serializer serializer) {
        if (this.serializedResponseType == null || this.responseTypeType == null) {
            return null;
        }
        return serializer.deserialize(new SimpleSerializedObject<>(
                this.serializedResponseType, byte[].class, this.responseTypeType, this.responseTypeRevision
        ));
    }
}

package io.github.cnadjim.axon.distributed.query.message;

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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Wire representation of a {@link QueryResponseMessage} sent back over the internal HTTP query
 * transport (PLAN.md §11.2). Adapted from {@code quizup-axon-starter}'s RabbitMQ-based
 * {@code SpringReplyQueryMessage}.
 * <p>
 * Correctness rules preserved from the original implementation (PLAN.md §11.2):
 * <ul>
 *     <li>An exceptional result produces an exceptional {@link QueryResponseMessage}, never an
 *     exception smuggled as a normal payload.</li>
 *     <li>{@code Optional<T>}/collection payloads are reconstructed through
 *     {@link ResponseType#convert(Object)} so the element type is preserved rather than
 *     degrading into a raw {@code LinkedHashMap}.</li>
 *     <li>For {@link MultipleInstancesResponseType}, the payload is transported as a typed array
 *     so the element type survives serialization.</li>
 * </ul>
 */
public class ReplyQueryMessage implements Serializable {

    private String queryIdentifier;
    private byte[] serializedMetaData;

    private String payloadType;
    private String payloadRevision;
    private byte[] serializedPayload;

    private String exceptionType;
    private String exceptionRevision;
    private byte[] serializedException;

    private String responseTypeType;
    private String responseTypeRevision;
    private byte[] serializedResponseType;

    @SuppressWarnings("unused")
    private ReplyQueryMessage() {
        // Used for JSON deserialization
    }

    public ReplyQueryMessage(String queryIdentifier,
                              QueryResponseMessage<?> queryResponseMessage,
                              Serializer serializer,
                              ResponseType<?> responseType) {
        this.queryIdentifier = queryIdentifier;

        SerializedObject<byte[]> metaData = queryResponseMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();

        SerializedObject<byte[]> exception = queryResponseMessage.serializeExceptionResult(serializer, byte[].class);
        this.serializedException = exception.getData();
        this.exceptionType = exception.getType().getName();
        this.exceptionRevision = exception.getType().getRevision();

        ResponseType<?> forSerialization = (responseType == null) ? null : responseType.forSerialization();
        if (forSerialization != null) {
            SerializedObject<byte[]> rt = serializer.serialize(forSerialization, byte[].class);
            this.serializedResponseType = rt.getData();
            this.responseTypeType = rt.getType().getName();
            this.responseTypeRevision = rt.getType().getRevision();
        }

        SerializedObject<byte[]> payloadSerialized;
        try {
            Object payload = queryResponseMessage.getPayload();
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
        Object rawPayload = deserializePayload(serializer);
        RemoteExceptionDescription exceptionDescription = deserializeException(serializer);

        SerializedMetaData<byte[]> serializedMetaDataObject = new SerializedMetaData<>(serializedMetaData, byte[].class);
        MetaData metaData = serializer.deserialize(serializedMetaDataObject);

        if (exceptionDescription != null) {
            QueryExecutionException queryExecutionException = new QueryExecutionException(
                    "The remote query handler threw an exception",
                    convertToRemoteException(exceptionDescription),
                    rawPayload
            );
            // Use the (Class<R>, Throwable, MetaData) constructor so the resulting message is
            // genuinely exceptional (isExceptional()=true) - NOT the (R, MetaData) overload,
            // which would silently treat the exception as a normal payload (PLAN.md §11.2).
            ResponseType<?> responseTypeForException = deserializeResponseType(serializer);
            Class<?> payloadType = (responseTypeForException != null)
                    ? responseTypeForException.responseMessagePayloadType()
                    : Object.class;
            return exceptionalResponse(payloadType, queryExecutionException, metaData);
        }

        ResponseType<?> responseType = deserializeResponseType(serializer);
        Object finalPayload = (responseType == null) ? rawPayload : responseType.convert(rawPayload);

        return new GenericQueryResponseMessage<>(finalPayload, metaData);
    }

    private static <R> QueryResponseMessage<R> exceptionalResponse(Class<R> payloadType, Exception exception,
                                                                    MetaData metaData) {
        return new GenericQueryResponseMessage<>(payloadType, exception, metaData);
    }

    private static Object adaptPayloadForTransport(Object payload, ResponseType<?> responseType) {
        if (payload == null || responseType == null) {
            return payload;
        }
        if (responseType instanceof MultipleInstancesResponseType<?>) {
            Class<?> elementType = responseType.getExpectedResponseType();
            if (elementType == null || payload.getClass().isArray()) {
                return payload;
            }
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

    private Object deserializePayload(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(
                serializedPayload, byte[].class, payloadType, payloadRevision
        ));
    }

    private RemoteExceptionDescription deserializeException(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(
                serializedException, byte[].class, exceptionType, exceptionRevision
        ));
    }

    private ResponseType<?> deserializeResponseType(Serializer serializer) {
        if (serializedResponseType == null || responseTypeType == null) {
            return null;
        }
        return serializer.deserialize(new SimpleSerializedObject<>(
                serializedResponseType, byte[].class, responseTypeType, responseTypeRevision
        ));
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

    public String getExceptionType() {
        return exceptionType;
    }

    public String getExceptionRevision() {
        return exceptionRevision;
    }

    public byte[] getSerializedException() {
        return serializedException;
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
        if (!(obj instanceof ReplyQueryMessage other)) {
            return false;
        }
        return Objects.equals(queryIdentifier, other.queryIdentifier)
                && Objects.equals(payloadType, other.payloadType)
                && Objects.equals(payloadRevision, other.payloadRevision)
                && Arrays.equals(serializedPayload, other.serializedPayload)
                && Objects.equals(exceptionType, other.exceptionType)
                && Objects.equals(exceptionRevision, other.exceptionRevision)
                && Arrays.equals(serializedException, other.serializedException)
                && Arrays.equals(serializedMetaData, other.serializedMetaData)
                && Objects.equals(responseTypeType, other.responseTypeType)
                && Objects.equals(responseTypeRevision, other.responseTypeRevision)
                && Arrays.equals(serializedResponseType, other.serializedResponseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryIdentifier, payloadType, payloadRevision, Arrays.hashCode(serializedPayload),
                exceptionType, exceptionRevision, Arrays.hashCode(serializedException),
                Arrays.hashCode(serializedMetaData), responseTypeType, responseTypeRevision,
                Arrays.hashCode(serializedResponseType));
    }

    @Override
    public String toString() {
        return "ReplyQueryMessage{queryIdentifier='" + queryIdentifier + "'}";
    }
}


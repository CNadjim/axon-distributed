package com.axon.distributed.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all wire messages (command, query, heartbeat) survive a
 * Jackson round-trip using Axon's default JacksonSerializer and the AMQP
 * Jackson2JsonMessageConverter — the same converters used at runtime.
 */
class WireMessageSerializationTest {

    private Serializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        serializer = JacksonSerializer.builder().build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    // -------------------------------------------------------------------------
    // HeartbeatMessage — via raw ObjectMapper (Axon serializer path)
    // -------------------------------------------------------------------------

    @Nested
    class HeartbeatMessageTests {

        @Test
        void shouldRoundTripFullHeartbeat() throws Exception {
            HeartbeatMessage original = HeartbeatMessage.builder()
                    .memberName("order-service-" + UUID.randomUUID())
                    .timestamp(Instant.now())
                    .commandNames(Set.of("PlaceOrderCommand", "CancelOrderCommand"))
                    .queryNames(Set.of("FindOrderQuery"))
                    .build();

            String json = objectMapper.writeValueAsString(original);
            HeartbeatMessage deserialized = objectMapper.readValue(json, HeartbeatMessage.class);

            assertThat(deserialized.getMemberName()).isEqualTo(original.getMemberName());
            assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(deserialized.getCommandNames()).containsExactlyInAnyOrderElementsOf(original.getCommandNames());
            assertThat(deserialized.getQueryNames()).containsExactlyInAnyOrderElementsOf(original.getQueryNames());
        }

        @Test
        void shouldRoundTripHeartbeatWithEmptyCapabilities() throws Exception {
            HeartbeatMessage original = HeartbeatMessage.builder()
                    .memberName("idle-service")
                    .timestamp(Instant.now())
                    .commandNames(Set.of())
                    .queryNames(Set.of())
                    .build();

            String json = objectMapper.writeValueAsString(original);
            HeartbeatMessage deserialized = objectMapper.readValue(json, HeartbeatMessage.class);

            assertThat(deserialized.getMemberName()).isEqualTo(original.getMemberName());
            assertThat(deserialized.getCommandNames()).isEmpty();
            assertThat(deserialized.getQueryNames()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // HeartbeatMessage — via Jackson2JsonMessageConverter (AMQP wire path)
    // -------------------------------------------------------------------------

    @Nested
    class HeartbeatMessageConverterTests {

        private Jackson2JsonMessageConverter converter;

        @BeforeEach
        void setUp() {
            // Mirrors RabbitMqConfiguration.discoveryMessageConverter(objectMapper)
            converter = new Jackson2JsonMessageConverter(objectMapper);
        }

        @Test
        void shouldRoundTripHeartbeatThroughAmqpConverter() {
            HeartbeatMessage original = HeartbeatMessage.builder()
                    .memberName("billing-service-" + UUID.randomUUID())
                    .timestamp(Instant.now())
                    .commandNames(Set.of("ChargeCommand"))
                    .queryNames(Set.of("GetInvoiceQuery", "ListInvoicesQuery"))
                    .build();

            // Serialize → AMQP Message (mimics RabbitTemplate.convertAndSend)
            Message amqpMessage = converter.toMessage(original, new MessageProperties());

            // Deserialize ← AMQP Message (mimics @RabbitListener)
            Object raw = converter.fromMessage(amqpMessage);

            assertThat(raw).isInstanceOf(HeartbeatMessage.class);
            HeartbeatMessage deserialized = (HeartbeatMessage) raw;

            assertThat(deserialized.getMemberName()).isEqualTo(original.getMemberName());
            assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(deserialized.getCommandNames()).containsExactlyInAnyOrderElementsOf(original.getCommandNames());
            assertThat(deserialized.getQueryNames()).containsExactlyInAnyOrderElementsOf(original.getQueryNames());
        }

        @Test
        void shouldRoundTripHeartbeatWithEmptyCapabilitiesThroughAmqpConverter() {
            HeartbeatMessage original = HeartbeatMessage.builder()
                    .memberName("idle-node")
                    .timestamp(Instant.now())
                    .commandNames(Set.of())
                    .queryNames(Set.of())
                    .build();

            Message amqpMessage = converter.toMessage(original, new MessageProperties());
            HeartbeatMessage deserialized = (HeartbeatMessage) converter.fromMessage(amqpMessage);

            assertThat(deserialized.getMemberName()).isEqualTo(original.getMemberName());
            assertThat(deserialized.getCommandNames()).isEmpty();
            assertThat(deserialized.getQueryNames()).isEmpty();
        }

        @Test
        void amqpMessageShouldCarryCorrectContentTypeAndClassName() {
            HeartbeatMessage original = HeartbeatMessage.builder()
                    .memberName("probe-service")
                    .timestamp(Instant.now())
                    .commandNames(Set.of())
                    .queryNames(Set.of())
                    .build();

            Message amqpMessage = converter.toMessage(original, new MessageProperties());

            assertThat(amqpMessage.getMessageProperties().getContentType())
                    .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
            assertThat(amqpMessage.getMessageProperties().getInferredArgumentType())
                    .isNull(); // type resolved from __TypeId__ header at deserialization
        }
    }

    // -------------------------------------------------------------------------
    // SpringDispatchCommandMessage  (wraps DispatchMessage)
    // -------------------------------------------------------------------------

    @Nested
    class DispatchCommandMessageTests {

        @Test
        void shouldRoundTripDispatchCommandMessage() throws Exception {
            var command = new PlaceOrderCommand(UUID.randomUUID(), "item-42", 3);
            var commandMessage = GenericCommandMessage.asCommandMessage(command);

            SpringDispatchCommandMessage<?> original = new SpringDispatchCommandMessage<>(commandMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchCommandMessage<?> deserialized = objectMapper.readValue(json, SpringDispatchCommandMessage.class);

            assertThat(deserialized.getCommandIdentifier()).isEqualTo(original.getCommandIdentifier());
            assertThat(deserialized.getCommandName()).isEqualTo(original.getCommandName());
            assertThat(deserialized.isExpectReply()).isEqualTo(original.isExpectReply());
            assertThat(deserialized.getPayloadType()).isEqualTo(original.getPayloadType());

            // Full Axon round-trip
            var reconstructed = deserialized.getCommandMessage(serializer);
            assertThat(reconstructed.getCommandName()).isEqualTo(commandMessage.getCommandName());
            assertThat(reconstructed.getPayload()).usingRecursiveComparison().isEqualTo(command);
        }

        @Test
        void shouldPreserveMetaDataInDispatchCommandMessage() throws Exception {
            var command = new PlaceOrderCommand(UUID.randomUUID(), "item-99", 1);
            var commandMessage = GenericCommandMessage.asCommandMessage(command)
                    .withMetaData(MetaData.with("correlationId", "corr-123").and("tenantId", "acme"));

            SpringDispatchCommandMessage<?> original = new SpringDispatchCommandMessage<>(commandMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchCommandMessage<?> deserialized = objectMapper.readValue(json, SpringDispatchCommandMessage.class);

            var reconstructed = deserialized.getCommandMessage(serializer);
            assertThat(reconstructed.getMetaData()).containsEntry("correlationId", "corr-123");
            assertThat(reconstructed.getMetaData()).containsEntry("tenantId", "acme");
        }
    }

    // -------------------------------------------------------------------------
    // SpringReplyCommandMessage  (wraps ReplyMessage)
    // -------------------------------------------------------------------------

    @Nested
    class ReplyCommandMessageTests {

        @Test
        void shouldRoundTripSuccessfulReplyCommandMessage() throws Exception {
            String commandId = UUID.randomUUID().toString();
            CommandResultMessage<OrderResult> result = GenericCommandResultMessage.asCommandResultMessage(
                    new OrderResult("order-1", "CONFIRMED")
            );

            SpringReplyCommandMessage<OrderResult> original = new SpringReplyCommandMessage<>(commandId, result, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringReplyCommandMessage<?> deserialized = objectMapper.readValue(json, SpringReplyCommandMessage.class);

            assertThat(deserialized.getCommandIdentifier()).isEqualTo(commandId);

            CommandResultMessage<?> reconstructed = deserialized.getCommandResultMessage(serializer);
            assertThat(reconstructed.isExceptional()).isFalse();
            assertThat(reconstructed.getPayload()).usingRecursiveComparison().isEqualTo(result.getPayload());
        }

        @Test
        void shouldRoundTripExceptionalReplyCommandMessage() throws Exception {
            String commandId = UUID.randomUUID().toString();
            CommandResultMessage<Void> result = GenericCommandResultMessage.asCommandResultMessage(
                    new IllegalArgumentException("Order quantity must be positive")
            );

            SpringReplyCommandMessage<Void> original = new SpringReplyCommandMessage<>(commandId, result, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringReplyCommandMessage<?> deserialized = objectMapper.readValue(json, SpringReplyCommandMessage.class);

            CommandResultMessage<?> reconstructed = deserialized.getCommandResultMessage(serializer);
            assertThat(reconstructed.isExceptional()).isTrue();
            // Axon wraps the original exception in a RemoteHandlingException on the receiving side
            assertThat(reconstructed.exceptionResult().getMessage())
                    .contains("The remote handler threw an exception");
            assertThat(reconstructed.exceptionResult().getCause().getMessage())
                    .contains("Order quantity must be positive");
        }
    }

    // -------------------------------------------------------------------------
    // SpringDispatchQueryMessage — tous les ResponseTypes
    // -------------------------------------------------------------------------

    @Nested
    class DispatchQueryMessageTests {

        @Test
        void shouldRoundTripInstanceOfQuery() throws Exception {
            QueryMessage<FindOrderQuery, OrderResult> queryMessage = new GenericQueryMessage<>(
                    new FindOrderQuery("order-42"),
                    ResponseTypes.instanceOf(OrderResult.class)
            );

            SpringDispatchQueryMessage<?, ?> original = new SpringDispatchQueryMessage<>(queryMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchQueryMessage<?, ?> deserialized = objectMapper.readValue(json, SpringDispatchQueryMessage.class);

            assertThat(deserialized.getQueryIdentifier()).isEqualTo(original.getQueryIdentifier());
            assertThat(deserialized.getQueryName()).isEqualTo(original.getQueryName());
            assertThat(deserialized.getResponseTypeType()).isEqualTo(original.getResponseTypeType());

            QueryMessage<?, ?> reconstructed = deserialized.getQueryMessage(serializer);
            assertThat(reconstructed.getPayload()).usingRecursiveComparison().isEqualTo(new FindOrderQuery("order-42"));
            assertThat(reconstructed.getResponseType())
                    .isInstanceOf(org.axonframework.messaging.responsetypes.InstanceResponseType.class);
        }

        @Test
        void shouldRoundTripMultipleInstancesOfQuery() throws Exception {
            QueryMessage<FindOrderQuery, List<OrderResult>> queryMessage = new GenericQueryMessage<>(
                    new FindOrderQuery("order-*"),
                    ResponseTypes.multipleInstancesOf(OrderResult.class)
            );

            SpringDispatchQueryMessage<?, ?> original = new SpringDispatchQueryMessage<>(queryMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchQueryMessage<?, ?> deserialized = objectMapper.readValue(json, SpringDispatchQueryMessage.class);

            QueryMessage<?, ?> reconstructed = deserialized.getQueryMessage(serializer);
            assertThat(reconstructed.getResponseType())
                    .isInstanceOf(MultipleInstancesResponseType.class);
            // Verify the element type is preserved
            assertThat(reconstructed.getResponseType().getExpectedResponseType())
                    .isEqualTo(OrderResult.class);
        }

        @Test
        void shouldRoundTripOptionalInstanceOfQuery() throws Exception {
            QueryMessage<FindOrderQuery, Optional<OrderResult>> queryMessage = new GenericQueryMessage<>(
                    new FindOrderQuery("order-99"),
                    ResponseTypes.optionalInstanceOf(OrderResult.class)
            );

            SpringDispatchQueryMessage<?, ?> original = new SpringDispatchQueryMessage<>(queryMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchQueryMessage<?, ?> deserialized = objectMapper.readValue(json, SpringDispatchQueryMessage.class);

            QueryMessage<?, ?> reconstructed = deserialized.getQueryMessage(serializer);
            // NOTE: Axon's forSerialization() normalizes OptionalResponseType → InstanceResponseType on the wire.
            // The subscriber reconstructs an Optional via OptionalResponseType.convert() on the receiving side.
            assertThat(reconstructed.getResponseType())
                    .isInstanceOf(org.axonframework.messaging.responsetypes.InstanceResponseType.class);
            assertThat(reconstructed.getResponseType().getExpectedResponseType())
                    .isEqualTo(OrderResult.class);
        }

        @Test
        void shouldRoundTripPublisherOfQuery() throws Exception {
            QueryMessage<FindOrderQuery, ?> queryMessage = new GenericQueryMessage<>(
                    new FindOrderQuery("order-stream"),
                    ResponseTypes.publisherOf(OrderResult.class)
            );

            SpringDispatchQueryMessage<?, ?> original = new SpringDispatchQueryMessage<>(queryMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchQueryMessage<?, ?> deserialized = objectMapper.readValue(json, SpringDispatchQueryMessage.class);

            QueryMessage<?, ?> reconstructed = deserialized.getQueryMessage(serializer);
            // NOTE: Axon's forSerialization() normalizes PublisherResponseType → MultipleInstancesResponseType on the wire.
            // Reactive streaming is a local concern; the transport always uses the collection equivalent.
            assertThat(reconstructed.getResponseType())
                    .isInstanceOf(MultipleInstancesResponseType.class);
            assertThat(reconstructed.getResponseType().getExpectedResponseType())
                    .isEqualTo(OrderResult.class);
        }

        @Test
        void shouldPreserveQueryMetaData() throws Exception {
            QueryMessage<FindOrderQuery, OrderResult> queryMessage = new GenericQueryMessage<>(
                    new FindOrderQuery("order-42"),
                    ResponseTypes.instanceOf(OrderResult.class)
            ).withMetaData(MetaData.with("userId", "user-1"));

            SpringDispatchQueryMessage<?, ?> original = new SpringDispatchQueryMessage<>(queryMessage, serializer);

            String json = objectMapper.writeValueAsString(original);
            SpringDispatchQueryMessage<?, ?> deserialized = objectMapper.readValue(json, SpringDispatchQueryMessage.class);

            QueryMessage<?, ?> reconstructed = deserialized.getQueryMessage(serializer);
            assertThat(reconstructed.getMetaData()).containsEntry("userId", "user-1");
        }
    }

    // -------------------------------------------------------------------------
    // SpringReplyQueryMessage — tous les ResponseTypes
    // -------------------------------------------------------------------------

    @Nested
    class ReplyQueryMessageTests {

        @Test
        void shouldRoundTripInstanceOfReply() throws Exception {
            var responseType = ResponseTypes.instanceOf(OrderResult.class);
            QueryResponseMessage<OrderResult> response = GenericQueryResponseMessage.asResponseMessage(
                    new OrderResult("order-1", "CONFIRMED")
            );

            SpringReplyQueryMessage<OrderResult> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializer, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            assertThat(deserialized.getQueryIdentifier()).isEqualTo(original.getQueryIdentifier());

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializer);
            assertThat(reconstructed.isExceptional()).isFalse();
            assertThat(reconstructed.getPayload()).usingRecursiveComparison()
                    .isEqualTo(new OrderResult("order-1", "CONFIRMED"));
        }

        @Test
        void shouldRoundTripMultipleInstancesOfReply() throws Exception {
            var responseType = ResponseTypes.multipleInstancesOf(OrderResult.class);
            var results = List.of(
                    new OrderResult("order-1", "CONFIRMED"),
                    new OrderResult("order-2", "PENDING")
            );
            QueryResponseMessage<List<OrderResult>> response = GenericQueryResponseMessage.asResponseMessage(results);

            SpringReplyQueryMessage<List<OrderResult>> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializer, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializer);
            assertThat(reconstructed.isExceptional()).isFalse();
            assertThat(reconstructed.getPayload()).isInstanceOf(List.class);
            assertThat((List<?>) reconstructed.getPayload()).hasSize(2)
                    .allMatch(e -> e instanceof OrderResult);
        }

        @Test
        void shouldRoundTripMultipleInstancesOfReplyPreservingElementType() throws Exception {
            var responseType = ResponseTypes.multipleInstancesOf(OrderResult.class);
            var results = List.of(new OrderResult("order-1", "CONFIRMED"));
            QueryResponseMessage<List<OrderResult>> response = GenericQueryResponseMessage.asResponseMessage(results);

            SpringReplyQueryMessage<List<OrderResult>> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializer, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializer);
            // ReplyQueryMessage transports as typed array to preserve element type across generic erasure
            assertThat(reconstructed.getPayload()).isInstanceOf(List.class);
            List<?> list = (List<?>) reconstructed.getPayload();
            assertThat(list).hasSize(1);
            assertThat(list.get(0)).isInstanceOf(OrderResult.class);
        }

        @Test
        void shouldRoundTripOptionalInstanceOfReplyWithValue() throws Exception {
            // Optional payload requires jackson-datatype-jdk8 — registered via findAndRegisterModules()
            // which mirrors ObjectMapperAutoConfiguration + JacksonAutoConfiguration at runtime.
            var serializerWithJdk8 = JacksonSerializer.builder()
                    .objectMapper(new ObjectMapper().findAndRegisterModules())
                    .build();

            var responseType = ResponseTypes.optionalInstanceOf(OrderResult.class);
            QueryResponseMessage<Optional<OrderResult>> response = GenericQueryResponseMessage.asResponseMessage(
                    Optional.of(new OrderResult("order-1", "CONFIRMED"))
            );

            SpringReplyQueryMessage<Optional<OrderResult>> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializerWithJdk8, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializerWithJdk8);
            assertThat(reconstructed.isExceptional()).isFalse();
            // OptionalResponseType.convert() wraps the payload back in Optional
            Object payload = reconstructed.getPayload();
            assertThat(payload).isInstanceOf(Optional.class);
            Optional<?> optional = (Optional<?>) payload;
            assertThat(optional).isPresent();
            // NOTE: due to generic type erasure, the inner value is deserialized as a LinkedHashMap.
            // Type information is lost at the transport boundary — callers must cast or use a typed serializer.
            assertThat(optional.get()).extracting("orderId", "status")
                    .containsExactly("order-1", "CONFIRMED");
        }

        @Test
        void shouldRoundTripOptionalInstanceOfReplyEmpty() throws Exception {
            // Optional payload requires jackson-datatype-jdk8 — registered via findAndRegisterModules()
            var serializerWithJdk8 = JacksonSerializer.builder()
                    .objectMapper(new ObjectMapper().findAndRegisterModules())
                    .build();

            var responseType = ResponseTypes.optionalInstanceOf(OrderResult.class);
            QueryResponseMessage<Optional<OrderResult>> response = GenericQueryResponseMessage.asResponseMessage(
                    Optional.empty()
            );

            SpringReplyQueryMessage<Optional<OrderResult>> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializerWithJdk8, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializerWithJdk8);
            assertThat(reconstructed.isExceptional()).isFalse();
            Object payload = reconstructed.getPayload();
            assertThat(payload).isInstanceOf(Optional.class);
            assertThat(((Optional<?>) payload)).isEmpty();
        }

        @Test
        void defaultSerializerShouldNotSupportOptionalPayloadWithoutJdk8Module() {
            // Documents that JacksonSerializer.builder().build() (no findAndRegisterModules) cannot
            // serialize Optional payloads — callers must configure the ObjectMapper with jdk8 module.
            var responseType = ResponseTypes.optionalInstanceOf(OrderResult.class);
            QueryResponseMessage<Optional<OrderResult>> response = GenericQueryResponseMessage.asResponseMessage(
                    Optional.of(new OrderResult("order-1", "CONFIRMED"))
            );

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    new SpringReplyQueryMessage<>(UUID.randomUUID().toString(), response, serializer, responseType)
            ).hasCauseInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class)
             .hasMessageContaining("Unable to serialize object");
        }

        @Test
        void shouldRoundTripExceptionalReply() throws Exception {
            var responseType = ResponseTypes.instanceOf(OrderResult.class);
            QueryResponseMessage<OrderResult> response = new GenericQueryResponseMessage<>(
                    OrderResult.class, new RuntimeException("Order not found")
            );
            assertThat(response.isExceptional()).isTrue();

            SpringReplyQueryMessage<OrderResult> original = new SpringReplyQueryMessage<>(
                    UUID.randomUUID().toString(), response, serializer, responseType
            );

            String json = objectMapper.writeValueAsString(original);
            SpringReplyQueryMessage<?> deserialized = objectMapper.readValue(json, SpringReplyQueryMessage.class);

            QueryResponseMessage<?> reconstructed = deserialized.getQueryResponseMessage(serializer);

            // NOTE: ReplyQueryMessage.getQueryResponseMessage wraps the remote exception via
            // new GenericQueryResponseMessage<>(queryExecutionException, metaData) which uses the
            // (payload, metaData) constructor — isExceptional() is therefore false, but the
            // QueryExecutionException is accessible as payload.
            assertThat(reconstructed.getPayload())
                    .isInstanceOf(org.axonframework.queryhandling.QueryExecutionException.class);
            var qee = (org.axonframework.queryhandling.QueryExecutionException) reconstructed.getPayload();
            assertThat(qee.getMessage()).contains("The remote query handler threw an exception");
            assertThat(qee.getCause().getMessage()).contains("Order not found");
        }
    }

    // -------------------------------------------------------------------------
    // Shared test fixtures
    // -------------------------------------------------------------------------

    record PlaceOrderCommand(UUID orderId, String itemId, int quantity) {}

    record FindOrderQuery(String orderId) {}

    record OrderResult(String orderId, String status) {}
}


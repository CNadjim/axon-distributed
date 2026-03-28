package com.axon.distributed.deadline;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializerRoundTripTest {

    private Serializer eventSerializer;

    @BeforeEach
    void setUp() {
        // Uses Axon's default JacksonSerializer — no custom ObjectMapper needed.
        eventSerializer = JacksonSerializer.builder().build();
    }

    @Test
    void shouldRoundTripSimplePayload() {
        DeadlinePayload payload = new DeadlinePayload(UUID.randomUUID(), "invoice.created", Instant.now());

        Object deserialized = roundTrip(payload);

        assertThat(deserialized)
                .usingRecursiveComparison()
                .isEqualTo(payload);
    }

    @Test
    void shouldRoundTripNestedPayload() {
        NestedDeadlinePayload payload = new NestedDeadlinePayload(
                "deadline-nested",
                new NestedData("order-42", 3),
                List.of("A", "B", "C"),
                Map.of("source", "billing", "tenant", "acme")
        );

        Object deserialized = roundTrip(payload);

        assertThat(deserialized)
                .usingRecursiveComparison()
                .isEqualTo(payload);
    }

    @Test
    void shouldRoundTripRecordPayload() {
        RecordDeadlinePayload payload = new RecordDeadlinePayload("record", Instant.now().plusSeconds(120));

        Object deserialized = roundTrip(payload);

        assertThat(deserialized)
                .usingRecursiveComparison()
                .isEqualTo(payload);
    }

    @Test
    void transientAutowiredFieldShouldNotBeSerializedNorDeserialized() throws Exception {
        // Simulate Spring injection via constructor (field is final)
        ServiceAwarePayload payload = new ServiceAwarePayload("order-99", new SomeService("injected-before-serialization"));

        // --- Serialize ---
        SerializedObject<byte[]> serialized = eventSerializer.serialize(payload, byte[].class);
        String json = new String(serialized.getData());

        // The transient/@JsonIgnore field must be absent from the wire format
        assertThat(json)
                .as("transient @JsonIgnore field must not appear in serialized JSON")
                .doesNotContain("injectedService")
                .doesNotContain("injected-before-serialization");

        // --- Deserialize ---
        ServiceAwarePayload deserialized = (ServiceAwarePayload) eventSerializer.deserialize(serialized);

        // Business fields are preserved
        assertThat(deserialized.orderId).isEqualTo(payload.orderId);

        // The transient field is null after deserialization (Spring would inject it at runtime)
        assertThat(deserialized.injectedService)
                .as("transient @JsonIgnore field must be null after deserialization — Spring injection is expected at runtime")
                .isNull();
    }

    private Object roundTrip(Object payload) {
        SerializedObject<byte[]> serialized = eventSerializer.serialize(payload, byte[].class);
        return eventSerializer.deserialize(serialized);
    }

    static class DeadlinePayload {
        private UUID id;
        private String type;
        private Instant createdAt;

        @SuppressWarnings("unused")
        DeadlinePayload() {}

        DeadlinePayload(UUID id, String type, Instant createdAt) {
            this.id = id;
            this.type = type;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public String getType() { return type; }
        public Instant getCreatedAt() { return createdAt; }
    }

    static class NestedDeadlinePayload {
        private String name;
        private NestedData data;
        private List<String> tags;
        private Map<String, String> attributes;

        @SuppressWarnings("unused")
        NestedDeadlinePayload() {}

        NestedDeadlinePayload(String name, NestedData data, List<String> tags, Map<String, String> attributes) {
            this.name = name;
            this.data = data;
            this.tags = tags;
            this.attributes = attributes;
        }

        public String getName() { return name; }
        public NestedData getData() { return data; }
        public List<String> getTags() { return tags; }
        public Map<String, String> getAttributes() { return attributes; }
    }

    static class NestedData {
        private String aggregateId;
        private int attempt;

        @SuppressWarnings("unused")
        NestedData() {}

        NestedData(String aggregateId, int attempt) {
            this.aggregateId = aggregateId;
            this.attempt = attempt;
        }

        public String getAggregateId() { return aggregateId; }
        public int getAttempt() { return attempt; }
    }

    record RecordDeadlinePayload(String label, Instant triggerAt) {}

    /**
     * Payload that holds a transient service dependency.
     * In production, {@code injectedService} is populated by Spring after deserialization
     * (e.g., via @Configurable or manual lookup). It must never travel over the wire.
     */
    static class ServiceAwarePayload {
        String orderId;

        /** Transient + final: excluded from serialization, cannot be set after construction. */
        transient final SomeService injectedService;

        @SuppressWarnings("unused")
        ServiceAwarePayload() {
            this.injectedService = null; // Jackson deserialization path — Spring injects at runtime
        }

        ServiceAwarePayload(String orderId) {
            this.orderId = orderId;
            this.injectedService = null;
        }

        ServiceAwarePayload(String orderId, SomeService injectedService) {
            this.orderId = orderId;
            this.injectedService = injectedService;
        }

        public String getOrderId() { return orderId; }
    }

    static class SomeService {
        final String name;

        SomeService(String name) { this.name = name; }

        public String getName() { return name; }
    }
}

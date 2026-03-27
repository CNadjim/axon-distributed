package com.axon.distributed.deadline;

import com.axon.distributed.config.JacksonConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@ContextConfiguration(classes = JacksonConfiguration.class)
class EventSerializerRoundTripTest {

    @Autowired
    @Qualifier("defaultAxonObjectMapper")
    private ObjectMapper defaultAxonObjectMapper;

    private Serializer eventSerializer;

    @BeforeEach
    void setUp() {
        eventSerializer = JacksonSerializer.builder()
                .objectMapper(defaultAxonObjectMapper)
                .build();
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
    void shouldRoundTripFieldOnlyPayloadConfiguredByJacksonConfiguration() {
        FieldOnlyPayload payload = new FieldOnlyPayload(UUID.randomUUID(), Instant.now(), "field-only");

        Object deserialized = roundTrip(payload);

        assertThat(deserialized)
                .usingRecursiveComparison()
                .isEqualTo(payload);
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
        DeadlinePayload() {
            // Default constructor for Jackson.
        }

        DeadlinePayload(UUID id, String type, Instant createdAt) {
            this.id = id;
            this.type = type;
            this.createdAt = createdAt;
        }

        public UUID getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    static class NestedDeadlinePayload {
        private String name;
        private NestedData data;
        private List<String> tags;
        private Map<String, String> attributes;

        @SuppressWarnings("unused")
        NestedDeadlinePayload() {
            // Default constructor for Jackson.
        }

        NestedDeadlinePayload(String name, NestedData data, List<String> tags, Map<String, String> attributes) {
            this.name = name;
            this.data = data;
            this.tags = tags;
            this.attributes = attributes;
        }

        public String getName() {
            return name;
        }

        public NestedData getData() {
            return data;
        }

        public List<String> getTags() {
            return tags;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }
    }

    static class NestedData {
        private String aggregateId;
        private int attempt;

        @SuppressWarnings("unused")
        NestedData() {
            // Default constructor for Jackson.
        }

        NestedData(String aggregateId, int attempt) {
            this.aggregateId = aggregateId;
            this.attempt = attempt;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public int getAttempt() {
            return attempt;
        }
    }

    record RecordDeadlinePayload(String label, Instant triggerAt) {
    }

    static class FieldOnlyPayload {
        private UUID id;
        private Instant triggerAt;
        private String category;

        @SuppressWarnings("unused")
        FieldOnlyPayload() {
            // Default constructor for Jackson.
        }

        FieldOnlyPayload(UUID id, Instant triggerAt, String category) {
            this.id = id;
            this.triggerAt = triggerAt;
            this.category = category;
        }
    }
}

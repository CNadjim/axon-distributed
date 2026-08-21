package io.github.cnadjim.axon.distributed.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AxonDistributedEnvironmentPostProcessorTest {

    private final AxonDistributedEnvironmentPostProcessor postProcessor = new AxonDistributedEnvironmentPostProcessor();

    @Test
    void whenEnabledByDefault_thenDefaultsAreApplied() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("axon.axonserver.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("axon.distributed.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("axon.distributed.spring-cloud.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("axon.kafka.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("axon.kafka.default-topic")).isEqualTo("Axon.Events");
    }

    @Test
    void whenExplicitlyDisabled_thenNoDefaultIsApplied() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("axon.starter.enabled", "false");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("axon.axonserver.enabled")).isNull();
        assertThat(environment.getProperty("axon.distributed.enabled")).isNull();
        assertThat(environment.getProperty("axon.kafka.enabled")).isNull();
    }

    @Test
    void whenUserAlreadyDefinesAProperty_thenUserValueWins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("axon.kafka.default-topic", "my-custom-topic");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("axon.kafka.default-topic")).isEqualTo("my-custom-topic");
    }
}

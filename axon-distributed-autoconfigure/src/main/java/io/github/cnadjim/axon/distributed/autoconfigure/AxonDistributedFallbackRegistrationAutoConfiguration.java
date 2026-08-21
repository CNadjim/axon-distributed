package io.github.cnadjim.axon.distributed.autoconfigure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.net.URI;
import java.util.Collections;
import java.util.Map;


@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(Registration.class)
@ConditionalOnProperty(prefix = "axon.starter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AxonDistributedFallbackRegistrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Registration.class)
    public Registration axonDistributedFallbackRegistration(
            @Value("${spring.application.name:axon-distributed-application}") String serviceId,
            @Value("${server.address:localhost}") String host,
            @Value("${server.port:8080}") int port) {
        return new SimpleRegistration(serviceId, host, port);
    }

    private record SimpleRegistration(String serviceId, String host, int port) implements Registration {

        @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public String getHost() {
                return host;
            }

            @Override
            public int getPort() {
                return port;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public URI getUri() {
                return URI.create("http://" + host + ":" + port);
            }

            @Override
            public Map<String, String> getMetadata() {
                return Collections.emptyMap();
            }
        }
}
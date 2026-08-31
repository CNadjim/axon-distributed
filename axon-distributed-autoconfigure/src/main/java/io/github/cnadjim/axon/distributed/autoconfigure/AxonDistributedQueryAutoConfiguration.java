package io.github.cnadjim.axon.distributed.autoconfigure;

import io.github.cnadjim.axon.distributed.query.HttpQueryBusConnector;
import io.github.cnadjim.axon.distributed.query.QueryCapabilityRegistry;
import io.github.cnadjim.axon.distributed.query.SpringCloudDistributedQueryBus;
import io.github.cnadjim.axon.distributed.query.SpringCloudQueryRouter;
import io.github.cnadjim.axon.distributed.query.api.QueryCapabilitiesController;
import io.github.cnadjim.axon.distributed.query.api.QueryTransportController;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


@AutoConfiguration(after = AxonAutoConfiguration.class)
public class AxonDistributedQueryAutoConfiguration {

    @Bean
    public QueryCapabilityRegistry queryCapabilityRegistry() {
        return new QueryCapabilityRegistry();
    }

    @Bean("axonKafkaQueryDispatchExecutor")
    public Executor axonKafkaQueryDispatchExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("axon-kafka-query-dispatch");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public HttpQueryBusConnector httpQueryBusConnector(RestTemplate restTemplate,
                                                       Serializer serializer) {
        return new HttpQueryBusConnector(restTemplate, serializer);
    }

    @Bean
    public SpringCloudQueryRouter springCloudQueryRouter(DiscoveryClient discoveryClient,
                                                         Registration registration,
                                                         HttpQueryBusConnector connector) {
        return new SpringCloudQueryRouter(discoveryClient, registration, connector);
    }

    @Primary
    @Bean("distributedQueryBus")
    public QueryBus distributedQueryBus(@Qualifier("localSegment") QueryBus localQueryBus,
                                        QueryCapabilityRegistry capabilityRegistry,
                                        SpringCloudQueryRouter router,
                                        HttpQueryBusConnector connector,
                                        @Qualifier("axonKafkaQueryDispatchExecutor") Executor axonKafkaQueryDispatchExecutor) {
        return new SpringCloudDistributedQueryBus(localQueryBus, capabilityRegistry, router, connector,
                axonKafkaQueryDispatchExecutor);
    }

    @Bean
    public QueryCapabilitiesController queryCapabilitiesController(QueryCapabilityRegistry registry,
                                                                   Registration registration) {
        return new QueryCapabilitiesController(registry, registration);
    }

    @Bean
    public QueryTransportController queryTransportController(@Qualifier("localSegment") QueryBus localQueryBus,
                                                             Serializer serializer) {
        return new QueryTransportController(localQueryBus, serializer);
    }

}

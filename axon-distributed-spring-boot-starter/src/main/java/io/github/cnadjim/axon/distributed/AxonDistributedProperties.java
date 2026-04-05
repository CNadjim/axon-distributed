package io.github.cnadjim.axon.distributed;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Configuration properties for Axon Distributed Framework.
 * <p>
 * Provides configuration for Event Store, Token Store, Command Bus, Event Bus,
 * Query Bus, Saga Store, Dead Letter Queue, and Service Discovery.
 */
@ConfigurationProperties(prefix = "axon.distributed")
public class AxonDistributedProperties {
    private boolean enabled = true;
    private EventStoreProperties eventStore = new EventStoreProperties();
    private TokenStoreProperties tokenStore = new TokenStoreProperties();
    private CommandBusProperties commandBus = new CommandBusProperties();
    private EventBusProperties eventBus = new EventBusProperties();
    private QueryBusProperties queryBus = new QueryBusProperties();
    private SagaStoreProperties sagaStore = new SagaStoreProperties();
    private DeadLetterProperties deadLetter = new DeadLetterProperties();
    private ServiceDiscoveryProperties serviceDiscovery = new ServiceDiscoveryProperties();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public EventStoreProperties getEventStore() {
        return eventStore;
    }

    public void setEventStore(EventStoreProperties eventStore) {
        this.eventStore = eventStore;
    }

    public TokenStoreProperties getTokenStore() {
        return tokenStore;
    }

    public void setTokenStore(TokenStoreProperties tokenStore) {
        this.tokenStore = tokenStore;
    }

    public CommandBusProperties getCommandBus() {
        return commandBus;
    }

    public void setCommandBus(CommandBusProperties commandBus) {
        this.commandBus = commandBus;
    }

    public EventBusProperties getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBusProperties eventBus) {
        this.eventBus = eventBus;
    }

    public QueryBusProperties getQueryBus() {
        return queryBus;
    }

    public void setQueryBus(QueryBusProperties queryBus) {
        this.queryBus = queryBus;
    }

    public SagaStoreProperties getSagaStore() {
        return sagaStore;
    }

    public void setSagaStore(SagaStoreProperties sagaStore) {
        this.sagaStore = sagaStore;
    }

    public DeadLetterProperties getDeadLetter() {
        return deadLetter;
    }

    public void setDeadLetter(DeadLetterProperties deadLetter) {
        this.deadLetter = deadLetter;
    }

    public ServiceDiscoveryProperties getServiceDiscovery() {
        return serviceDiscovery;
    }

    public void setServiceDiscovery(ServiceDiscoveryProperties serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * Event Store configuration properties.
     */
    public static class EventStoreProperties {
        private int snapshotThreshold = 250;
        private int batchSize = 100;

        public int getSnapshotThreshold() {
            return snapshotThreshold;
        }

        public void setSnapshotThreshold(int snapshotThreshold) {
            this.snapshotThreshold = snapshotThreshold;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /**
     * Token Store configuration properties.
     */
    public static class TokenStoreProperties {
        private long claimTimeout = 10000;
        private long claimUpdateInterval = 5000;
        private String nodeId = "axon-node-" + UUID.randomUUID();

        public long getClaimTimeout() {
            return claimTimeout;
        }

        public void setClaimTimeout(long claimTimeout) {
            this.claimTimeout = claimTimeout;
        }

        public long getClaimUpdateInterval() {
            return claimUpdateInterval;
        }

        public void setClaimUpdateInterval(long claimUpdateInterval) {
            this.claimUpdateInterval = claimUpdateInterval;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
    }

    /**
     * Command Bus configuration properties.
     */
    public static class CommandBusProperties {
        public static final String DEFAULT_EXCHANGE = "axon.commands";
        private String exchange = DEFAULT_EXCHANGE;
        private long timeout = 30000;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Event Bus configuration properties.
     */
    public static class EventBusProperties {
        public static final String DEFAULT_EXCHANGE = "axon.events";
        private String exchange = DEFAULT_EXCHANGE;
        private long timeout = 10000;
        private int batchSize = 100;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /**
     * Query Bus configuration properties.
     */
    public static class QueryBusProperties {
        public static final String DEFAULT_EXCHANGE = "axon.queries";
        private String exchange = DEFAULT_EXCHANGE;
        private long timeout = 10000;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Dead Letter Queue configuration properties.
     * <p>
     * Controls retry behavior with exponential backoff before routing
     * failed messages to a Dead Letter Queue (DLQ).
     */
    public static class DeadLetterProperties {
        public static final String DEFAULT_EXCHANGE = "axon.deadletter";
        private String exchange = DEFAULT_EXCHANGE;
        /** Maximum number of delivery attempts (including the first). */
        private int maxRetries = 3;
        private boolean enabled = true;
        /** Initial retry interval in milliseconds. */
        private long initialInterval = 1000;
        /** Multiplier for exponential backoff between retries. */
        private double multiplier = 2.0;
        /** Maximum retry interval in milliseconds. */
        private long maxInterval = 10000;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(long initialInterval) {
            this.initialInterval = initialInterval;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public long getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(long maxInterval) {
            this.maxInterval = maxInterval;
        }
    }

    /**
     * Saga Store configuration properties.
     */
    public static class SagaStoreProperties {
        private long claimTimeout = 10000;

        public long getClaimTimeout() {
            return claimTimeout;
        }

        public void setClaimTimeout(long claimTimeout) {
            this.claimTimeout = claimTimeout;
        }
    }

    /**
     * Service Discovery configuration properties.
     */
    public static class ServiceDiscoveryProperties {
        public static final String DEFAULT_EXCHANGE = "axon.discovery.heartbeat";
        private String exchange = DEFAULT_EXCHANGE;
        private long heartbeatInterval = 5000;
        private long staleThreshold = 15000;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public long getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(long heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public long getStaleThreshold() {
            return staleThreshold;
        }

        public void setStaleThreshold(long staleThreshold) {
            this.staleThreshold = staleThreshold;
        }
    }
}

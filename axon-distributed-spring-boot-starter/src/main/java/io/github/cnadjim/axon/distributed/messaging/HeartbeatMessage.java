package io.github.cnadjim.axon.distributed.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Message sent periodically by services to announce their presence.
 * Contains service identification and metadata for service discovery.
 */
public class HeartbeatMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String memberName;
    private final Instant timestamp;
    private final Set<String> commandNames;
    private final Set<String> queryNames;

    @JsonCreator
    public HeartbeatMessage(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("memberName") String memberName,
            @JsonProperty("queryNames") Set<String> queryNames,
            @JsonProperty("commandNames") Set<String> commandNames) {
        this.memberName = memberName;
        this.timestamp = timestamp;
        this.commandNames = commandNames;
        this.queryNames = queryNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMemberName() {
        return memberName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Set<String> getCommandNames() {
        return commandNames;
    }

    public Set<String> getQueryNames() {
        return queryNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeartbeatMessage that = (HeartbeatMessage) o;
        return Objects.equals(memberName, that.memberName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberName);
    }

    @Override
    public String toString() {
        return "HeartbeatMessage {" +
                "memberName='" + memberName + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static class Builder {
        private String memberName;
        private Instant timestamp;
        private Set<String> queryNames;
        private Set<String> commandNames;

        public Builder memberName(String memberName) {
            this.memberName = memberName;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder queryNames(Set<String> queryNames) {
            this.queryNames = queryNames;
            return this;
        }

        public Builder commandNames(Set<String> commandNames) {
            this.commandNames = commandNames;
            return this;
        }

        public HeartbeatMessage build() {
            return new HeartbeatMessage(timestamp, memberName, queryNames, commandNames);
        }
    }
}

package io.github.cnadjim.axon.distributed.member;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Optional;
import java.util.Set;

public interface CustomMember extends Member {

    int getLoadFactor();

    Set<String> getCommandCapabilities();

    Set<String> getQueryCapabilities();

    default boolean canHandleCommand(CommandMessage<?> commandMessage) {
        return getCommandCapabilities().contains(commandMessage.getCommandName());
    }

    default boolean canHandleQuery(QueryMessage<?, ?> queryMessage) {
        return getQueryCapabilities().contains(queryMessage.getQueryName());
    }

    @Override
    default void suspect() {
        // No implementation needed for this example
    }
    @Override
    default <T> Optional<T> getConnectionEndpoint(Class<T> protocol) {
        return Optional.empty();
    }
}

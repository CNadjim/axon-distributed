package com.axon.distributed.member;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.queryhandling.QueryMessage;

public interface LocalMember extends CustomMember {

    @Override
    default int getLoadFactor() {
        return 100;
    }

    @Override
    default boolean local() {
        return true;
    }

    void addCommandCapability(String commandName);

    void addQueryCapability(String queryName);

    default void addCommandCapability(CommandMessage<?> commandMessage) {
        addCommandCapability(commandMessage.getCommandName());
    }

    default void addQueryCapability(QueryMessage<?, ?> queryMessage) {
        addQueryCapability(queryMessage.getQueryName());
    }
}

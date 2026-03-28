package io.github.cnadjim.axon.distributed.discovery;

import io.github.cnadjim.axon.distributed.member.CustomMember;
import io.github.cnadjim.axon.distributed.member.LocalMember;
import io.github.cnadjim.axon.distributed.member.RemoteMember;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Collection;

public interface MemberRegistry {
    void save(RemoteMember member);

    LocalMember getLocalMember();

    Collection<CustomMember> findMemberForCommand(CommandMessage<?> commandMessage);

    Collection<CustomMember> findMemberForQuery(QueryMessage<?, ?> queryMessage);

    default void addCommandCapability(String commandName) {
        LocalMember member = getLocalMember();
        member.addCommandCapability(commandName);
    }

    default void addQueryCapability(String queryName) {
        LocalMember member = getLocalMember();
        member.addQueryCapability(queryName);
    }
}

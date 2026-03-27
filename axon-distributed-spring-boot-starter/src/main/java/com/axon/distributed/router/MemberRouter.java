package com.axon.distributed.router;

import com.axon.distributed.member.CustomMember;
import com.axon.distributed.discovery.MemberRegistry;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.queryhandling.QueryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public class MemberRouter implements CustomRouter {

    private static final Logger logger = LoggerFactory.getLogger(MemberRouter.class);
    private final MemberRegistry memberRegistry;

    public MemberRouter(MemberRegistry memberRegistry) {
        this.memberRegistry = memberRegistry;
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> message) {
        Collection<CustomMember> members = memberRegistry.findMemberForCommand(message);

        if (CollectionUtils.isEmpty(members)) {
            logger.warn("No member found to handle command: {}", message.getCommandName());
        }

        return members
                .stream()
                .min(Comparator.comparingInt(CustomMember::getLoadFactor))
                .map(member -> (Member) member);
    }

    @Override
    public void updateMembership(int loadFactor, CommandMessageFilter commandFilter) {
        // No implementation needed for this example
    }

    @Override
    public Optional<Member> findDestination(QueryMessage<?, ?> message) {
        Collection<CustomMember> members = memberRegistry.findMemberForQuery(message);

        if (CollectionUtils.isEmpty(members)) {
            logger.warn("No member found to handle query: {}", message.getQueryName());
        }

        return members
                .stream()
                .min(Comparator.comparingInt(CustomMember::getLoadFactor))
                .map(member -> (Member) member);

    }
}

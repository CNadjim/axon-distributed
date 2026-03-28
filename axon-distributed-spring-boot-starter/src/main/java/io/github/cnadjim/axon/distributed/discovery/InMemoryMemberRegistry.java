package io.github.cnadjim.axon.distributed.discovery;

import io.github.cnadjim.axon.distributed.member.CustomMember;
import io.github.cnadjim.axon.distributed.member.LocalMember;
import io.github.cnadjim.axon.distributed.member.RemoteMember;
import io.github.cnadjim.axon.distributed.member.SpringLocalMember;
import io.github.cnadjim.axon.distributed.messaging.HeartbeatMessage;
import io.github.cnadjim.axon.distributed.mapper.MemberMapper;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryMemberRegistry implements MemberRegistry {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryMemberRegistry.class);
    private final LocalMember localMember;
    private final Map<String, RemoteMember> remoteMembers = new ConcurrentHashMap<>();
    private final long staleThreshold;
    private final String heartbeatExchange;
    private final RabbitTemplate rabbitTemplate;

    public InMemoryMemberRegistry(
            String heartbeatExchange,
            String localMemberName,
            long staleThreshold,
            RabbitTemplate rabbitTemplate) {
        this.heartbeatExchange = heartbeatExchange;
        this.localMember = new SpringLocalMember(localMemberName);
        this.staleThreshold = staleThreshold;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void save(RemoteMember member) {
        remoteMembers.put(member.name(), member);
    }

    @Override
    public LocalMember getLocalMember() {
        return localMember;
    }


    @Override
    public Collection<CustomMember> findMemberForCommand(CommandMessage<?> commandMessage) {
        if (localMember.canHandleCommand(commandMessage)) {
            return Collections.singleton(localMember);
        }

        return remoteMembers.values()
                .stream()
                .filter(member -> member.canHandleCommand(commandMessage))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<CustomMember> findMemberForQuery(QueryMessage<?, ?> queryMessage) {
        if (localMember.canHandleQuery(queryMessage)) {
            return Collections.singleton(localMember);
        }

        return remoteMembers.values()
                .stream()
                .filter(member -> member.canHandleQuery(queryMessage))
                .collect(Collectors.toList());
    }


    @RabbitListener(queues = "#{heartbeatQueue.name}")
    public void onHeartbeat(HeartbeatMessage message) {
        try {
            RemoteMember remoteMember = MemberMapper.toMember(message);
            if (remoteMember.name().equals(localMember.name())) {
                //logger.debug("Ignoring heartbeat from self: {}", remoteMember.name());
            } else {
                save(remoteMember);
                //logger.debug("Processed heartbeat from member: {}", remoteMember.name());
            }
        } catch (Exception exception) {
            logger.error("Failed to process heartbeat from {}", message, exception);
        }
    }

    @Scheduled(fixedRateString = "${axon.distributed.service-discovery.heartbeat-interval:5000}")
    public void cleanupStaleMembers() {
        Instant threshold = Instant.now().minusMillis(staleThreshold);

        List<String> staleMembers = remoteMembers.entrySet().stream()
                .filter(predicate -> predicate.getValue().getLastHeartbeat().isBefore(threshold))
                .map(Map.Entry::getKey)
                .toList();

        staleMembers.forEach(memberId -> {
            remoteMembers.remove(memberId);
            logger.warn("Removed stale member: {}", memberId);
        });
    }

    @Scheduled(fixedRateString = "${axon.distributed.service-discovery.heartbeat-interval:5000}")
    public void publishHeartbeat() {
        try {
            HeartbeatMessage heartbeatMessage = MemberMapper.toHeartbeatMessage(localMember);
            rabbitTemplate.convertAndSend(heartbeatExchange, "", heartbeatMessage);
            logger.debug("Published heartbeat for member: {}", localMember.name());
        } catch (Exception exception) {
            logger.error("Failed to publish heartbeat", exception);
        }
    }
}

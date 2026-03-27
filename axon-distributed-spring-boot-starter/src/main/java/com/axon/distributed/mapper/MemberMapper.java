package com.axon.distributed.mapper;

import com.axon.distributed.member.LocalMember;
import com.axon.distributed.member.RemoteMember;
import com.axon.distributed.member.SpringRemoteMember;
import com.axon.distributed.messaging.HeartbeatMessage;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;

public final class MemberMapper {
    private MemberMapper() {
        // private constructor to prevent instantiation
    }

    public static RemoteMember toMember(HeartbeatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        String memberName = message.getMemberName();

        if (memberName == null || memberName.isBlank()) {
            throw new IllegalArgumentException("HeartbeatMessage must contain a valid serviceId");
        }

        return new SpringRemoteMember(memberName, calculateLoadFactor(), Instant.now(), message.getQueryNames(), message.getCommandNames());
    }


    public static HeartbeatMessage toHeartbeatMessage(LocalMember member) {
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }

        return HeartbeatMessage.builder()
                .memberName(member.name())
                .timestamp(Instant.now())
                .commandNames(member.getCommandCapabilities())
                .queryNames(member.getQueryCapabilities())
                .build();
    }

    private static int calculateLoadFactor() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double load = osBean.getSystemLoadAverage();
        int processors = osBean.getAvailableProcessors();

        if (load < 0) {
            return 50; // Default if unavailable
        }

        return (int) Math.min(100, (load / processors) * 100);
    }
}

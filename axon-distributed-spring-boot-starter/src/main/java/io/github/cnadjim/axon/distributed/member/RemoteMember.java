package io.github.cnadjim.axon.distributed.member;

import java.time.Instant;

public interface RemoteMember extends CustomMember {

    Instant getLastHeartbeat();

    @Override
    default boolean local() {
        return false;
    }
}

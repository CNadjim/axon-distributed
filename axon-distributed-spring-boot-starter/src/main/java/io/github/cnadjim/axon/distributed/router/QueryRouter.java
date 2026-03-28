package io.github.cnadjim.axon.distributed.router;

import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Optional;

public interface QueryRouter {

    Optional<Member> findDestination(QueryMessage<?,?> message);
}

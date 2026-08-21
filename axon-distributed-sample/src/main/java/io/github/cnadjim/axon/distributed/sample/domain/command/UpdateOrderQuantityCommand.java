package io.github.cnadjim.axon.distributed.sample.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UpdateOrderQuantityCommand(@TargetAggregateIdentifier String orderId, int quantity) {
}

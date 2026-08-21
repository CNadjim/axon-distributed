package io.github.cnadjim.axon.distributed.sample.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CreateOrderCommand(@TargetAggregateIdentifier String orderId, String product, int quantity) {
}

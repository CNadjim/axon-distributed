package io.github.cnadjim.axon.distributed.sample.domain.event;

public record OrderQuantityUpdatedEvent(String orderId, int quantity) {

}

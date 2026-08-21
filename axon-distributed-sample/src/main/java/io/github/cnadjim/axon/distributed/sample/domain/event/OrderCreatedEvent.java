package io.github.cnadjim.axon.distributed.sample.domain.event;

public record OrderCreatedEvent(String orderId, String product, int quantity) {

}

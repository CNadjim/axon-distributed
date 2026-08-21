package io.github.cnadjim.axon.distributed.sample.application;

import io.github.cnadjim.axon.distributed.sample.domain.event.OrderCreatedEvent;
import io.github.cnadjim.axon.distributed.sample.infrastructure.entity.OrderEntity;
import io.github.cnadjim.axon.distributed.sample.infrastructure.repository.OrderRepository;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class OrderProjection {
    private static final Logger logger = LoggerFactory.getLogger(OrderProjection.class);

    private final OrderRepository orderRepository;

    public OrderProjection(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @EventHandler
    public void on(OrderCreatedEvent event) {
        OrderEntity orderEntity = new OrderEntity(event.orderId(), event.product(), event.quantity());
        orderRepository.save(orderEntity);
    }
}

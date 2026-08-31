package io.github.cnadjim.axon.distributed.sample.application;

import io.github.cnadjim.axon.distributed.sample.domain.query.FindOrderById;
import io.github.cnadjim.axon.distributed.sample.infrastructure.entity.OrderEntity;
import io.github.cnadjim.axon.distributed.sample.infrastructure.repository.OrderRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderQueryHandler {
    private final OrderRepository orderRepository;

    public OrderQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @QueryHandler
    public OrderEntity handle(FindOrderById query) {
        return orderRepository.findById(query.orderId())
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + query.orderId()));
    }
}

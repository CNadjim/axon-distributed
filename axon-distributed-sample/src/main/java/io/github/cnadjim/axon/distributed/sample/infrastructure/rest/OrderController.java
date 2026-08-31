package io.github.cnadjim.axon.distributed.sample.infrastructure.rest;

import io.github.cnadjim.axon.distributed.sample.domain.command.CreateOrderCommand;
import io.github.cnadjim.axon.distributed.sample.domain.query.FindOrderById;
import io.github.cnadjim.axon.distributed.sample.infrastructure.entity.OrderEntity;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public OrderController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    public CompletableFuture<String> create(@RequestBody CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        return commandGateway
                .send(new CreateOrderCommand(orderId, request.product(), request.quantity()))
                .thenApply(result -> orderId);
    }

    @GetMapping("/{orderId}")
    public CompletableFuture<OrderEntity> get(@PathVariable("orderId") String orderId) {
        return queryGateway
                .query(new FindOrderById(orderId), ResponseTypes.instanceOf(OrderEntity.class));
    }

    public record CreateOrderRequest(String product, int quantity) {
    }
}

package io.github.cnadjim.axon.distributed.sample.domain;

import io.github.cnadjim.axon.distributed.sample.domain.command.CreateOrderCommand;
import io.github.cnadjim.axon.distributed.sample.domain.command.UpdateOrderQuantityCommand;
import io.github.cnadjim.axon.distributed.sample.domain.event.OrderCreatedEvent;
import io.github.cnadjim.axon.distributed.sample.domain.event.OrderQuantityUpdatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
public class OrderAggregate {

    @AggregateIdentifier
    private String orderId;
    private String product;
    private int quantity;

    protected OrderAggregate() {
        // requis par Axon
    }

    @CommandHandler
    public OrderAggregate(CreateOrderCommand command) {
        AggregateLifecycle.apply(new OrderCreatedEvent(command.orderId(), command.product(), command.quantity()));
    }

    @CommandHandler
    public OrderAggregate(UpdateOrderQuantityCommand command) {
        AggregateLifecycle.apply(new OrderQuantityUpdatedEvent(command.orderId(), command.quantity()));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent event) {
        this.orderId = event.orderId();
        this.product = event.product();
        this.quantity = event.quantity();
    }

    @EventSourcingHandler
    public void on(OrderQuantityUpdatedEvent event) {
        this.quantity = event.quantity();
    }
}

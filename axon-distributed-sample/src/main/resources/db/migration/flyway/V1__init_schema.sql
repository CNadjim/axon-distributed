-- Flyway migration script
-- Corresponds to: io.github.cnadjim.axon.distributed.sample.infrastructure.entity.OrderEntity

CREATE TABLE order_entry
(
    order_id VARCHAR(255) NOT NULL,
    product  VARCHAR(255),
    quantity INT,
    CONSTRAINT pk_order_entry PRIMARY KEY (order_id)
);
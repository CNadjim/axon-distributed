package io.github.cnadjim.axon.distributed.sample.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "order_entry")
public class OrderEntity {

    @Id
    @Column(name = "order_id", length = 255, nullable = false)
    private String orderId;

    @Column(name = "product", length = 255)
    private String product;

    @Column(name = "quantity", length = 255)
    private int quantity;

    public OrderEntity(){

    }
    public OrderEntity(
            String userId,
            String product,
            int quantity
    ){
        this.orderId = userId;
        this.product = product;
        this.quantity = quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String userId) {
        this.orderId = userId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

}
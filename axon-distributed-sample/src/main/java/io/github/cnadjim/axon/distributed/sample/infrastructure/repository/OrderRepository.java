package io.github.cnadjim.axon.distributed.sample.infrastructure.repository;

import io.github.cnadjim.axon.distributed.sample.infrastructure.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}

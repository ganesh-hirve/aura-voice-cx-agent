package com.ganesh.aura.repository;

import com.ganesh.aura.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}

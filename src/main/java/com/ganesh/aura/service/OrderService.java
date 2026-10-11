package com.ganesh.aura.service;

import com.ganesh.aura.entity.Order;
import com.ganesh.aura.normalization.OrderIdNormalizer;
import com.ganesh.aura.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order getOrder(String orderId) {
        String normalizedOrderId = OrderIdNormalizer.normalize(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return repository.findByOrderId(normalizedOrderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}

package com.ganesh.aura.dto;

import com.ganesh.aura.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderResponse(
        String orderId,
        String customerName,
        String product,
        BigDecimal amount,
        String status,
        String courier,
        String trackingNumber,
        LocalDate expectedDelivery,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getOrderId(), order.getCustomerName(), order.getProduct(),
                order.getAmount(), order.getStatus(), order.getCourier(), order.getTrackingNumber(),
                order.getExpectedDelivery(), order.getDeliveredAt(), order.getCreatedAt());
    }
}

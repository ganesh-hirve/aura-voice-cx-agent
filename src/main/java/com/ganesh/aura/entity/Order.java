package com.ganesh.aura.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;
    private String customerName;
    private String product;
    private BigDecimal amount;
    private String status;
    private String courier;
    private String trackingNumber;
    private LocalDate expectedDelivery;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;

    protected Order() {
    }

    public String getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getProduct() { return product; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getCourier() { return courier; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDate getExpectedDelivery() { return expectedDelivery; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

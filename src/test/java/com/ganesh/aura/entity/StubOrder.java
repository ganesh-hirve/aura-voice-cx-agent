package com.ganesh.aura.entity;

import java.math.BigDecimal;

/** Simple test entity without reflection or persistence. */
public final class StubOrder extends Order {

    private final String testOrderId;
    private final String testCustomerName;
    private final String testStatus;
    private final String testProduct;
    private final BigDecimal testAmount;

    public StubOrder(String orderId, String customerName, String status) {
        this(orderId, customerName, status, null, null);
    }

    public StubOrder(String orderId, String customerName, String status, String product, BigDecimal amount) {
        this.testOrderId = orderId;
        this.testCustomerName = customerName;
        this.testStatus = status;
        this.testProduct = product;
        this.testAmount = amount;
    }

    @Override
    public String getOrderId() {
        return testOrderId;
    }

    @Override
    public String getCustomerName() {
        return testCustomerName;
    }

    @Override
    public String getStatus() {
        return testStatus;
    }

    @Override
    public String getProduct() {
        return testProduct;
    }

    @Override
    public BigDecimal getAmount() {
        return testAmount;
    }
}

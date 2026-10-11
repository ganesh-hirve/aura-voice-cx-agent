package com.ganesh.aura.dto;

public record OrderToolResult(
        Code code,
        String message,
        OrderResponse order,
        String confirmationId) {

    public enum Code {
        ORDER_FOUND,
        ORDER_NOT_FOUND,
        VERIFICATION_REQUIRED,
        VERIFICATION_FAILED,
        CUSTOMER_VERIFIED,
        ACTION_NOT_PERMITTED,
        CONFIRMATION_REQUIRED,
        AUTHORIZED_TO_PROCEED,
        INVALID_INPUT
    }
}

package com.ganesh.aura.dto;

public record AuthorizationResult(
        Status status,
        String orderId,
        String confirmationId,
        String reason) {

    public enum Status {
        ORDER_NOT_FOUND,
        VERIFICATION_REQUIRED,
        VERIFICATION_FAILED,
        CUSTOMER_VERIFIED,
        ACTION_NOT_PERMITTED,
        CONFIRMATION_REQUIRED,
        ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION
    }
}

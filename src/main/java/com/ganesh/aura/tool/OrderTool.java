package com.ganesh.aura.tool;

import com.ganesh.aura.dto.AuthorizationResult;
import com.ganesh.aura.dto.OrderResponse;
import com.ganesh.aura.dto.OrderToolResult;
import com.ganesh.aura.entity.Order;
import com.ganesh.aura.normalization.OrderIdNormalizer;
import com.ganesh.aura.service.AuthorizationService;
import com.ganesh.aura.service.OrderNotFoundException;
import com.ganesh.aura.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.ganesh.aura.dto.AuthorizationResult.Status;
import static com.ganesh.aura.dto.OrderToolResult.Code;

/** Typed application tools for order operations; it performs no order mutations. */
@Component
public class OrderTool {

    private static final Pattern CONFIRMATION_ID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final OrderService orderService;
    private final AuthorizationService authorizationService;

    public OrderTool(OrderService orderService, AuthorizationService authorizationService) {
        this.orderService = orderService;
        this.authorizationService = authorizationService;
    }

    public OrderToolResult lookupOrder(LookupOrderInput input) {
        String orderId = input == null ? null : normalizeOrderId(input.orderId());
        if (input == null || !validSessionId(input.sessionId()) || orderId == null) {
            return invalidInput("A valid session ID and order ID are required.");
        }

        AuthorizationResult authorization = authorizationService.authorizeProtectedAction(
                input.sessionId(), orderId);
        if (authorization.status() != Status.CUSTOMER_VERIFIED) {
            if (authorization.status() == Status.VERIFICATION_REQUIRED
                    || authorization.status() == Status.VERIFICATION_FAILED) {
                authorizationService.bindVerificationOrder(input.sessionId(), authorization.orderId());
            } else {
                authorizationService.clearPendingVerification(input.sessionId());
            }
            return fromAuthorization(authorization);
        }

        authorizationService.clearPendingVerification(input.sessionId());

        try {
            Order order = orderService.getOrder(authorization.orderId());
            return new OrderToolResult(Code.ORDER_FOUND,
                    "Order details retrieved for the verified customer.", OrderResponse.from(order), null);
        } catch (OrderNotFoundException missingAfterAuthorization) {
            return new OrderToolResult(Code.ORDER_NOT_FOUND,
                    "No order was found for the supplied order ID.", null, null);
        }
    }

    public OrderToolResult verifyOrderCustomer(VerifyOrderCustomerInput input) {
        String orderId = input == null ? null : normalizeOrderId(input.orderId());
        if (input == null || !validSessionId(input.sessionId()) || orderId == null
                || !validText(input.customerName(), 200)) {
            return invalidInput("A valid session ID, order ID, and customer name are required.");
        }

        // Only the typed authorization outcome is returned; the stored customer name is never included.
        AuthorizationResult verification = authorizationService.verifyCustomer(
                input.sessionId(), orderId, input.customerName());
        return fromAuthorization(verification);
    }

    public OrderToolResult requestCancellation(RequestCancellationInput input) {
        String orderId = input == null ? null : normalizeOrderId(input.orderId());
        if (input == null || !validSessionId(input.sessionId()) || orderId == null) {
            return invalidInput("A valid session ID and order ID are required.");
        }

        AuthorizationResult authorization = authorizationService.requestCancellation(
                input.sessionId(), orderId);
        return fromAuthorization(authorization);
    }

    public OrderToolResult confirmCancellation(ConfirmCancellationInput input) {
        String orderId = input == null ? null : normalizeOrderId(input.orderId());
        if (input == null || !validSessionId(input.sessionId()) || orderId == null
                || !validConfirmationId(input.confirmationId()) || !validText(input.customerConfirmation(), 100)) {
            return invalidInput("A valid session ID, order ID, confirmation ID, and explicit confirmation are required.");
        }

        AuthorizationResult confirmed = authorizationService.confirmCancellation(
                input.sessionId(), orderId, input.confirmationId(), input.customerConfirmation());
        if (confirmed.status() != Status.ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION) {
            return fromAuthorization(confirmed);
        }

        // Consume the confirmation only after a fresh order lookup and policy evaluation.
        AuthorizationResult revalidated = authorizationService.revalidateCancellation(
                input.sessionId(), orderId, input.confirmationId());
        return fromAuthorization(revalidated);
    }

    private static OrderToolResult fromAuthorization(AuthorizationResult authorization) {
        Code code = switch (authorization.status()) {
            case ORDER_NOT_FOUND -> Code.ORDER_NOT_FOUND;
            case VERIFICATION_REQUIRED -> Code.VERIFICATION_REQUIRED;
            case VERIFICATION_FAILED -> Code.VERIFICATION_FAILED;
            case CUSTOMER_VERIFIED -> Code.CUSTOMER_VERIFIED;
            case ACTION_NOT_PERMITTED -> Code.ACTION_NOT_PERMITTED;
            case CONFIRMATION_REQUIRED -> Code.CONFIRMATION_REQUIRED;
            case ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION -> Code.AUTHORIZED_TO_PROCEED;
        };
        return new OrderToolResult(code, authorization.reason(), null, authorization.confirmationId());
    }

    private static OrderToolResult invalidInput(String message) {
        return new OrderToolResult(Code.INVALID_INPUT, message, null, null);
    }

    private static boolean validSessionId(String sessionId) {
        return validText(sessionId, 128);
    }

    private static String normalizeOrderId(String orderId) {
        return OrderIdNormalizer.normalize(orderId).orElse(null);
    }

    private static boolean validConfirmationId(String confirmationId) {
        return confirmationId != null
                && CONFIRMATION_ID_PATTERN.matcher(confirmationId.trim()).matches();
    }

    private static boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    public record LookupOrderInput(String sessionId, String orderId) {
    }

    public record VerifyOrderCustomerInput(String sessionId, String orderId, String customerName) {
    }

    public record RequestCancellationInput(String sessionId, String orderId) {
    }

    /** customerConfirmation is the customer's raw confirmation phrase, not an LLM-derived boolean. */
    public record ConfirmCancellationInput(
            String sessionId,
            String orderId,
            String confirmationId,
            String customerConfirmation) {
    }
}

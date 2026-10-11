package com.ganesh.aura.service;

import com.ganesh.aura.dto.AuthorizationResult;
import com.ganesh.aura.entity.Order;
import com.ganesh.aura.normalization.OrderIdNormalizer;
import com.ganesh.aura.session.SessionState;
import com.ganesh.aura.session.SessionState.PendingAction;
import com.ganesh.aura.session.SessionState.PendingConfirmation;
import com.ganesh.aura.session.SessionManager;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static com.ganesh.aura.dto.AuthorizationResult.Status;

@Service
public class AuthorizationService {

    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(15);
    private static final Duration VERIFICATION_FAILURE_TTL = Duration.ofMinutes(2);
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(2);

    private final OrderService orderService;
    private final PolicyService policyService;
    private final SessionManager sessionManager;
    private final Clock clock;

    public AuthorizationService(
            OrderService orderService,
            PolicyService policyService,
            SessionManager sessionManager,
            Clock clock) {
        this.orderService = orderService;
        this.policyService = policyService;
        this.sessionManager = sessionManager;
        this.clock = clock;
    }

    public AuthorizationResult verifyCustomer(String sessionId, String orderId, String suppliedCustomerName) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null || isBlank(suppliedCustomerName)) {
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "A session, order ID, and customer name are required for verification.");
        }

        SessionState session = sessionManager.getOrCreate(sessionId);
        Instant now = clock.instant();
        if (!session.isPendingVerificationFor(normalizedOrderId, now)
                && !session.isOrderVerified(normalizedOrderId, now)) {
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "Request this order's details before verifying its customer.");
        }

        // Check existence only after the session has a backend-established binding,
        // so the verification endpoint cannot be used to probe arbitrary order IDs.
        Order order = findOrder(normalizedOrderId);
        if (order == null) {
            return result(Status.ORDER_NOT_FOUND, normalizedOrderId, null,
                    "No order was found for the supplied order ID.");
        }

        String suppliedName = normalizeName(suppliedCustomerName);
        String storedName = normalizeName(order.getCustomerName());
        if (suppliedName == null || storedName == null || !suppliedName.equals(storedName)) {
            session.invalidateOrderVerification(normalizedOrderId);
            session.markVerificationFailed(normalizedOrderId,
                    clock.instant().plus(VERIFICATION_FAILURE_TTL));
            return result(Status.VERIFICATION_FAILED, normalizedOrderId, null,
                    "The supplied name could not be verified for this order.");
        }

        session.markOrderVerified(normalizedOrderId, clock.instant().plus(VERIFICATION_TTL));
        return result(Status.CUSTOMER_VERIFIED, normalizedOrderId, null,
                "The customer is verified for this order in the current session.");
    }

    /** Binds the next name verification to an existing order requested through the backend lookup flow. */
    public void bindVerificationOrder(String sessionId, String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null || findOrder(normalizedOrderId) == null) {
            return;
        }
        sessionManager.getOrCreate(sessionId).bindPendingVerification(
                normalizedOrderId, clock.instant().plus(VERIFICATION_TTL));
    }

    public void clearPendingVerification(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        sessionManager.getOrCreate(sessionId).clearPendingVerification();
    }

    /** True only when this session already has a backend-bound pending or verified order. */
    public boolean isOrderBoundToSession(String sessionId, String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null) {
            return false;
        }
        SessionState session = sessionManager.getOrCreate(sessionId);
        Instant now = clock.instant();
        return session.isPendingVerificationFor(normalizedOrderId, now)
                || session.isOrderVerified(normalizedOrderId, now);
    }

    /** Confirms only that this session may access protected information for this order. */
    public AuthorizationResult authorizeProtectedAction(String sessionId, String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null) {
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "A session and order ID are required.");
        }
        if (findOrder(normalizedOrderId) == null) {
            return result(Status.ORDER_NOT_FOUND, normalizedOrderId, null,
                    "No order was found for the supplied order ID.");
        }
        SessionState session = sessionManager.getOrCreate(sessionId);
        if (!session.isOrderVerified(normalizedOrderId, clock.instant())) {
            if (session.hasRecentVerificationFailure(normalizedOrderId, clock.instant())) {
                return result(Status.VERIFICATION_FAILED, normalizedOrderId, null,
                        "The previous name verification failed. Verify the customer again before accessing protected details.");
            }
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "Verify the customer for this order before accessing protected details.");
        }
        return result(Status.CUSTOMER_VERIFIED, normalizedOrderId, null,
                "The customer is verified for this order in the current session.");
    }

    /** Starts cancellation authorization; it does not cancel or mutate the order. */
    public AuthorizationResult requestCancellation(String sessionId, String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null) {
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "A verified session and order ID are required.");
        }

        Order order = findOrder(normalizedOrderId);
        if (order == null) {
            return result(Status.ORDER_NOT_FOUND, normalizedOrderId, null,
                    "No order was found for the supplied order ID.");
        }

        SessionState session = sessionManager.getOrCreate(sessionId);
        if (!session.isOrderVerified(normalizedOrderId, clock.instant())) {
            if (session.hasRecentVerificationFailure(normalizedOrderId, clock.instant())) {
                return result(Status.VERIFICATION_FAILED, normalizedOrderId, null,
                        "The previous name verification failed. Verify the customer again before requesting cancellation.");
            }
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "Verify the customer for this order before requesting cancellation.");
        }

        PolicyService.EligibilityResult eligibility = policyService.evaluateCancellation(order.getStatus());
        if (eligibility.decision() != PolicyService.Decision.ELIGIBLE) {
            session.clearPendingConfirmation();
            return result(Status.ACTION_NOT_PERMITTED, normalizedOrderId, null,
                    "Cancellation is not permitted under the current order status and policy.");
        }

        String confirmationId = UUID.randomUUID().toString();
        session.setPendingConfirmation(new PendingConfirmation(
                confirmationId, PendingAction.CANCEL_ORDER, normalizedOrderId,
                clock.instant().plus(CONFIRMATION_TTL), false));
        return result(Status.CONFIRMATION_REQUIRED, normalizedOrderId, confirmationId,
                "Please explicitly confirm cancellation of this order within 2 minutes.");
    }

    /**
     * Accepts a narrowly recognized customer affirmation tied to the active order and
     * confirmation ID, then re-fetches the order and reevaluates its current policy status.
     */
    public AuthorizationResult confirmCancellation(
            String sessionId, String orderId, String confirmationId, String customerResponse) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null || isBlank(confirmationId)) {
            return result(Status.CONFIRMATION_REQUIRED, normalizedOrderId, confirmationId,
                    "A current cancellation confirmation for this order is required.");
        }

        SessionState session = sessionManager.getOrCreate(sessionId);
        if (!session.isOrderVerified(normalizedOrderId, clock.instant())) {
            session.clearPendingConfirmation();
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "Verify the customer for this order before confirming cancellation.");
        }
        if (!hasMatchingPendingConfirmation(session, normalizedOrderId, confirmationId)) {
            return result(Status.ACTION_NOT_PERMITTED, normalizedOrderId, null,
                    "There is no active cancellation confirmation for this order.");
        }
        if (session.isApprovedConfirmation(normalizedOrderId, confirmationId, clock.instant())) {
            return result(Status.ACTION_NOT_PERMITTED, normalizedOrderId, null,
                    "This cancellation confirmation has already been submitted.");
        }
        if (!isExplicitAffirmative(customerResponse)) {
            if (isExplicitNegative(customerResponse)) {
                session.clearPendingConfirmation();
                return result(Status.ACTION_NOT_PERMITTED, normalizedOrderId, null,
                        "The customer did not confirm cancellation.");
            }
            return result(Status.CONFIRMATION_REQUIRED, normalizedOrderId, confirmationId,
                    "An explicit affirmative response is required to confirm this cancellation.");
        }
        if (!session.markConfirmationApproved(normalizedOrderId, confirmationId, clock.instant())) {
            return result(Status.ACTION_NOT_PERMITTED, normalizedOrderId, null,
                    "There is no active cancellation confirmation for this order.");
        }

        return revalidateConfirmedCancellation(session, normalizedOrderId, confirmationId, false);
    }

    /** Re-fetches and reevaluates status immediately before a downstream executor acts. */
    public AuthorizationResult revalidateCancellation(
            String sessionId, String orderId, String confirmationId) {
        String normalizedOrderId = normalizeOrderId(orderId);
        if (isBlank(sessionId) || normalizedOrderId == null || isBlank(confirmationId)) {
            return result(Status.CONFIRMATION_REQUIRED, normalizedOrderId, confirmationId,
                    "A current confirmed cancellation request is required.");
        }
        SessionState session = sessionManager.getOrCreate(sessionId);
        if (!session.isOrderVerified(normalizedOrderId, clock.instant())) {
            session.clearPendingConfirmation();
            return result(Status.VERIFICATION_REQUIRED, normalizedOrderId, null,
                    "Verification expired or is missing; verify the customer again.");
        }
        if (!session.isApprovedConfirmation(normalizedOrderId, confirmationId, clock.instant())) {
            return result(Status.CONFIRMATION_REQUIRED, normalizedOrderId, null,
                    "A matching, unexpired affirmative cancellation confirmation is required.");
        }
        return revalidateConfirmedCancellation(session, normalizedOrderId, confirmationId, true);
    }

    private AuthorizationResult revalidateConfirmedCancellation(
            SessionState session, String orderId, String confirmationId, boolean consumeOnSuccess) {
        Order currentOrder = findOrder(orderId);
        if (currentOrder == null) {
            session.clearPendingConfirmation();
            return result(Status.ORDER_NOT_FOUND, orderId, null,
                    "No order was found for the supplied order ID.");
        }

        PolicyService.EligibilityResult eligibility = policyService.evaluateCancellation(currentOrder.getStatus());
        if (eligibility.decision() != PolicyService.Decision.ELIGIBLE) {
            session.clearPendingConfirmation();
            return result(Status.ACTION_NOT_PERMITTED, orderId, null,
                    "Cancellation is no longer permitted under the current order status and policy.");
        }
        if (consumeOnSuccess) {
            session.clearPendingConfirmation();
        }
        return result(Status.ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION, orderId, confirmationId,
                "Authorization is valid for the current status. Revalidate immediately before execution; no cancellation was performed.");
    }

    private boolean hasMatchingPendingConfirmation(SessionState session, String orderId, String confirmationId) {
        return session.getPendingConfirmation(clock.instant())
                .filter(pending -> pending.action() == PendingAction.CANCEL_ORDER
                        && pending.orderId().equals(orderId)
                        && pending.confirmationId().equals(confirmationId))
                .isPresent();
    }

    private Order findOrder(String orderId) {
        try {
            return orderService.getOrder(orderId);
        } catch (OrderNotFoundException notFound) {
            return null;
        }
    }

    private static boolean isExplicitAffirmative(String response) {
        String normalized = normalizeResponse(response);
        return normalized != null && switch (normalized) {
            case "yes cancel", "yes cancel it", "confirm cancel",
                    "confirm cancellation", "please cancel", "please cancel it" -> true;
            default -> false;
        };
    }

    private static boolean isExplicitNegative(String response) {
        String normalized = normalizeResponse(response);
        return normalized != null && switch (normalized) {
            case "no", "no cancel", "do not cancel", "dont cancel", "do not cancel it", "dont cancel it" -> true;
            default -> false;
        };
    }

    private static String normalizeResponse(String response) {
        if (isBlank(response)) {
            return null;
        }
        return response.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}\\s]", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static String normalizeName(String name) {
        if (isBlank(name)) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeOrderId(String orderId) {
        return OrderIdNormalizer.normalize(orderId).orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AuthorizationResult result(
            Status status, String orderId, String confirmationId, String reason) {
        return new AuthorizationResult(status, orderId, confirmationId, reason);
    }
}

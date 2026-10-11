package com.ganesh.aura.service;

import com.ganesh.aura.dto.AuthorizationResult;
import com.ganesh.aura.entity.Order;
import com.ganesh.aura.entity.StubOrder;
import com.ganesh.aura.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.ganesh.aura.dto.AuthorizationResult.Status;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTests {

    private static final String SESSION = "session-a";

    private OrderService orderService;
    private SessionManager sessionManager;
    private AuthorizationService authorizationService;
    private MutableClock clock;
    private Order processingOrder;
    private Order outForDeliveryOrder;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-10T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        processingOrder = new StubOrder("ORD-103", "Ananya Patel", "Processing");
        outForDeliveryOrder = new StubOrder("ORD-101", "Priya Sharma", "Out for Delivery");
        orderService = new InMemoryOrderService(Map.of(
                "ORD-103", processingOrder,
                "ORD-101", outForDeliveryOrder));
        sessionManager = new SessionManager();
        authorizationService = new AuthorizationService(
                orderService, new PolicyService(clock), sessionManager, clock);
    }

    @Test
    void verifiesNormalizedCustomerNameWithoutReturningStoredName() {
        authorizationService.bindVerificationOrder(SESSION, "ORD-103");
        AuthorizationResult result = authorizationService.verifyCustomer(
                SESSION, " ord-103 ", "  ANANYA   PATEL ");

        assertThat(result.status()).isEqualTo(Status.CUSTOMER_VERIFIED);
        assertThat(result.reason()).doesNotContain("Ananya Patel");
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-103").status())
                .isEqualTo(Status.CUSTOMER_VERIFIED);
    }

    @Test
    void rejectsAnIncorrectCustomerNameAndInvalidatesPriorVerification() {
        authorizationService.bindVerificationOrder(SESSION, "ORD-103");
        authorizationService.verifyCustomer(SESSION, "ORD-103", "Ananya Patel");
        AuthorizationResult result = authorizationService.verifyCustomer(
                SESSION, "ORD-103", "Ananya Patil");

        assertThat(result.status()).isEqualTo(Status.VERIFICATION_FAILED);
        assertThat(result.reason()).doesNotContain("Ananya Patel");
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-103").status())
                .isEqualTo(Status.VERIFICATION_FAILED);
    }

    @Test
    void requiresBackendOrderBindingBeforeVerificationAndDoesNotExposeUnknownOrderDetails() {
        AuthorizationResult result = authorizationService.verifyCustomer(
                SESSION, "ORD-999", "Ananya Patel");

        assertThat(result.status()).isEqualTo(Status.VERIFICATION_REQUIRED);
        assertThat(result.reason()).doesNotContain("Ananya Patel");
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-999").status())
                .isEqualTo(Status.ORDER_NOT_FOUND);
    }

    @Test
    void rejectsVerificationForAnOrderDifferentFromTheBackendBoundOrder() {
        authorizationService.bindVerificationOrder(SESSION, "ORD-103");

        AuthorizationResult result = authorizationService.verifyCustomer(
                SESSION, "ORD-101", "Ananya Patel");

        assertThat(result.status()).isEqualTo(Status.VERIFICATION_REQUIRED);
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-101").status())
                .isEqualTo(Status.VERIFICATION_REQUIRED);
    }

    @Test
    void requiresVerificationBeforeProtectedActionsOrCancellation() {
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-103").status())
                .isEqualTo(Status.VERIFICATION_REQUIRED);
        assertThat(authorizationService.requestCancellation(SESSION, "ORD-103").status())
                .isEqualTo(Status.VERIFICATION_REQUIRED);
    }

    @Test
    void verificationIsIsolatedBySessionAndExpires() {
        verifyAnanya();
        assertThat(authorizationService.authorizeProtectedAction("session-b", "ORD-103").status())
                .isEqualTo(Status.VERIFICATION_REQUIRED);

        clock.advance(Duration.ofMinutes(16));
        assertThat(authorizationService.authorizeProtectedAction(SESSION, "ORD-103").status())
                .isEqualTo(Status.VERIFICATION_REQUIRED);
    }

    @Test
    void cancellationRequestRequiresSeparateExplicitConfirmation() {
        verifyAnanya();
        AuthorizationResult request = authorizationService.requestCancellation(SESSION, "ORD-103");
        assertThat(request.status()).isEqualTo(Status.CONFIRMATION_REQUIRED);

        AuthorizationResult unconfirmed = authorizationService.confirmCancellation(
                SESSION, "ORD-103", request.confirmationId(), "cancel my order");
        assertThat(unconfirmed.status()).isEqualTo(Status.CONFIRMATION_REQUIRED);
        assertThat(authorizationService.revalidateCancellation(
                SESSION, "ORD-103", request.confirmationId()).status())
                .isEqualTo(Status.CONFIRMATION_REQUIRED);
    }

    @Test
    void confirmationForWrongOrderOrWrongPendingTokenCannotAuthorizeCancellation() {
        verifyAnanya();
        AuthorizationResult request = authorizationService.requestCancellation(SESSION, "ORD-103");

        AuthorizationResult wrongOrder = authorizationService.confirmCancellation(
                SESSION, "ORD-101", request.confirmationId(), "yes");
        assertThat(wrongOrder.status()).isNotEqualTo(Status.ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION);

        AuthorizationResult wrongToken = authorizationService.confirmCancellation(
                SESSION, "ORD-103", "stale-or-unrelated-confirmation", "yes");
        assertThat(wrongToken.status()).isEqualTo(Status.ACTION_NOT_PERMITTED);
    }

    @Test
    void expiredConfirmationCannotAuthorizeCancellation() {
        verifyAnanya();
        AuthorizationResult request = authorizationService.requestCancellation(SESSION, "ORD-103");
        clock.advance(Duration.ofMinutes(3));

        AuthorizationResult expired = authorizationService.confirmCancellation(
                SESSION, "ORD-103", request.confirmationId(), "yes");

        assertThat(expired.status()).isEqualTo(Status.ACTION_NOT_PERMITTED);
    }

    @Test
    void doesNotAuthorizeCancellationWhenCurrentStatusIsNotProcessing() {
        authorizationService.bindVerificationOrder(SESSION, "ORD-101");
        authorizationService.verifyCustomer(SESSION, "ORD-101", "Priya Sharma");

        AuthorizationResult result = authorizationService.requestCancellation(SESSION, "ORD-101");

        assertThat(result.status()).isEqualTo(Status.ACTION_NOT_PERMITTED);
        assertThat(result.confirmationId()).isNull();
    }

    @Test
    void authorizesConfirmedEligibleCancellationWithoutExecutingIt() {
        verifyAnanya();
        AuthorizationResult request = authorizationService.requestCancellation(SESSION, "ORD-103");
        AuthorizationResult confirmed = authorizationService.confirmCancellation(
                SESSION, "ORD-103", request.confirmationId(), "Yes, cancel it.");

        assertThat(confirmed.status()).isEqualTo(Status.ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION);
        AuthorizationResult revalidated = authorizationService.revalidateCancellation(
                SESSION, "ORD-103", request.confirmationId());
        assertThat(revalidated.status()).isEqualTo(Status.ACTION_PERMITTED_SUBJECT_TO_FINAL_REVALIDATION);
        assertThat(authorizationService.revalidateCancellation(
                SESSION, "ORD-103", request.confirmationId()).status())
                .isEqualTo(Status.CONFIRMATION_REQUIRED);
    }

    private void verifyAnanya() {
        authorizationService.bindVerificationOrder(SESSION, "ORD-103");
        assertThat(authorizationService.verifyCustomer(SESSION, "ORD-103", "Ananya Patel").status())
                .isEqualTo(Status.CUSTOMER_VERIFIED);
    }

    private static final class InMemoryOrderService extends OrderService {
        private final Map<String, Order> orders;

        private InMemoryOrderService(Map<String, Order> orders) {
            super(null);
            this.orders = orders;
        }

        @Override
        public Order getOrder(String orderId) {
            Order order = orders.get(orderId);
            if (order == null) {
                throw new OrderNotFoundException(orderId);
            }
            return order;
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        private MutableClock(Instant initialInstant, ZoneId zone) {
            this.current = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}

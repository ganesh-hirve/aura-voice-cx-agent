package com.ganesh.aura.tool;

import com.ganesh.aura.dto.OrderToolResult;
import com.ganesh.aura.entity.Order;
import com.ganesh.aura.entity.StubOrder;
import com.ganesh.aura.service.AuthorizationService;
import com.ganesh.aura.service.OrderNotFoundException;
import com.ganesh.aura.service.OrderService;
import com.ganesh.aura.service.PolicyService;
import com.ganesh.aura.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.ganesh.aura.dto.OrderToolResult.Code;
import static org.assertj.core.api.Assertions.assertThat;

class OrderToolTests {

    private static final String SESSION = "voice-session-1";

    private MutableClock clock;
    private OrderTool orderTool;
    private Order processingOrder;
    private Order outForDeliveryOrder;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-10T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        processingOrder = new StubOrder("ORD-103", "Ananya Patel", "Processing",
                "Green Tea Face Wash + Toner", new BigDecimal("850"));
        outForDeliveryOrder = new StubOrder("ORD-101", "Priya Sharma", "Out for Delivery");
        OrderService orderService = new InMemoryOrderService(Map.of(
                "ORD-103", processingOrder,
                "ORD-101", outForDeliveryOrder));
        AuthorizationService authorizationService = new AuthorizationService(
                orderService, new PolicyService(clock), new SessionManager(), clock);
        orderTool = new OrderTool(orderService, authorizationService);
    }

    @Test
    void unverifiedLookupReturnsNoProtectedDetails() {
        OrderToolResult result = orderTool.lookupOrder(
                new OrderTool.LookupOrderInput(SESSION, "ORD-103"));

        assertThat(result.code()).isEqualTo(Code.VERIFICATION_REQUIRED);
        assertThat(result.order()).isNull();
        assertThat(result.message()).doesNotContain("Ananya Patel", "Green Tea", "850");
    }

    @Test
    void verifiedLookupReturnsTypedOrderDetails() {
        orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "order 103"));
        assertThat(orderTool.verifyOrderCustomer(new OrderTool.VerifyOrderCustomerInput(
                SESSION, "103", "Ananya Patel")).code()).isEqualTo(Code.CUSTOMER_VERIFIED);

        OrderToolResult result = orderTool.lookupOrder(
                new OrderTool.LookupOrderInput(SESSION, "ORD-103"));

        assertThat(result.code()).isEqualTo(Code.ORDER_FOUND);
        assertThat(result.order()).isNotNull();
        assertThat(result.order().orderId()).isEqualTo("ORD-103");
        assertThat(result.order().customerName()).isEqualTo("Ananya Patel");
        assertThat(result.order().product()).isEqualTo("Green Tea Face Wash + Toner");
        assertThat(result.order().amount()).isEqualByComparingTo("850");
    }

    @Test
    void incorrectNameDoesNotReturnProtectedDataOrPartialMatchInformation() {
        orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "ORD-103"));
        OrderToolResult verification = orderTool.verifyOrderCustomer(
                new OrderTool.VerifyOrderCustomerInput(SESSION, "ORD-103", "Ananya Patil"));

        assertThat(verification.code()).isEqualTo(Code.VERIFICATION_FAILED);
        assertThat(verification.order()).isNull();
        assertThat(verification.message()).doesNotContain("Ananya", "Patel", "match", "close");

        OrderToolResult lookup = orderTool.lookupOrder(
                new OrderTool.LookupOrderInput(SESSION, "ORD-103"));
        assertThat(lookup.code()).isEqualTo(Code.VERIFICATION_FAILED);
        assertThat(lookup.order()).isNull();
    }

    @Test
    void unknownOrderReturnsSafeNotFoundResult() {
        OrderToolResult result = orderTool.lookupOrder(
                new OrderTool.LookupOrderInput(SESSION, "ORD-999"));

        assertThat(result.code()).isEqualTo(Code.ORDER_NOT_FOUND);
        assertThat(result.order()).isNull();
        assertThat(result.message()).doesNotContain("Ananya Patel", "Priya Sharma");
        assertThat(orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "999")).code())
                .isEqualTo(Code.ORDER_NOT_FOUND);
    }

    @Test
    void cancellationRequiresVerificationAndAffirmationAndCannotBeReused() {
        assertThat(orderTool.requestCancellation(
                new OrderTool.RequestCancellationInput(SESSION, "ORD-103")).code())
                .isEqualTo(Code.VERIFICATION_REQUIRED);

        orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "ORD-103"));
        orderTool.verifyOrderCustomer(new OrderTool.VerifyOrderCustomerInput(
                SESSION, "ORD-103", "Ananya Patel"));
        OrderToolResult request = orderTool.requestCancellation(
                new OrderTool.RequestCancellationInput(SESSION, "ORD-103"));
        assertThat(request.code()).isEqualTo(Code.CONFIRMATION_REQUIRED);
        assertThat(request.confirmationId()).isNotBlank();

        OrderToolResult unconfirmed = orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-103", request.confirmationId(), "cancel my order"));
        assertThat(unconfirmed.code()).isEqualTo(Code.CONFIRMATION_REQUIRED);

        OrderToolResult authorized = orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-103", request.confirmationId(), "yes, cancel it"));
        assertThat(authorized.code()).isEqualTo(Code.AUTHORIZED_TO_PROCEED);

        OrderToolResult reused = orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-103", request.confirmationId(), "yes"));
        assertThat(reused.code()).isEqualTo(Code.ACTION_NOT_PERMITTED);
    }

    @Test
    void wrongOrderAndExpiredConfirmationsAreRejected() {
        orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "ORD-103"));
        orderTool.verifyOrderCustomer(new OrderTool.VerifyOrderCustomerInput(
                SESSION, "ORD-103", "Ananya Patel"));
        OrderToolResult request = orderTool.requestCancellation(
                new OrderTool.RequestCancellationInput(SESSION, "ORD-103"));

        OrderToolResult wrongOrder = orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-101", request.confirmationId(), "yes"));
        assertThat(wrongOrder.code()).isNotEqualTo(Code.AUTHORIZED_TO_PROCEED);

        OrderToolResult expiredRequest = orderTool.requestCancellation(
                new OrderTool.RequestCancellationInput(SESSION, "ORD-103"));
        clock.advance(Duration.ofMinutes(3));
        OrderToolResult expired = orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-103", expiredRequest.confirmationId(), "yes"));
        assertThat(expired.code()).isEqualTo(Code.ACTION_NOT_PERMITTED);
    }

    @Test
    void toolOperationsDoNotMutateOrderState() {
        String initialProcessingStatus = processingOrder.getStatus();
        String initialDeliveryStatus = outForDeliveryOrder.getStatus();

        orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "ORD-103"));
        orderTool.verifyOrderCustomer(new OrderTool.VerifyOrderCustomerInput(
                SESSION, "ORD-103", "incorrect name"));
        orderTool.requestCancellation(new OrderTool.RequestCancellationInput(SESSION, "ORD-103"));

        assertThat(processingOrder.getStatus()).isEqualTo(initialProcessingStatus);
        assertThat(outForDeliveryOrder.getStatus()).isEqualTo(initialDeliveryStatus);
    }

    @Test
    void malformedInputsAreRejectedWithoutCallingProtectedOperations() {
        assertThat(orderTool.lookupOrder(new OrderTool.LookupOrderInput(" ", "ORD-103")).code())
                .isEqualTo(Code.INVALID_INPUT);
        assertThat(orderTool.lookupOrder(new OrderTool.LookupOrderInput(SESSION, "ORD-101 extra")).code())
                .isEqualTo(Code.INVALID_INPUT);
        assertThat(orderTool.verifyOrderCustomer(
                new OrderTool.VerifyOrderCustomerInput(SESSION, "bad/order", "Ananya Patel")).code())
                .isEqualTo(Code.INVALID_INPUT);
        assertThat(orderTool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                SESSION, "ORD-103", "bad-token", "yes")).code()).isEqualTo(Code.INVALID_INPUT);
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

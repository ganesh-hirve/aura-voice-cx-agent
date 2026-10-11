package com.ganesh.aura.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PolicyService {

    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499");
    private static final BigDecimal SHIPPING_FEE = new BigDecimal("50");
    private static final BigDecimal COD_LIMIT = new BigDecimal("2500");
    private static final Duration RETURN_WINDOW = Duration.ofDays(7);
    private static final Duration DAMAGE_REPORT_WINDOW = Duration.ofHours(48);

    private final Clock clock;

    public PolicyService(Clock clock) {
        this.clock = clock;
    }

    public ShippingResult evaluateShipping(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.signum() < 0) {
            return new ShippingResult(Decision.NEEDS_INFORMATION, null,
                    "A valid order amount is required to determine the shipping fee.");
        }
        int thresholdComparison = orderAmount.compareTo(FREE_SHIPPING_THRESHOLD);
        if (thresholdComparison == 0) {
            return new ShippingResult(Decision.UNRESOLVED_POLICY, null,
                    "The shipping policy does not specify the fee for an order of exactly ₹499.");
        }
        if (thresholdComparison > 0) {
            return new ShippingResult(Decision.ELIGIBLE, BigDecimal.ZERO,
                    "Orders above ₹499 qualify for free shipping.");
        }
        return new ShippingResult(Decision.ELIGIBLE, SHIPPING_FEE,
                "Orders below ₹499 have a ₹50 shipping fee.");
    }

    public DeliveryEstimate standardDeliveryEstimate() {
        return new DeliveryEstimate(3, 5, "business days");
    }

    public EligibilityResult evaluateReturn(
            LocalDateTime deliveredAt,
            Boolean unopened,
            Boolean unused,
            Boolean originalPackaging) {
        if (deliveredAt == null || unopened == null || unused == null || originalPackaging == null) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "Delivery time and whether the product is unopened, unused, and in original packaging are required.");
        }

        Duration sinceDelivery = Duration.between(deliveredAt, LocalDateTime.now(clock));
        if (sinceDelivery.isNegative()) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "The delivery time is in the future; return eligibility cannot be determined.");
        }
        if (sinceDelivery.compareTo(RETURN_WINDOW) > 0) {
            return new EligibilityResult(Decision.NOT_ELIGIBLE,
                    "Returns are accepted only within 7 days of delivery.");
        }
        if (!unopened || !unused || !originalPackaging) {
            return new EligibilityResult(Decision.NOT_ELIGIBLE,
                    "The product must be unopened, unused, and in its original packaging to qualify for a return.");
        }
        return new EligibilityResult(Decision.ELIGIBLE,
                "The request meets the stated return criteria. Refund amount and processing time are not specified by the policy.");
    }

    public EligibilityResult evaluateDamagedOrDefectiveProduct(
            LocalDateTime deliveredAt,
            LocalDateTime reportedAt,
            Boolean photosProvided) {
        if (deliveredAt == null || reportedAt == null || photosProvided == null) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "Delivery time, report time, and photo availability are required to evaluate a replacement request.");
        }

        Duration timeToReport = Duration.between(deliveredAt, reportedAt);
        if (timeToReport.isNegative()) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "The report time is before delivery; the report window cannot be evaluated.");
        }
        if (timeToReport.compareTo(DAMAGE_REPORT_WINDOW) > 0) {
            return new EligibilityResult(Decision.NOT_ELIGIBLE,
                    "Damaged or defective products must be reported within 48 hours of delivery.");
        }
        if (!photosProvided) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "Photos are required before a replacement request can be evaluated.");
        }
        return new EligibilityResult(Decision.REQUIREMENTS_MET,
                "The request meets the stated reporting and photo requirements; replacement approval is not guaranteed.");
    }

    public EligibilityResult evaluateCancellation(String orderStatus) {
        if (orderStatus == null || orderStatus.isBlank()) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "The current order status is required to evaluate cancellation eligibility.");
        }
        if ("Processing".equalsIgnoreCase(orderStatus.trim())) {
            return new EligibilityResult(Decision.ELIGIBLE,
                    "Orders with Processing status are eligible for cancellation. This evaluates eligibility only and does not cancel the order.");
        }
        if ("Shipped".equalsIgnoreCase(orderStatus.trim())
                || "Out for Delivery".equalsIgnoreCase(orderStatus.trim())) {
            return new EligibilityResult(Decision.NOT_ELIGIBLE,
                    "Orders that are Shipped or Out for Delivery cannot be cancelled.");
        }
        return new EligibilityResult(Decision.NOT_ELIGIBLE,
                "Only orders with Processing status are eligible for cancellation.");
    }

    public EligibilityResult evaluateCashOnDelivery(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.signum() < 0) {
            return new EligibilityResult(Decision.NEEDS_INFORMATION,
                    "A valid order amount is required to determine cash-on-delivery eligibility.");
        }
        if (orderAmount.compareTo(COD_LIMIT) <= 0) {
            return new EligibilityResult(Decision.ELIGIBLE,
                    "Cash on delivery is available for orders up to ₹2,500.");
        }
        return new EligibilityResult(Decision.NOT_ELIGIBLE,
                "Cash on delivery is available only for orders up to ₹2,500.");
    }

    public enum Decision {
        ELIGIBLE,
        NOT_ELIGIBLE,
        NEEDS_INFORMATION,
        UNRESOLVED_POLICY,
        REQUIREMENTS_MET
    }

    public record ShippingResult(Decision decision, BigDecimal shippingFee, String message) {
    }

    public record DeliveryEstimate(int minimum, int maximum, String unit) {
    }

    public record EligibilityResult(Decision decision, String message) {
    }
}

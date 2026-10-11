package com.ganesh.aura.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(Clock.fixed(
                Instant.parse("2026-10-10T06:30:00Z"), ZONE));
    }

    @Test
    void evaluatesShippingBelowAboveAndAtUnspecifiedThreshold() {
        var below = policyService.evaluateShipping(new BigDecimal("498.99"));
        assertThat(below.decision()).isEqualTo(PolicyService.Decision.ELIGIBLE);
        assertThat(below.shippingFee()).isEqualByComparingTo("50");

        var boundary = policyService.evaluateShipping(new BigDecimal("499"));
        assertThat(boundary.decision()).isEqualTo(PolicyService.Decision.UNRESOLVED_POLICY);
        assertThat(boundary.shippingFee()).isNull();

        var above = policyService.evaluateShipping(new BigDecimal("499.01"));
        assertThat(above.decision()).isEqualTo(PolicyService.Decision.ELIGIBLE);
        assertThat(above.shippingFee()).isEqualByComparingTo("0");

        assertThat(policyService.evaluateShipping(null).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
    }

    @Test
    void returnsRequireAllConditionsAndAreAllowedThroughSevenDays() {
        var withinWindow = policyService.evaluateReturn(NOW.minusDays(7), true, true, true);
        assertThat(withinWindow.decision()).isEqualTo(PolicyService.Decision.ELIGIBLE);
        assertThat(withinWindow.message()).doesNotContain("refund amount");

        assertThat(policyService.evaluateReturn(NOW.minusDays(7).minusNanos(1), true, true, true).decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateReturn(NOW.minusDays(1), false, true, true).decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateReturn(NOW.minusDays(1), true, null, true).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
    }

    @Test
    void damagedProductRequiresTimelyReportAndPhotosWithoutGuaranteeingApproval() {
        var timely = policyService.evaluateDamagedOrDefectiveProduct(
                NOW.minusHours(48), NOW, true);
        assertThat(timely.decision()).isEqualTo(PolicyService.Decision.REQUIREMENTS_MET);
        assertThat(timely.message()).contains("not guaranteed");

        assertThat(policyService.evaluateDamagedOrDefectiveProduct(
                NOW.minusHours(48).minusNanos(1), NOW, true).decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateDamagedOrDefectiveProduct(
                NOW.minusHours(1), NOW, false).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
        assertThat(policyService.evaluateDamagedOrDefectiveProduct(
                null, NOW, true).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
    }

    @Test
    void cancellationEvaluationIsReadOnlyAndOnlyProcessingIsEligible() {
        assertThat(policyService.evaluateCancellation(" processing ").decision())
                .isEqualTo(PolicyService.Decision.ELIGIBLE);
        assertThat(policyService.evaluateCancellation("Shipped").decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateCancellation("Out for Delivery").decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateCancellation("Delivered").decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateCancellation(null).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
    }

    @Test
    void cashOnDeliveryAllowsUpToAndIncluding2500() {
        assertThat(policyService.evaluateCashOnDelivery(new BigDecimal("2500")).decision())
                .isEqualTo(PolicyService.Decision.ELIGIBLE);
        assertThat(policyService.evaluateCashOnDelivery(new BigDecimal("2500.01")).decision())
                .isEqualTo(PolicyService.Decision.NOT_ELIGIBLE);
        assertThat(policyService.evaluateCashOnDelivery(null).decision())
                .isEqualTo(PolicyService.Decision.NEEDS_INFORMATION);
    }

    @Test
    void providesStandardBusinessDayDeliveryEstimate() {
        assertThat(policyService.standardDeliveryEstimate())
                .isEqualTo(new PolicyService.DeliveryEstimate(3, 5, "business days"));
    }
}

package com.ganesh.aura.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIdNormalizerTests {

    @Test
    void normalizesOnlyRecognizedOrderIdForms() {
        assertThat(OrderIdNormalizer.normalize("101")).contains("ORD-101");
        assertThat(OrderIdNormalizer.normalize("order 101")).contains("ORD-101");
        assertThat(OrderIdNormalizer.normalize("ORD-101")).contains("ORD-101");
        assertThat(OrderIdNormalizer.normalize("ORD-102")).contains("ORD-102");
    }

    @Test
    void rejectsMalformedAmbiguousAndUnsupportedForms() {
        assertThat(OrderIdNormalizer.normalize(null)).isEmpty();
        assertThat(OrderIdNormalizer.normalize(" ")).isEmpty();
        assertThat(OrderIdNormalizer.normalize("order" )).isEmpty();
        assertThat(OrderIdNormalizer.normalize("order one oh one")).isEmpty();
        assertThat(OrderIdNormalizer.normalize("ORD-10")).isEmpty();
        assertThat(OrderIdNormalizer.normalize("ORD-101 extra")).isEmpty();
        assertThat(OrderIdNormalizer.normalize("ORD--101")).isEmpty();
        assertThat(OrderIdNormalizer.normalize("my order 101")).isEmpty();
    }

    @Test
    void unknownCanonicalIdRemainsUnknownRatherThanMappingToAnotherOrder() {
        assertThat(OrderIdNormalizer.normalize("ORD-999")).contains("ORD-999");
        assertThat(OrderIdNormalizer.normalize("999")).contains("ORD-999");
    }
}

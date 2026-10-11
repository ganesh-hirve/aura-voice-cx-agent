package com.ganesh.aura.tool;

import com.ganesh.aura.AuraApplication;
import com.ganesh.aura.dto.OrderToolResult;
import com.ganesh.aura.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static com.ganesh.aura.dto.OrderToolResult.Code;
import static org.assertj.core.api.Assertions.assertThat;

class OrderToolPersistenceTests {

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = new SpringApplicationBuilder(AuraApplication.class)
                .run("--spring.main.web-application-type=none");
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void toolOperationsLeaveThePersistedOrderUnchanged() {
        OrderTool tool = context.getBean(OrderTool.class);
        OrderRepository repository = context.getBean(OrderRepository.class);
        String originalStatus = repository.findByOrderId("ORD-103").orElseThrow().getStatus();
        String sessionId = "persisted-order-tool-test";

        assertThat(tool.lookupOrder(new OrderTool.LookupOrderInput(sessionId, "ORD-103")).code())
                .isEqualTo(Code.VERIFICATION_REQUIRED);
        assertThat(tool.verifyOrderCustomer(new OrderTool.VerifyOrderCustomerInput(
                sessionId, "ORD-103", "Ananya Patel")).code())
                .isEqualTo(Code.CUSTOMER_VERIFIED);
        OrderToolResult lookup = tool.lookupOrder(new OrderTool.LookupOrderInput(sessionId, "ORD-103"));
        assertThat(lookup.code()).isEqualTo(Code.ORDER_FOUND);

        OrderToolResult cancellation = tool.requestCancellation(
                new OrderTool.RequestCancellationInput(sessionId, "ORD-103"));
        assertThat(cancellation.code()).isEqualTo(Code.CONFIRMATION_REQUIRED);
        OrderToolResult confirmation = tool.confirmCancellation(new OrderTool.ConfirmCancellationInput(
                sessionId, "ORD-103", cancellation.confirmationId(), "yes, cancel it"));
        assertThat(confirmation.code()).isEqualTo(Code.AUTHORIZED_TO_PROCEED);

        assertThat(repository.findByOrderId("ORD-103").orElseThrow().getStatus()).isEqualTo(originalStatus);
    }
}

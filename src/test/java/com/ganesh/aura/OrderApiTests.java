package com.ganesh.aura;

import com.ganesh.aura.controller.HealthController;
import com.ganesh.aura.controller.OrderController;
import com.ganesh.aura.repository.OrderRepository;
import com.ganesh.aura.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OrderApiTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(), new HealthController()).build();
    }

    @Test
    void seedsTheThreeAssessmentOrdersWithoutDuplicates() {
        assertThat(orderRepository.count()).isEqualTo(3);

        var priya = orderService.getOrder("ORD-101");
        assertThat(priya.getCustomerName()).isEqualTo("Priya Sharma");
        assertThat(priya.getProduct()).isEqualTo("Vitamin C Serum (30ml)");
        assertThat(priya.getAmount()).isEqualByComparingTo("699");
        assertThat(priya.getStatus()).isEqualTo("Out for Delivery");
        assertThat(priya.getCourier()).isEqualTo("BlueDart");
        assertThat(priya.getTrackingNumber()).isEqualTo("BD-982103");
        assertThat(priya.getExpectedDelivery()).isNotNull();

        var rahul = orderService.getOrder("ORD-102");
        assertThat(rahul.getCustomerName()).isEqualTo("Rahul Verma");
        assertThat(rahul.getProduct()).isEqualTo("Hydrating Sunscreen SPF 50");
        assertThat(rahul.getAmount()).isEqualByComparingTo("499");
        assertThat(rahul.getStatus()).isEqualTo("Delivered");
        assertThat(rahul.getCourier()).isEqualTo("Delhivery");
        assertThat(rahul.getTrackingNumber()).isEqualTo("DL-441029");
        assertThat(rahul.getDeliveredAt()).isNotNull();

        var ananya = orderService.getOrder("ORD-103");
        assertThat(ananya.getCustomerName()).isEqualTo("Ananya Patel");
        assertThat(ananya.getProduct()).isEqualTo("Green Tea Face Wash + Toner");
        assertThat(ananya.getAmount()).isEqualByComparingTo("850");
        assertThat(ananya.getStatus()).isEqualTo("Processing");
        assertThat(ananya.getCreatedAt()).isNotNull();
    }

    @Test
    void directOrderEndpointNeverReturnsProtectedDetails() throws Exception {
        mockMvc.perform(get("/api/orders/ORD-101").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AUTHORIZATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value(
                        "Order details are available only after customer verification in the conversation."))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.product").doesNotExist())
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        // Same denial for known and unknown IDs avoids exposing order existence on this route.
        mockMvc.perform(get("/api/orders/ORD-999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AUTHORIZATION_REQUIRED"));
    }

    @Test
    void healthEndpointStillReturnsUp() throws Exception {
        mockMvc.perform(get("/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

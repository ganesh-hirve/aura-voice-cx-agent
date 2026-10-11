package com.ganesh.aura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ganesh.aura.ai.GeminiClientException;
import com.ganesh.aura.ai.GeminiGateway;
import com.ganesh.aura.dto.ChatRequest;
import com.ganesh.aura.entity.Order;
import com.ganesh.aura.entity.StubOrder;
import com.ganesh.aura.session.SessionManager;
import com.ganesh.aura.tool.OrderTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationServiceTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeGateway gateway;
    private ConversationService conversation;
    private AuthorizationService authorization;
    private SessionManager sessions;

    @BeforeEach
    void setup() {
        gateway = new FakeGateway(mapper);
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        OrderService orderService = new InMemoryOrderService(Map.of(
                "ORD-101", new StubOrder("ORD-101", "Priya Sharma", "Out for Delivery",
                        "Vitamin C Serum 30ml", new BigDecimal("699")),
                "ORD-102", new StubOrder("ORD-102", "Rahul Verma", "Delivered",
                        "Hydrating Sunscreen SPF 50", new BigDecimal("499")),
                "ORD-103", new StubOrder("ORD-103", "Ananya Patel", "Processing",
                        "Green Tea Face Wash + Toner", new BigDecimal("850"))));
        sessions = new SessionManager();
        PolicyService policies = new PolicyService(clock);
        authorization = new AuthorizationService(orderService, policies, sessions, clock);
        OrderTool orderTool = new OrderTool(orderService, authorization);
        conversation = new ConversationService(gateway, orderTool, authorization, sessions, mapper, 3);
    }

    @Test
    void ordinaryTextConversationReturnsAssistantText() throws Exception {
        gateway.add(textResponse("Hello! How can I help with Aura Skincare today?"));
        var response = conversation.chat(new ChatRequest("session-a", "Hello", null));
        assertThat(response.reply()).contains("How can I help");
        assertThat(response.turnId()).isNotBlank();
    }

    @Test
    void unverifiedLookupAsksForNameWithoutAnotherModelRound() throws Exception {
        gateway.add(functionResponse("lookup_order", "call-1", "{\"orderId\":\"101\"}"));
        var response = conversation.chat(new ChatRequest("session-a", "Where is order 101?", null));
        assertThat(response.reply()).contains("provide the full customer name", "verify");
        assertThat(gateway.requests).hasSize(1);
        assertThat(sessions.getOrCreate("session-a").conversationHistorySnapshot().get(0).serializedContents())
                .contains("Where is order 101", "provide the full customer name")
                .doesNotContain("Vitamin C Serum", "Priya Sharma", "BD-982103");
    }

    @Test
    void exactOrd101CompletesMockedVerificationAndAuthorizedLookupFlow() throws Exception {
        assertExactOrderVerificationAndLookup("ORD-101", "Priya Sharma", "Vitamin C Serum 30ml");
    }

    @Test
    void exactOrd102CompletesMockedVerificationAndAuthorizedLookupFlow() throws Exception {
        assertExactOrderVerificationAndLookup("ORD-102", "Rahul Verma", "Hydrating Sunscreen SPF 50");
    }

    @Test
    void unknownOrd999ReturnsNotFoundThroughMockedToolRoundTrip() throws Exception {
        gateway.add(functionResponse("lookup_order", "call-1", "{\"orderId\":\"ORD-999\"}"));

        var response = conversation.chat(new ChatRequest("session-999", "Where is ORD-999?", null));

        assertThat(response.reply()).contains("could not find an order", "check the order ID");
        assertThat(gateway.requests).hasSize(1);
        assertThat(sessions.getOrCreate("session-999").conversationHistorySnapshot().get(0)
                .serializedContents()).doesNotContain("provide the full customer name");
    }

    @Test
    void unknownOrderReturnedFromVerificationDoesNotAskForNameOrCallGeminiAgain() throws Exception {
        gateway.add(functionResponse("verify_order_customer", "call-1",
                "{\"orderId\":\"ORD-199\",\"customerName\":\"A Customer\"}"));

        var response = conversation.chat(new ChatRequest("session-199",
                "Find order with ID 199 for A Customer", null));

        assertThat(response.reply()).contains("could not find an order", "check the order ID")
                .doesNotContain("provide", "name");
        assertThat(gateway.requests).hasSize(1);
        String saved = sessions.getOrCreate("session-199").conversationHistorySnapshot().get(0)
                .serializedContents();
        assertThat(saved).contains("I could not find an order")
                .doesNotContain("provide the full customer name", "Priya Sharma", "Vitamin C Serum");
    }

    private void assertExactOrderVerificationAndLookup(String orderId, String customerName, String product)
            throws Exception {
        gateway.add(functionResponse("lookup_order", "call-lookup", "{\"orderId\":\"" + orderId + "\"}"));
        var initial = conversation.chat(new ChatRequest("session-" + orderId, "Where is " + orderId + "?", null));
        assertThat(initial.reply()).contains("provide the full customer name", "verify");
        assertThat(gateway.requests).hasSize(1);
        assertThat(sessions.getOrCreate("session-" + orderId).conversationHistorySnapshot().get(0)
                .serializedContents()).doesNotContain(product, customerName);

        gateway.add(functionResponse("verify_order_customer", "call-verify",
                "{\"orderId\":\"" + orderId + "\",\"customerName\":\"" + customerName + "\"}"));
        gateway.add(functionResponse("lookup_order", "call-authorized-lookup",
                "{\"orderId\":\"" + orderId + "\"}"));
        gateway.add(textResponse("I found your order: " + product + "."));
        var verified = conversation.chat(new ChatRequest("session-" + orderId,
                "The name on the order is " + customerName + ".", null));

        assertThat(verified.reply()).contains(product);
        String verifiedResult = mapper.writeValueAsString(gateway.requests.get(2));
        assertThat(verifiedResult).contains("CUSTOMER_VERIFIED").doesNotContain(product);
        String authorizedResult = mapper.writeValueAsString(gateway.requests.get(3));
        assertThat(authorizedResult).contains("ORDER_FOUND", product, customerName);
    }

    @Test
    void unverifiedLookupThenCustomerNameKeepsSafeOrderContextAndAllowsAuthorizedLookup() throws Exception {
        gateway.add(functionResponse("lookup_order", "call-1", "{\"orderId\":\"ORD-103\"}"));
        conversation.chat(new ChatRequest("session-a", "Where is ORD-103?", null));

        gateway.add(functionResponse("verify_order_customer", "call-2",
                "{\"orderId\":\"ORD-103\",\"customerName\":\"Ananya Patel\"}"));
        gateway.add(functionResponse("lookup_order", "call-3", "{\"orderId\":\"ORD-103\"}"));
        gateway.add(textResponse("Your order is processing."));
        var response = conversation.chat(new ChatRequest(
                "session-a", "The name on the order is Ananya Patel.", null));

        assertThat(response.reply()).contains("processing");
        String verificationRequest = mapper.writeValueAsString(gateway.requests.get(1));
        assertThat(verificationRequest).contains("ORD-103", "provide the full customer name", "Ananya Patel");
        assertThat(verificationRequest).doesNotContain("Green Tea Face Wash", "850", "trackingNumber");
        String postVerificationRequest = mapper.writeValueAsString(gateway.requests.get(2));
        assertThat(postVerificationRequest).contains("CUSTOMER_VERIFIED");
        assertThat(postVerificationRequest).doesNotContain("Green Tea Face Wash", "850");
        String authorizedLookupRequest = mapper.writeValueAsString(gateway.requests.get(3));
        assertThat(authorizedLookupRequest).contains("ORDER_FOUND", "Green Tea Face Wash + Toner");
        assertThat(gateway.requests).hasSize(4);
    }

    @Test
    void incorrectNameCannotExposeDetailsAfterSafeContextReplay() throws Exception {
        beginUnverifiedAnanyaLookup();
        gateway.add(functionResponse("verify_order_customer", "call-2",
                "{\"orderId\":\"ORD-103\",\"customerName\":\"Ananya Patil\"}"));

        var response = conversation.chat(new ChatRequest("session-a", "The name on the order is Ananya Patil.", null));

        assertThat(response.reply()).contains("couldn't verify", "try again");
        assertThat(gateway.requests).hasSize(2);
        assertThat(authorization.authorizeProtectedAction("session-a", "ORD-103").status())
                .isEqualTo(com.ganesh.aura.dto.AuthorizationResult.Status.VERIFICATION_FAILED);
    }

    @Test
    void verificationCannotSwitchAwayFromBackendBoundOrder() throws Exception {
        beginUnverifiedAnanyaLookup();
        gateway.add(functionResponse("verify_order_customer", "call-2",
                "{\"orderId\":\"ORD-101\",\"customerName\":\"Ananya Patel\"}"));
        gateway.add(textResponse("Please verify the order that was requested."));

        conversation.chat(new ChatRequest("session-a", "The name on the order is Ananya Patel.", null));

        String toolResult = mapper.writeValueAsString(gateway.requests.get(2));
        assertThat(toolResult).contains("VERIFICATION_REQUIRED");
        assertThat(authorization.isOrderBoundToSession("session-a", "ORD-103")).isTrue();
        assertThat(authorization.isOrderBoundToSession("session-a", "ORD-101")).isFalse();
        assertThat(toolResult).doesNotContain("Priya Sharma", "Vitamin C Serum", "BD-982103");
    }

    @Test
    void modelCannotInventNameAbsentFromRawCustomerMessage() throws Exception {
        gateway.add(functionResponse("verify_order_customer", "call-1",
                "{\"orderId\":\"ORD-101\",\"customerName\":\"Priya Sharma\"}"));
        gateway.add(textResponse("Please provide the name associated with your order."));
        conversation.chat(new ChatRequest("session-a", "Look up ORD-101", null));
        String toolResult = mapper.writeValueAsString(gateway.requests.get(1));
        assertThat(toolResult).contains("INVALID_ARGUMENTS");
        assertThat(toolResult).doesNotContain("Vitamin C Serum", "BD-982103");
    }

    @Test
    void sessionHistoryIsNotSentToAnotherSession() throws Exception {
        gateway.add(textResponse("Hello from the first session."));
        gateway.add(textResponse("Hello from the second session."));
        conversation.chat(new ChatRequest("session-a", "First message", null));
        conversation.chat(new ChatRequest("session-b", "Second message", null));
        assertThat(mapper.writeValueAsString(gateway.requests.get(1))).doesNotContain("First message");
    }

    @Test
    void unknownAndMalformedFunctionsReturnSafeFunctionErrors() throws Exception {
        gateway.add(functionResponse("erase_database", "call-1", "{}"));
        gateway.add(textResponse("That operation is unavailable."));
        assertThat(conversation.chat(new ChatRequest("session-a", "help", null)).reply()).isNotBlank();
        assertThat(mapper.writeValueAsString(gateway.requests.get(1))).contains("UNKNOWN_FUNCTION");

        gateway.add(functionResponse("lookup_order", "call-2", "{\"orderId\":42}"));
        gateway.add(textResponse("Please provide a valid order ID."));
        conversation.chat(new ChatRequest("session-b", "lookup", null));
        assertThat(mapper.writeValueAsString(gateway.requests.get(3))).contains("INVALID_ARGUMENTS");
    }

    @Test
    void toolCallingStopsAtConfiguredIterationLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            gateway.add(functionResponse("lookup_order", "call-" + i, "{\"orderId\":\"ORD-101\"}"));
        }
        var response = conversation.chat(new ChatRequest("session-a", "lookup", null));
        assertThat(response.reply()).contains("try again");
        assertThat(gateway.requests).hasSize(4);
    }

    @Test
    void timeoutBeforeNextTurnLeavesPriorSafeContextAndVerificationBindingIntact() throws Exception {
        beginUnverifiedAnanyaLookup();
        gateway.failOnCall(2, new GeminiClientException(GeminiClientException.Code.TIMEOUT));

        assertThatThrownBy(() -> conversation.chat(new ChatRequest(
                "session-a", "The name on the order is Ananya Patel.", null)))
                .isInstanceOf(GeminiClientException.class);

        assertThat(sessions.getOrCreate("session-a").conversationHistorySnapshot()).hasSize(1);
        assertThat(authorization.isOrderBoundToSession("session-a", "ORD-103")).isTrue();
        String timedOutRequest = mapper.writeValueAsString(gateway.requests.get(1));
        assertThat(timedOutRequest).contains("ORD-103", "provide the full customer name");
        assertThat(timedOutRequest).doesNotContain("Green Tea Face Wash", "850", "trackingNumber");
    }

    @Test
    void timeoutAfterVerificationKeepsOnlySafePartialTurnAndBackendVerification() throws Exception {
        beginUnverifiedAnanyaLookup();
        gateway.add(functionResponse("verify_order_customer", "call-2",
                "{\"orderId\":\"ORD-103\",\"customerName\":\"Ananya Patel\"}"));
        gateway.failOnCall(3, new GeminiClientException(GeminiClientException.Code.TIMEOUT));

        assertThatThrownBy(() -> conversation.chat(new ChatRequest(
                "session-a", "The name on the order is Ananya Patel.", null)))
                .isInstanceOf(GeminiClientException.class);

        assertThat(authorization.authorizeProtectedAction("session-a", "ORD-103").status())
                .isEqualTo(com.ganesh.aura.dto.AuthorizationResult.Status.CUSTOMER_VERIFIED);
        var history = sessions.getOrCreate("session-a").conversationHistorySnapshot();
        assertThat(history).hasSize(2);
        assertThat(history.get(1).protectedOrderIds()).isEmpty();
        assertThat(history.get(1).serializedContents()).contains("CUSTOMER_VERIFIED", "Ananya Patel")
                .doesNotContain("Green Tea Face Wash", "850", "trackingNumber");
    }

    private void beginUnverifiedAnanyaLookup() throws Exception {
        gateway.add(functionResponse("lookup_order", "call-1", "{\"orderId\":\"ORD-103\"}"));
        conversation.chat(new ChatRequest("session-a", "Where is ORD-103?", null));
    }

    private JsonNode textResponse(String text) throws Exception {
        return mapper.readTree("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":"
                + mapper.writeValueAsString(text) + "}]}}]}");
    }

    private JsonNode functionResponse(String name, String id, String args) throws Exception {
        var root = mapper.createObjectNode();
        var parts = root.putArray("candidates").addObject().putObject("content").putArray("parts");
        var call = parts.addObject().putObject("functionCall");
        call.put("name", name);
        call.put("id", id);
        call.set("args", mapper.readTree(args));
        return root;
    }

    private static final class FakeGateway implements GeminiGateway {
        private final ObjectMapper mapper;
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private final List<ArrayNode> requests = new java.util.ArrayList<>();
        private int failOnCall = -1;
        private RuntimeException failure;

        private FakeGateway(ObjectMapper mapper) { this.mapper = mapper; }
        private void add(JsonNode response) { responses.add(response); }
        private void failOnCall(int callNumber, RuntimeException failure) {
            this.failOnCall = callNumber;
            this.failure = failure;
        }

        @Override
        public JsonNode generateContent(String systemInstruction, ArrayNode contents, ArrayNode tools) {
            requests.add(contents.deepCopy());
            if (requests.size() == failOnCall) throw failure;
            JsonNode response = responses.poll();
            if (response == null) throw new AssertionError("No mocked Gemini response was queued");
            return response;
        }
    }

    private static final class InMemoryOrderService extends OrderService {
        private final Map<String, Order> orders;
        private InMemoryOrderService(Map<String, Order> orders) { super(null); this.orders = orders; }
        @Override public Order getOrder(String orderId) {
            Order order = orders.get(orderId);
            if (order == null) throw new OrderNotFoundException(orderId);
            return order;
        }
    }
}

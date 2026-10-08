package com.ganesh.aura.session;
import java.util.List;
/** Data template for state associated with one conversation session. */
public class SessionState {
    private String sessionId;
    private List<String> conversationHistory;
    private String language;
    private String verifiedCustomer;
    private String verifiedOrderId;
    private String pendingConfirmation;
    private String latestTurnId;
    private List<String> cancelledOrders;
    private String transcript;
}

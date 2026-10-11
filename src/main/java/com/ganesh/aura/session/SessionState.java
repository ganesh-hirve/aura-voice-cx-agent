package com.ganesh.aura.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Small mutable state holder; all access is synchronized per session. */
public class SessionState {

    private final String sessionId;
    private List<ConversationTurn> conversationHistory = new ArrayList<>();
    private String language;
    private String verifiedCustomer;
    private String verifiedOrderId;
    private Instant verificationExpiresAt;
    private String failedVerificationOrderId;
    private Instant failedVerificationExpiresAt;
    private PendingVerification pendingVerification;
    private PendingConfirmation pendingConfirmation;
    private String latestTurnId;
    private List<String> cancelledOrders;
    private String transcript;

    SessionState(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public synchronized List<ConversationTurn> conversationHistorySnapshot() {
        return List.copyOf(conversationHistory);
    }

    public synchronized void saveConversationTurn(
            String turnId, String serializedContents, Set<String> protectedOrderIds) {
        conversationHistory.removeIf(turn -> turn.turnId().equals(turnId));
        conversationHistory.add(new ConversationTurn(turnId, serializedContents,
                Set.copyOf(new HashSet<>(protectedOrderIds))));
        while (conversationHistory.size() > 12) {
            conversationHistory.removeFirst();
        }
        latestTurnId = turnId;
    }

    public synchronized void markOrderVerified(String orderId, Instant expiresAt) {
        verifiedOrderId = orderId;
        verificationExpiresAt = expiresAt;
        pendingVerification = null;
        failedVerificationOrderId = null;
        failedVerificationExpiresAt = null;
        pendingConfirmation = null;
    }

    public synchronized void markVerificationFailed(String orderId, Instant expiresAt) {
        failedVerificationOrderId = orderId;
        failedVerificationExpiresAt = expiresAt;
    }

    public synchronized boolean isOrderVerified(String orderId, Instant now) {
        expireVerificationIfNeeded(now);
        return verifiedOrderId != null && verifiedOrderId.equals(orderId);
    }

    public synchronized boolean hasRecentVerificationFailure(String orderId, Instant now) {
        if (failedVerificationExpiresAt != null && !now.isBefore(failedVerificationExpiresAt)) {
            failedVerificationOrderId = null;
            failedVerificationExpiresAt = null;
        }
        return failedVerificationOrderId != null && failedVerificationOrderId.equals(orderId);
    }

    public synchronized void bindPendingVerification(String orderId, Instant expiresAt) {
        pendingVerification = new PendingVerification(Objects.requireNonNull(orderId),
                Objects.requireNonNull(expiresAt));
    }

    public synchronized boolean isPendingVerificationFor(String orderId, Instant now) {
        expirePendingVerificationIfNeeded(now);
        return pendingVerification != null && pendingVerification.orderId().equals(orderId);
    }

    public synchronized void clearPendingVerification() {
        pendingVerification = null;
    }

    public synchronized void invalidateOrderVerification(String orderId) {
        if (Objects.equals(verifiedOrderId, orderId)) {
            verifiedOrderId = null;
            verificationExpiresAt = null;
        }
        if (pendingConfirmation != null && pendingConfirmation.orderId().equals(orderId)) {
            pendingConfirmation = null;
        }
    }

    public synchronized void setPendingConfirmation(PendingConfirmation confirmation) {
        pendingConfirmation = Objects.requireNonNull(confirmation);
    }

    public synchronized Optional<PendingConfirmation> getPendingConfirmation(Instant now) {
        expireVerificationIfNeeded(now);
        if (pendingConfirmation != null && !now.isBefore(pendingConfirmation.expiresAt())) {
            pendingConfirmation = null;
        }
        return Optional.ofNullable(pendingConfirmation);
    }

    public synchronized boolean markConfirmationApproved(
            String orderId, String confirmationId, Instant now) {
        Optional<PendingConfirmation> pending = getPendingConfirmation(now);
        if (pending.isEmpty()) {
            return false;
        }
        PendingConfirmation existing = pending.get();
        if (existing.action() != PendingAction.CANCEL_ORDER
                || !existing.orderId().equals(orderId)
                || !existing.confirmationId().equals(confirmationId)) {
            return false;
        }
        pendingConfirmation = new PendingConfirmation(existing.confirmationId(), existing.action(),
                existing.orderId(), existing.expiresAt(), true);
        return true;
    }

    public synchronized boolean isApprovedConfirmation(
            String orderId, String confirmationId, Instant now) {
        return getPendingConfirmation(now)
                .filter(pending -> pending.approved()
                        && pending.action() == PendingAction.CANCEL_ORDER
                        && pending.orderId().equals(orderId)
                        && pending.confirmationId().equals(confirmationId))
                .isPresent();
    }

    public synchronized void clearPendingConfirmation() {
        pendingConfirmation = null;
    }

    private void expireVerificationIfNeeded(Instant now) {
        if (verificationExpiresAt != null && !now.isBefore(verificationExpiresAt)) {
            String expiredOrderId = verifiedOrderId;
            verifiedOrderId = null;
            verificationExpiresAt = null;
            if (pendingConfirmation != null && pendingConfirmation.orderId().equals(expiredOrderId)) {
                pendingConfirmation = null;
            }
        }
    }

    private void expirePendingVerificationIfNeeded(Instant now) {
        if (pendingVerification != null && !now.isBefore(pendingVerification.expiresAt())) {
            pendingVerification = null;
        }
    }

    public enum PendingAction {
        CANCEL_ORDER
    }

    public record PendingVerification(String orderId, Instant expiresAt) {
    }

    public record PendingConfirmation(
            String confirmationId,
            PendingAction action,
            String orderId,
            Instant expiresAt,
            boolean approved) {
    }

    public record ConversationTurn(String turnId, String serializedContents, Set<String> protectedOrderIds) {
    }
}

package com.ganesh.aura.session;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe in-memory session registry; state is isolated by session ID. */
@Component
public class SessionManager {

    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        String normalizedSessionId = sessionId.trim();
        return sessions.computeIfAbsent(normalizedSessionId, SessionState::new);
    }

    public void remove(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.remove(sessionId.trim());
        }
    }
}

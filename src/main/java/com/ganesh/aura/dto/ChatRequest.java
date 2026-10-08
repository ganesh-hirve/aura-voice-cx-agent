package com.ganesh.aura.dto;

public record ChatRequest(String sessionId, String message, String turnId) {
}

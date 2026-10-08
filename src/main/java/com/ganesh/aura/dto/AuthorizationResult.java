package com.ganesh.aura.dto;

public record AuthorizationResult(boolean allowed, String status, String reason) {
}

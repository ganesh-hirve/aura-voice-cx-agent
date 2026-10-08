package com.ganesh.aura.dto;

public record ToolResult(boolean success, String status, String message, Object data) {
}

package com.ganesh.aura.controller;

import com.ganesh.aura.ai.GeminiClientException;
import com.ganesh.aura.ai.SarvamClient;
import com.ganesh.aura.ai.SarvamTtsException;
import com.ganesh.aura.dto.ApiError;
import com.ganesh.aura.dto.ChatRequest;
import com.ganesh.aura.dto.ChatResponse;
import com.ganesh.aura.dto.SpeechRequest;
import com.ganesh.aura.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final ConversationService conversationService;
    private final SarvamClient sarvamClient;
    public CallController(ConversationService conversationService, SarvamClient sarvamClient) {
        this.conversationService = conversationService;
        this.sarvamClient = sarvamClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return conversationService.chat(request);
    }

    @PostMapping(value = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@RequestBody SpeechRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("Text must not be blank.");
        }
        if (request.text().codePointCount(0, request.text().length()) > 2_500) {
            throw new IllegalArgumentException("Text must be 2500 characters or fewer.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(sarvamClient.synthesize(request.text()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "A valid JSON chat request is required."));
    }

    @ExceptionHandler(GeminiClientException.class)
    public ResponseEntity<ApiError> handleGeminiFailure(GeminiClientException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case CONFIGURATION_MISSING -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case PROVIDER_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(SarvamTtsException.class)
    public ResponseEntity<ApiError> handleSpeechFailure(SarvamTtsException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case CONFIGURATION_MISSING -> HttpStatus.SERVICE_UNAVAILABLE;
            case AUTHENTICATION_FAILED, PROVIDER_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getCode().name(), exception.getMessage()));
    }

}

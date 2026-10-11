package com.ganesh.aura.controller;

import com.ganesh.aura.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiError> getOrder(@PathVariable String orderId) {
        // This route has no trusted authentication context. Protected order data is
        // available only through OrderTool after server-side conversational verification.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("AUTHORIZATION_REQUIRED",
                        "Order details are available only after customer verification in the conversation."));
    }
}

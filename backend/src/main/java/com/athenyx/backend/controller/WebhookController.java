package com.athenyx.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestBody Map<String, Object> payload) {
        log.info("[Webhook] Received {} webhook: {}", provider, payload);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}

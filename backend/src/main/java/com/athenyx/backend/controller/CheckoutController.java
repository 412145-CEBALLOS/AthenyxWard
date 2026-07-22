package com.athenyx.backend.controller;

import com.athenyx.backend.dto.*;
import com.athenyx.backend.service.payment.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<CheckoutStatusResponse> getStatus(
            Authentication authentication,
            @PathVariable Long paymentId,
            @RequestParam(required = false) String claimToken) {
        if (claimToken != null && !claimToken.isBlank()) {
            return ResponseEntity.ok(checkoutService.getStatusByClaim(paymentId, claimToken));
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(checkoutService.getStatus(userId, paymentId));
    }

    @PostMapping("/create")
    public ResponseEntity<CreateCheckoutResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateCheckoutRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CreateCheckoutResponse response = checkoutService.createCheckout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<UserInfo> confirm(
            Authentication authentication,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        if (request.claimToken() != null && !request.claimToken().isBlank()) {
            return ResponseEntity.ok(checkoutService.confirmPaymentByClaim(request.claimToken()));
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(checkoutService.confirmPayment(userId, request));
    }

    @PostMapping("/cancel/{paymentId}")
    public ResponseEntity<Void> cancel(
            Authentication authentication,
            @PathVariable Long paymentId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        checkoutService.cancelPending(userId, paymentId);
        return ResponseEntity.noContent().build();
    }
}

package com.athenyx.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutReturnController {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleReturn(
            @RequestParam(value = "payment_id", required = false) String paymentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "external_reference", required = false) String externalRef) {

        String safePaymentId = paymentId == null ? "" : escapeForJs(paymentId);
        String safeStatus    = status    == null ? "" : escapeForJs(status);
        String safeExtRef    = externalRef == null ? "" : escapeForJs(externalRef);
        String rawPaymentId  = paymentId    == null ? "" : paymentId;
        String rawStatus     = status       == null ? "" : status;
        String rawExtRef     = externalRef  == null ? "" : externalRef;

        String frontendOrigin = extractOrigin(frontendUrl);

        String fallbackUrl = frontendUrl + "/checkout/return"
                + "?payment_id=" + URLEncoder.encode(rawPaymentId, StandardCharsets.UTF_8)
                + "&status=" + URLEncoder.encode(rawStatus, StandardCharsets.UTF_8)
                + "&external_reference=" + URLEncoder.encode(rawExtRef, StandardCharsets.UTF_8);

        String html = """
            <!DOCTYPE html>
            <html>
            <head><title>Procesando pago...</title></head>
            <body>
            <p>Procesando pago, cerrando ventana…</p>
            <script>
            (function() {
                var FRONTEND_ORIGIN = '%s';
                var FRONTEND_URL    = '%s';
                var MSG = {
                    type: 'mp-return',
                    origin: FRONTEND_ORIGIN,
                    paymentId: '%s',
                    status: '%s',
                    externalRef: '%s'
                };
                var FALLBACK = '%s';
                try {
                    if (window.opener && !window.opener.closed) {
                        window.opener.postMessage(MSG, FRONTEND_ORIGIN);
                        setTimeout(function() {
                            if (!window.closed) window.close();
                        }, 300);
                    } else {
                        window.location.href = FALLBACK;
                    }
                } catch(e) {
                    window.location.href = FALLBACK;
                }
            })();
            </script>
            </body>
            </html>
            """.formatted(
                    escapeForJs(frontendOrigin),
                    escapeForJs(frontendUrl),
                    safePaymentId,
                    safeStatus,
                    safeExtRef,
                    escapeForJs(fallbackUrl)
            );

        log.info("[CheckoutReturn] Serving postMessage HTML: paymentId={} status={} externalRef={} frontendOrigin={}",
                rawPaymentId, rawStatus, rawExtRef, frontendOrigin);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String extractOrigin(String url) {
        try {
            return new java.net.URL(url).getProtocol() + "://" + new java.net.URL(url).getHost()
                    + (new java.net.URL(url).getPort() != -1 ? ":" + new java.net.URL(url).getPort() : "");
        } catch (Exception e) {
            return "";
        }
    }

    private String escapeForJs(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("/", "\\/");
    }
}

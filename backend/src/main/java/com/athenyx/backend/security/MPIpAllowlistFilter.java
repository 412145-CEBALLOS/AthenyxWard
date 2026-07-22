package com.athenyx.backend.security;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.payment.MercadoPagoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MPIpAllowlistFilter extends OncePerRequestFilter {

    private final MercadoPagoProperties properties;
    private final ConfigService configService;

    private Set<CidrRange> allowlist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/webhooks/mercadopago")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!properties.isConfigured()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!configService.getBoolean(ConfigKey.SECURITY_WEBHOOK_IP_ALLOWLIST_ENABLED)) {
            String clientIp = request.getRemoteAddr();
            log.debug("[MPIpAllowlist] IP allowlist DISABLED for /api/webhooks/mercadopago — allowing request from {} (url secrecy auth)", clientIp);
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        log.debug("[MPIpAllowlist] Resolved client IP={} for /api/webhooks/mercadopago", clientIp);

        if (isIpAllowed(clientIp)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("[MPIpAllowlist] Rejected request from {} for MP webhook", clientIp);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Forbidden");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (configService.getBoolean(ConfigKey.SECURITY_TRUST_FORWARDED_HEADERS)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String ip = xForwardedFor.split(",")[0].trim();
                log.debug("[MPIpAllowlist] Trusting X-Forwarded-For IP: {}", ip);
                return ip;
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isIpAllowed(String ip) {
        if (allowlist == null) {
            allowlist = parseAllowlist(properties.getIpAllowlist());
        }
        if (allowlist.isEmpty()) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return allowlist.stream().anyMatch(range -> range.contains(addr));
        } catch (UnknownHostException e) {
            log.warn("[MPIpAllowlist] Could not resolve IP: {}", ip);
            return false;
        }
    }

    private Set<CidrRange> parseAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CidrRange::new)
                .collect(Collectors.toSet());
    }

    private static class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        CidrRange(String cidr) {
            String[] parts = cidr.split("/");
            String ip = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            try {
                InetAddress addr = InetAddress.getByName(ip);
                this.networkAddress = addr.getAddress();
                this.prefixLength = prefix;
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        boolean contains(InetAddress addr) {
            byte[] addrBytes = addr.getAddress();
            if (addrBytes.length != networkAddress.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != networkAddress[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < addrBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addrBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}

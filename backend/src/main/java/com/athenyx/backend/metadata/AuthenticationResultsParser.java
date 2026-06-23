package com.athenyx.backend.metadata;

import org.springframework.stereotype.Component;

@Component
public class AuthenticationResultsParser {

    public record AuthResults(AuthStatus spf, AuthStatus dkim, AuthStatus dmarc, String raw) {}

    public AuthResults parse(String authResultsHeader) {
        if (authResultsHeader == null || authResultsHeader.isBlank()) {
            return new AuthResults(AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE, null);
        }
        String raw = authResultsHeader;
        AuthStatus spf = AuthStatus.NONE;
        AuthStatus dkim = AuthStatus.NONE;
        AuthStatus dmarc = AuthStatus.NONE;

        spf = parseAuthStatus(raw, "spf");
        dkim = parseAuthStatus(raw, "dkim");
        dmarc = parseAuthStatus(raw, "dmarc");

        return new AuthResults(spf, dkim, dmarc, raw);
    }

    private AuthStatus parseAuthStatus(String raw, String protocol) {
        String prefix = protocol + "=";
        int idx = raw.toLowerCase().indexOf(prefix);
        if (idx < 0) return AuthStatus.NONE;
        int start = idx + prefix.length();
        int end = start;
        while (end < raw.length()) {
            char c = raw.charAt(end);
            if (c == ' ' || c == ';' || c == ',') break;
            end++;
        }
        String value = raw.substring(start, end).trim().toUpperCase();
        return AuthStatus.fromString(value);
    }
}

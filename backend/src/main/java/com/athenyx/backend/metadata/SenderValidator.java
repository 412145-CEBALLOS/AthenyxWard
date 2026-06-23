package com.athenyx.backend.metadata;

import com.athenyx.backend.entity.Email;
import org.springframework.stereotype.Component;

@Component
public class SenderValidator {

    public SenderValidationResult validate(ExtractedHeaders headers) {
        boolean returnPathMismatch = checkReturnPathMismatch(headers);
        boolean replyToMismatch = checkReplyToMismatch(headers);
        boolean displayMismatch = checkDisplayMismatch(headers);
        return new SenderValidationResult(returnPathMismatch, replyToMismatch, displayMismatch);
    }

    private boolean checkReturnPathMismatch(ExtractedHeaders h) {
        if (h.returnPathDomain() == null || h.returnPathDomain().isBlank()) return false;
        if (h.fromDomain() == null || h.fromDomain().isBlank()) return false;
        return !h.returnPathDomain().equalsIgnoreCase(h.fromDomain());
    }

    private boolean checkReplyToMismatch(ExtractedHeaders h) {
        if (h.replyToDomain() == null || h.replyToDomain().isBlank()) return false;
        if (h.fromDomain() == null || h.fromDomain().isBlank()) return false;
        return !h.replyToDomain().equalsIgnoreCase(h.fromDomain());
    }

    private boolean checkDisplayMismatch(ExtractedHeaders h) {
        if (h.senderName() == null || h.senderName().isBlank()) return false;
        String nameLower = h.senderName().toLowerCase();
        String domainLower = h.fromDomain().toLowerCase();
        String[] genericTerms = {"soporte", "support", "seguridad", "security", "alerta",
            "admin", "noreply", "no-reply", "notificaciones", "cuenta", "account"};
        for (String term : genericTerms) {
            if (nameLower.contains(term) && !domainLower.contains(term)) {
                String[] trustedDomains = {"google.com", "microsoft.com", "apple.com",
                    "amazon.com", "paypal.com", "spotify.com", "netflix.com"};
                for (String trusted : trustedDomains) {
                    if (domainLower.contains(trusted)) return false;
                }
                return true;
            }
        }
        return false;
    }
}

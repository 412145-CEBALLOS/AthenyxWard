package com.athenyx.backend.metadata;

import com.athenyx.backend.entity.Email;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class MetadataExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtractedHeaders extract(Email email) {
        String sender = email.getSender() != null ? email.getSender() : "";
        String fromDomain = extractDomain(sender);
        String returnPath = email.getReturnPath();
        String returnPathDomain = extractDomain(returnPath != null ? returnPath : "");
        String replyTo = email.getReplyTo();
        String replyToDomain = extractDomain(replyTo != null ? replyTo : "");
        String receivedHeadersRaw = email.getReceivedHeaders();
        List<String> receivedChain = parseReceivedChain(receivedHeadersRaw);

        MassMailingProvider detected = MassMailingProvider.detect(
            email.getXMailer(),
            email.getListUnsubscribe(),
            receivedHeadersRaw,
            email.getSubject()
        );

        return new ExtractedHeaders(
            sender,
            fromDomain,
            email.getSenderName(),
            returnPath,
            returnPathDomain,
            replyTo,
            replyToDomain,
            receivedChain,
            email.getOriginalDateHeader(),
            email.getOriginalTimezone(),
            AuthStatus.fromString(email.getSpfStatus()),
            AuthStatus.fromString(email.getDkimStatus()),
            AuthStatus.fromString(email.getDmarcStatus()),
            null,
            email.getListUnsubscribe(),
            email.getXMailer(),
            detected != MassMailingProvider.NONE,
            detected
        );
    }

    private String extractDomain(String address) {
        if (address == null || address.isBlank()) return "";
        int at = address.indexOf('@');
        if (at < 0) return "";
        int domainEnd = address.indexOf('>', at);
        String domain = address.substring(at + 1);
        if (domainEnd > at) {
            domain = address.substring(at + 1, domainEnd);
        }
        return domain.trim().toLowerCase();
    }

    private List<String> parseReceivedChain(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            if (raw.contains("|")) {
                return new ArrayList<>(List.of(raw.split("\\|")));
            }
            return List.of(raw);
        }
    }
}

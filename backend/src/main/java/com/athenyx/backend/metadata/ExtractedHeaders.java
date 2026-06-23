package com.athenyx.backend.metadata;

import java.util.List;

public record ExtractedHeaders(
    String from,
    String fromDomain,
    String senderName,
    String returnPath,
    String returnPathDomain,
    String replyTo,
    String replyToDomain,
    List<String> receivedChain,
    String originalDateHeader,
    String originalTimezone,
    AuthStatus spfStatus,
    AuthStatus dkimStatus,
    AuthStatus dmarcStatus,
    String authenticationResults,
    String listUnsubscribe,
    String xMailer,
    boolean isMassMailing,
    MassMailingProvider massMailingProvider
) {}

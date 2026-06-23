package com.athenyx.backend.heuristics;

import java.time.LocalDateTime;
import java.util.List;

public record EmailHeuristicsInput(
    String subject,
    String sender,
    String senderName,
    String content,
    String htmlContent,
    List<String> urls,
    LocalDateTime receivedAt,
    String originalDateHeader,
    String replyTo,
    String returnPath,
    String spfStatus,
    String dkimStatus,
    String dmarcStatus,
    String originalTimezone,
    String listUnsubscribe,
    String xMailer
) {}

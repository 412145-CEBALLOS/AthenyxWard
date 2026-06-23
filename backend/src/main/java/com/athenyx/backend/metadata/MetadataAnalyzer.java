package com.athenyx.backend.metadata;

import com.athenyx.backend.entity.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetadataAnalyzer {

    private final MetadataExtractor metadataExtractor;
    private final SenderValidator senderValidator;
    private final TimestampAnalyzer timestampAnalyzer;
    private final MassMailingDetector massMailingDetector;
    private final SenderTrustCalculator senderTrustCalculator;

    public MetadataAnalysisResult analyze(Email email) {
        ExtractedHeaders headers = metadataExtractor.extract(email);
        SenderValidationResult senderValidation = senderValidator.validate(headers);
        TimestampAnalysisResult timestampAnalysis = timestampAnalyzer.analyze(headers, email.getReceivedAt());
        MassMailingResult massMailingResult = massMailingDetector.detect(headers);
        SenderTrustCalculator.TrustResult trustResult = senderTrustCalculator.calculate(
            headers, senderValidation, timestampAnalysis, massMailingResult);

        List<String> anomalies = new ArrayList<>();
        if (senderValidation.returnPathMismatch()) anomalies.add("Return-Path no coincide con From");
        if (senderValidation.replyToMismatch()) anomalies.add("Reply-To no coincide con From");
        if (timestampAnalysis.futureDate()) anomalies.add("Fecha en el futuro");
        if (timestampAnalysis.timezoneAnomaly()) anomalies.add("Zona horaria anómala");
        if (massMailingResult.isMassMailing()) anomalies.add("Servicio de envío masivo: " + massMailingResult.provider().name());

        log.debug("Metadata analysis for email {}: trustScore={}, level={}, anomalies={}",
            email.getId(), trustResult.score(), trustResult.level(), anomalies.size());

        return new MetadataAnalysisResult(
            headers,
            senderValidation,
            timestampAnalysis,
            trustResult.score(),
            trustResult.level(),
            trustResult.signals(),
            anomalies
        );
    }
}

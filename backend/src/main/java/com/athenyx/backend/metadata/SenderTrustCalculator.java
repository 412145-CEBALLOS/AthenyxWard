package com.athenyx.backend.metadata;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SenderTrustCalculator {

    private static final Set<String> FREE_EMAIL_PROVIDERS = Set.of(
        "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "yahoo.es",
        "yahoo.com.mx", "yahoo.com.ar", "yahoo.com.co", "yahoo.com.br",
        "icloud.com", "mail.com", "aol.com", "protonmail.com", "proton.me",
        "zoho.com", "yandex.com", "mail.ru", "qq.com", "163.com"
    );

    private static final Set<String> TRUSTED_DOMAINS = Set.of(
        "google.com", "microsoft.com", "apple.com", "amazon.com", "paypal.com",
        "spotify.com", "netflix.com", "facebook.com", "meta.com", "instagram.com",
        "twitter.com", "x.com", "linkedin.com", "dropbox.com", "adobe.com",
        "ebanx.com", "mercadopago.com", "bancomer.com", "banorte.com",
        "santander.com", "bbva.com", "hsbc.com", "bankofamerica.com",
        "chase.com", "wellsfargo.com", "scotiabank.com"
    );

    public record TrustResult(int score, SenderTrustLevel level, List<SenderTrustSignal> signals) {}

    public TrustResult calculate(ExtractedHeaders headers, SenderValidationResult validation,
                                 TimestampAnalysisResult timestamp, MassMailingResult massMailing) {
        int score = 50;
        List<SenderTrustSignal> signals = new ArrayList<>();

        if (headers.spfStatus() == AuthStatus.PASS) {
            score += 30;
            signals.add(new SenderTrustSignal("SPF_PASS", "SPF verificado correctamente", 30));
        } else if (headers.spfStatus() == AuthStatus.FAIL) {
            score -= 20;
            signals.add(new SenderTrustSignal("SPF_FAIL", "SPF falló", -20));
        }

        if (headers.dkimStatus() == AuthStatus.PASS) {
            score += 25;
            signals.add(new SenderTrustSignal("DKIM_PASS", "DKIM verificado correctamente", 25));
        } else if (headers.dkimStatus() == AuthStatus.FAIL) {
            score -= 15;
            signals.add(new SenderTrustSignal("DKIM_FAIL", "DKIM falló", -15));
        }

        if (headers.dmarcStatus() == AuthStatus.PASS) {
            score += 25;
            signals.add(new SenderTrustSignal("DMARC_PASS", "DMARC verificado correctamente", 25));
        } else if (headers.dmarcStatus() == AuthStatus.FAIL) {
            score -= 15;
            signals.add(new SenderTrustSignal("DMARC_FAIL", "DMARC falló", -15));
        }

        if (headers.fromDomain() != null && TRUSTED_DOMAINS.contains(headers.fromDomain().toLowerCase())) {
            score += 20;
            signals.add(new SenderTrustSignal("TRUSTED_DOMAIN", "Dominio de remitente en lista de confianza", 20));
        }

        if (headers.fromDomain() != null && FREE_EMAIL_PROVIDERS.contains(headers.fromDomain().toLowerCase())) {
            score -= 20;
            signals.add(new SenderTrustSignal("FREE_EMAIL", "Cuenta de email gratuito", -20));
        }

        if (massMailing.isMassMailing()) {
            score -= 30;
            signals.add(new SenderTrustSignal("MASS_MAILING", "Servicio de envío masivo detectado: " + massMailing.provider().name(), -30));
        }

        if (validation.returnPathMismatch()) {
            score -= 25;
            signals.add(new SenderTrustSignal("RETURN_PATH_MISMATCH", "Return-Path no coincide con From", -25));
        }

        if (validation.replyToMismatch()) {
            score -= 25;
            signals.add(new SenderTrustSignal("REPLY_TO_MISMATCH", "Reply-To no coincide con From", -25));
        }

        if (timestamp.timezoneAnomaly()) {
            score -= 15;
            signals.add(new SenderTrustSignal("TIMEZONE_ANOMALY", "Zona horaria anómala en header Date", -15));
        }

        if (timestamp.futureDate()) {
            score -= 20;
            signals.add(new SenderTrustSignal("FUTURE_DATE", "Fecha del email en el futuro", -20));
        }

        int clamped = Math.max(0, Math.min(100, score));
        SenderTrustLevel level = SenderTrustLevel.fromScore(clamped);

        return new TrustResult(clamped, level, signals);
    }
}

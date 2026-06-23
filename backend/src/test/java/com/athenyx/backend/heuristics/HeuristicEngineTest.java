package com.athenyx.backend.heuristics;

import com.athenyx.backend.heuristics.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicEngineTest {

    private HeuristicEngine engine;

    @BeforeEach
    void setUp() {
        var rules = List.of(
            new SuspiciousDomainRule(),
            new UrgentLanguageRule(),
            new FakeLoginPageRule(),
            new MaliciousUrlRule(),
            new SenderImpersonationRule(),
            new SuspiciousMetadataRule(),
            new ScamLanguagePatternRule(),
            new SuspiciousAttachmentRule(),
            new RegexPatternRule(),
            new FreeEmailProviderBrandRule(),
            new DisplayNameBrandSpoofRule(),
            new SuspiciousTldRule(),
            new HtmlFormRule(),
            new ShortenedUrlRule(),
            new RiskyKeywordsRule(),
            new ReplyToMismatchRule()
        );
        var scorer = new ThreatScorer(rules);
        this.engine = new HeuristicEngine(rules, scorer);
    }

    @Test
    void safeEmail_returnsGreenLowScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Project update",
            "colleague@company.com",
            "Colleague",
            "Hi, sending you the project files as discussed.",
            "<html><body><p>Hi</p></body></html>",
            List.of("https://company.com/drive/file"),
            LocalDateTime.now(),
            "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null
        );
        HeuristicResult result = engine.run(input);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
        assertThat(result.riskPercentage()).isLessThan(40);
    }

    @Test
    void phishingEmail_returnsYellowOrRed() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: Verify your PayPal account NOW",
            "support@paypa1-secure.com",
            "PayPal Security",
            "Your account has been suspended! Verify immediately or it will be closed in 24 hours!",
            "<form action=\"http://paypal-verify.com/login\"><input name=\"password\"></form>",
            List.of(
                "http://192.168.1.1/paypal-login",
                "https://bit.ly/fake-paypal"
            ),
            LocalDateTime.now(),
            "Mon, 22 Jun 2026 10:00:00 +0000",
            "support@paypal-verify.com",
            null, null, null, null
        );
        HeuristicResult result = engine.run(input);
        assertThat(result.threatLevel()).isIn(ThreatLevel.YELLOW, ThreatLevel.RED);
        assertThat(result.riskPercentage()).isGreaterThanOrEqualTo(40);
        assertThat(result.findings().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void scamEmail_returnsRed() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "FELICIDADES! Ha GANADO la LOTERÍA",
            "prince@nigeria-gov.xyz",
            "Príncipe Nigerianiano",
            "Usted ha ganado $10,000,000 USD en la lotería nigeriana. " +
            "Transferencia bancaria disponible. Reclame su premio ahora!",
            "",
            List.of(),
            LocalDateTime.now(),
            "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null
        );
        HeuristicResult result = engine.run(input);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.RED);
        assertThat(result.riskPercentage()).isGreaterThan(50);
        assertThat(result.findings().stream()
            .anyMatch(f -> f.rule().equals("ScamLanguagePatternRule"))).isTrue();
    }

    @Test
    void emailWithOnlySafeIndicators_returnsGreen() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Meeting tomorrow",
            "boss@company.com",
            "Boss",
            "Hi team, let's meet tomorrow at 3pm to discuss the roadmap.",
            "<html><body><p>Meeting at 3pm</p></body></html>",
            List.of("https://calendar.company.com/meeting"),
            LocalDateTime.now(),
            "Mon, 22 Jun 2026 10:00:00 +0000",
            null, null, null, null, null
        );
        HeuristicResult result = engine.run(input);
        assertThat(result.threatLevel()).isEqualTo(ThreatLevel.GREEN);
    }

    @Test
    void engineReportsCorrectRuleCount() {
        assertThat(engine.ruleCount()).isEqualTo(16);
    }

    @Test
    void multipleRules_fireAndCombine_returnsYellowOrRed() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "URGENTE: Banco - Action required",
            "support@bancomer-secure.com",
            "Bancomer",
            "Su cuenta ha sido suspendida. Verificar inmediatamente! Urgente! Sin demora!",
            "<form action=\"http://bancomer-verify.com/login\"><input name=\"password\"></form>",
            List.of("http://1.2.3.4/bancomer/login"),
            LocalDateTime.now().plusDays(10),
            null,
            "support@bancomer-scam.com",
            null, null, null, null
        );
        HeuristicResult result = engine.run(input);
        assertThat(result.findings().size()).isGreaterThanOrEqualTo(3);
        assertThat(result.threatLevel()).isIn(ThreatLevel.YELLOW, ThreatLevel.RED);
    }
}

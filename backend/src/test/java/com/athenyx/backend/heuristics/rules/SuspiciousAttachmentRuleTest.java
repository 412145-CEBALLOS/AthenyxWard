package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.EmailHeuristicsInput;
import com.athenyx.backend.heuristics.HeuristicFinding;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousAttachmentRuleTest {

    private final SuspiciousAttachmentRule rule = new SuspiciousAttachmentRule();

    @Test
    void noAttachments_returnsEmpty() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Hello", "sender@example.com", "Sender",
            "Just a normal email", "", java.util.List.of(),
            null, null, null, null, null, null, null
        );
        assertThat(rule.apply(input)).isEmpty();
    }

    @Test
    void exeFile_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Invoice", "accounting@vendor.com", "Accounting",
            "Please find attached",
            "", java.util.List.of("https://vendor.com/invoice.exe"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }

    @Test
    void zipFile_mediumScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Documents", "sender@example.com", "Sender",
            "Documents attached",
            "", java.util.List.of("https://files.com/docs.zip"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(40);
    }

    @Test
    void zipWithExe_inside_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Resume", "candidate@email.com", "Candidate",
            "Please review my resume",
            "", java.util.List.of("https://storage.com/resume.zip/invoice.exe"),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(95);
    }

    @Test
    void multipleDangerousExtensions_highScore() {
        EmailHeuristicsInput input = new EmailHeuristicsInput(
            "Files", "sender@example.com", "Sender",
            "Files",
            "", java.util.List.of(
                "https://files.com/update.bat",
                "https://files.com/script.js"
            ),
            null, null, null, null, null, null, null
        );
        Optional<HeuristicFinding> result = rule.apply(input);
        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(60);
    }
}

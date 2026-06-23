package com.athenyx.backend.gmail;

import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.GmailPageTokenRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GmailServiceParseDateTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailRepository emailRepository;
    @Mock
    private GmailPageTokenRepository gmailPageTokenRepository;
    @Mock
    private EmailAnalysisRepository emailAnalysisRepository;
    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private GmailService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GmailService(userRepository, emailRepository,
                gmailPageTokenRepository, emailAnalysisRepository, tokenEncryptionService);
        setField("googleClientId", "cid");
        setField("googleClientSecret", "sec");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = GmailService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void parsesRfc1123WithGmt() {
        LocalDateTime ldt = service.parseDate("Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2008, 6, 3, 11, 5, 30));
    }

    @Test
    void parsesRfc1123WithUtcDesignator() {
        LocalDateTime ldt = service.parseDate("Tue, 3 Jun 2008 11:05:30 +0000");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2008, 6, 3, 11, 5, 30));
    }

    @Test
    void parsesNegativeOffsetWithParenAbbrev() {
        // The case the user reported: 06:00 -0600 (MDT) is 12:00 UTC.
        LocalDateTime ldt = service.parseDate("Tue, 2 Jun 2026 06:00:32 -0600 (MDT)");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 2, 12, 0, 32));
    }

    @Test
    void parsesNegativeOffsetWithParenAbbrev_second() {
        LocalDateTime ldt = service.parseDate("Sun, 31 May 2026 08:50:08 -0600 (MDT)");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 5, 31, 14, 50, 8));
    }

    @Test
    void parsesColonOffset() {
        LocalDateTime ldt = service.parseDate("Tue, 2 Jun 2026 06:00:32 -06:00");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 2, 12, 0, 32));
    }

    @Test
    void parsesPositiveOffset() {
        LocalDateTime ldt = service.parseDate("Wed, 3 Jun 2026 11:05:30 +0530");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 3, 5, 35, 30));
    }

    @Test
    void parsesUtcAsParen() {
        LocalDateTime ldt = service.parseDate("Tue, 2 Jun 2026 06:00:32 +0000 (UTC)");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 2, 6, 0, 32));
    }

    @Test
    void parsesWithoutTimezone() {
        LocalDateTime ldt = service.parseDate("Tue, 2 Jun 2026 06:00:32");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 2, 6, 0, 32));
    }

    @Test
    void parsesShortTime() {
        LocalDateTime ldt = service.parseDate("Tue, 2 Jun 2026 06:00");
        assertThat(ldt).isEqualTo(LocalDateTime.of(2026, 6, 2, 6, 0));
    }

    @Test
    void returnsNowForNullOrBlank() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime a = service.parseDate(null);
        LocalDateTime b = service.parseDate("   ");
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(a).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(b).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void returnsNowForCompletelyGarbage() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime ldt = service.parseDate("definitely not a date");
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(ldt).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}

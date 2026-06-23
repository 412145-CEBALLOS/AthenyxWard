package com.athenyx.backend.service;

import com.athenyx.backend.dto.AnalysisHistoryResponse;
import com.athenyx.backend.dto.AnalysisHistoryResponse.AnalysisHistoryItem;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisHistoryServiceTest {

    @Mock
    private EmailAnalysisRepository repository;

    private AnalysisHistoryService service;

    private User user;
    private Email email;

    @BeforeEach
    void setUp() {
        service = new AnalysisHistoryService(repository);

        user = User.builder()
            .id(1L).googleId("gid").email("u@example.com").name("U")
            .role(Role.PREMIUM).build();
        email = Email.builder()
            .id(10L).gmailId("msg-1").sender("a@b.com").senderName("A")
            .subject("S").user(user).build();
    }

    private EmailAnalysis buildAnalysis(Long id, ThreatLevel level, int pct, String explanation) {
        return EmailAnalysis.builder()
            .id(id).email(email).user(user)
            .origin(AnalysisOrigin.HEURISTIC)
            .riskLevel(level).riskPercentage(pct)
            .analyzedAt(LocalDateTime.of(2026, 6, 8, 9, 14))
            .aiExplanation(explanation)
            .build();
    }

    @Test
    void getHistory_returnsEmptyResponseWhenNoAnalyses() {
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.currentPage()).isZero();
    }

    @Test
    void getHistory_passesDefaultPageableToRepository() {
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getHistory(1L, null, null, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findHistoryByUser(eq(1L), isNull(), isNull(), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("analyzedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getHistory_passesCustomPageAndSize() {
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        service.getHistory(1L, null, null, 2, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findHistoryByUser(eq(1L), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getHistory_clampsSizeToMaxPageSize() {
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, AnalysisHistoryService.MAX_PAGE_SIZE), 0));

        service.getHistory(1L, null, null, 0, 9999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findHistoryByUser(eq(1L), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(AnalysisHistoryService.MAX_PAGE_SIZE);
    }

    @Test
    void getHistory_clampsNegativePageToZero() {
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getHistory(1L, null, null, -3, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findHistoryByUser(eq(1L), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void getHistory_passesDateFiltersUnchanged() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
        when(repository.findHistoryByUser(eq(1L), eq(from), eq(to), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getHistory(1L, from, to, 0, 20);

        verify(repository).findHistoryByUser(eq(1L), eq(from), eq(to), any(Pageable.class));
    }

    @Test
    void getHistory_mapsEntityToItem() {
        EmailAnalysis a = buildAnalysis(99L, ThreatLevel.RED, 87, "Texto completo");
        Page<EmailAnalysis> page = new PageImpl<>(
            List.of(a), PageRequest.of(0, 20), 1);
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        AnalysisHistoryItem item = response.items().get(0);
        assertThat(item.analysisId()).isEqualTo(99L);
        assertThat(item.emailId()).isEqualTo(10L);
        assertThat(item.sender()).isEqualTo("a@b.com");
        assertThat(item.subject()).isEqualTo("S");
        assertThat(item.riskPercentage()).isEqualTo(87);
        assertThat(item.riskLevel()).isEqualTo("RED");
        assertThat(item.analyzedAt()).isEqualTo(LocalDateTime.of(2026, 6, 8, 9, 14));
        assertThat(item.summary()).isEqualTo("Texto completo");
    }

    @Test
    void getHistory_truncatesSummaryToMaxLength() {
        String longText = "a".repeat(AnalysisHistoryService.SUMMARY_MAX_LENGTH + 50);
        EmailAnalysis a = buildAnalysis(1L, ThreatLevel.YELLOW, 50, longText);
        Page<EmailAnalysis> page = new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1);
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 0, 20);

        String summary = response.items().get(0).summary();
        assertThat(summary).hasSize(AnalysisHistoryService.SUMMARY_MAX_LENGTH);
        assertThat(summary).endsWith("…");
    }

    @Test
    void getSummary_nullWhenAiExplanationIsNull() {
        EmailAnalysis a = buildAnalysis(1L, ThreatLevel.GREEN, 5, null);
        Page<EmailAnalysis> page = new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1);
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 0, 20);

        assertThat(response.items().get(0).summary()).isNull();
    }

    @Test
    void getHistory_doesNotTruncateShortSummary() {
        EmailAnalysis a = buildAnalysis(1L, ThreatLevel.GREEN, 5, "Corto");
        Page<EmailAnalysis> page = new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1);
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 0, 20);

        assertThat(response.items().get(0).summary()).isEqualTo("Corto");
    }

    @Test
    void getHistory_propagatesPaginationMetadata() {
        EmailAnalysis a1 = buildAnalysis(1L, ThreatLevel.GREEN, 5, "s1");
        EmailAnalysis a2 = buildAnalysis(2L, ThreatLevel.YELLOW, 50, "s2");
        Page<EmailAnalysis> page = new PageImpl<>(
            List.of(a1, a2), PageRequest.of(1, 20), 45);
        when(repository.findHistoryByUser(eq(1L), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(page);

        AnalysisHistoryResponse response = service.getHistory(1L, null, null, 1, 20);

        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.totalItems()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items()).hasSize(2);
    }
}

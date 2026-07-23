package com.athenyx.backend.service.stats;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.AdminStatsResponse;
import com.athenyx.backend.dto.StatsPeriod;
import com.athenyx.backend.dto.UserStatsResponse;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailAnalysisRepository.OriginCount;
import com.athenyx.backend.repository.EmailAnalysisRepository.RiskLevelCount;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private EmailAnalysisRepository emailAnalysisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ConfigService configService;

    private Clock clock;
    private ObjectMapper objectMapper;
    private StatsService service;

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        objectMapper = new ObjectMapper();
        service = new StatsService(emailAnalysisRepository, userRepository, paymentRepository,
            configService, clock, objectMapper);
    }

    @Test
    void getUserStats_weekPeriod_computesKpisAndTrends() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(100L).thenReturn(80L);
        when(emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(10L).thenReturn(8L);
        when(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(45.0).thenReturn(40.0);
        when(emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(eq(1L), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findHistoryByUser(eq(1L), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(emailAnalysisRepository.findLastThreatAtByUserId(1L))
            .thenReturn(Optional.empty());

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.period()).isEqualTo("week");
        assertThat(response.kpis()).hasSize(4);

        UserStatsResponse.KpiMetric analyzed = response.kpis().get(0);
        assertThat(analyzed.label()).isEqualTo("Correos analizados");
        assertThat(analyzed.value()).isEqualTo(100.0);
        assertThat(analyzed.previousValue()).isEqualTo(80.0);
        assertThat(analyzed.trendUp()).isTrue();

        UserStatsResponse.KpiMetric threats = response.kpis().get(1);
        assertThat(threats.label()).isEqualTo("Amenazas bloqueadas");
        assertThat(threats.value()).isEqualTo(10.0);

        UserStatsResponse.KpiMetric phishingRate = response.kpis().get(2);
        assertThat(phishingRate.label()).isEqualTo("Tasa de phishing");
        assertThat(phishingRate.value()).isEqualTo(10.0);

        UserStatsResponse.KpiMetric avgRisk = response.kpis().get(3);
        assertThat(avgRisk.label()).isEqualTo("Riesgo medio");
        assertThat(avgRisk.value()).isEqualTo(45.0);
    }

    @Test
    void getUserStats_weekPeriod_usesCorrectDateRange() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStats();

        service.getUserStats(1L, StatsPeriod.WEEK);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(emailAnalysisRepository, org.mockito.Mockito.times(2))
            .countByUserIdAndAnalyzedAtBetween(eq(1L), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2026-06-08T12:00:00"));
        assertThat(toCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2026-06-15T12:00:00"));
    }

    @Test
    void getUserStats_monthPeriod_usesCorrectDateRange() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStats();

        service.getUserStats(1L, StatsPeriod.MONTH);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(emailAnalysisRepository, org.mockito.Mockito.times(2))
            .countByUserIdAndAnalyzedAtBetween(eq(1L), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2026-05-16T12:00:00"));
        assertThat(toCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2026-06-15T12:00:00"));
    }

    @Test
    void getUserStats_yearPeriod_usesCorrectDateRange() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStats();

        service.getUserStats(1L, StatsPeriod.YEAR);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(emailAnalysisRepository, org.mockito.Mockito.times(2))
            .countByUserIdAndAnalyzedAtBetween(eq(1L), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2025-06-15T12:00:00"));
        assertThat(toCaptor.getAllValues().get(0)).isEqualTo(LocalDateTime.parse("2026-06-15T12:00:00"));
    }

    @Test
    void getUserStats_riskDistribution_includesAllLevels() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStatsExceptRiskDistribution();

        RiskLevelCount green = new RiskLevelCount() {
            @Override public ThreatLevel getLevel() { return ThreatLevel.GREEN; }
            @Override public long getCount() { return 80L; }
        };
        RiskLevelCount red = new RiskLevelCount() {
            @Override public ThreatLevel getLevel() { return ThreatLevel.RED; }
            @Override public long getCount() { return 20L; }
        };
        when(emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(eq(1L), any(), any()))
            .thenReturn(List.of(green, red));

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.riskDistribution()).hasSize(3);
        assertThat(response.riskDistribution().get(0).level()).isEqualTo("GREEN");
        assertThat(response.riskDistribution().get(0).count()).isEqualTo(80);
        assertThat(response.riskDistribution().get(1).level()).isEqualTo("YELLOW");
        assertThat(response.riskDistribution().get(1).count()).isZero();
        assertThat(response.riskDistribution().get(2).level()).isEqualTo("RED");
        assertThat(response.riskDistribution().get(2).count()).isEqualTo(20);
    }

    @Test
    void getUserStats_topCategories_parsesFindingsJson() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStatsExceptFindings();

        String findings = """
            [
              {"rule":"MaliciousUrlRule","description":"url","score":50},
              {"rule":"MaliciousUrlRule","description":"url","score":50},
              {"rule":"UrgentLanguageRule","description":"urgente","score":30}
            ]
            """;
        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of(findings));

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.topCategories()).hasSize(2);
        assertThat(response.topCategories().get(0).category()).isEqualTo("URL maliciosa");
        assertThat(response.topCategories().get(0).count()).isEqualTo(2);
        assertThat(response.topCategories().get(1).category()).isEqualTo("Lenguaje de urgencia");
        assertThat(response.topCategories().get(1).count()).isEqualTo(1);
    }

    @Test
    void getUserStats_topCategories_skipsMalformedJson() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStatsExceptFindings();

        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of("not-json", "[} nonsense"));

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.topCategories()).isEmpty();
    }

    @Test
    void getUserStats_trialUsage_presentForTrialUser() {
        User user = User.builder().id(1L).role(Role.TRIAL).analysisCount(8).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(configService.getInt(ConfigKey.TRIAL_ANALYSIS_LIMIT)).thenReturn(20);
        stubEmptyUserStats();

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.trialUsage()).isNotNull();
        assertThat(response.trialUsage().used()).isEqualTo(8);
        assertThat(response.trialUsage().total()).isEqualTo(20);
    }

    @Test
    void getUserStats_trialUsage_nullForPremium() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStats();

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.trialUsage()).isNull();
    }

    @Test
    void getUserStats_recentActivity_mapsEmailSenderAndRisk() {
        User user = User.builder().id(1L).role(Role.PREMIUM).build();
        Email email = Email.builder().id(10L).sender("a@b.com").subject("S").build();
        EmailAnalysis analysis = EmailAnalysis.builder()
            .id(1L).email(email).user(user)
            .riskLevel(ThreatLevel.RED).riskPercentage(87)
            .analyzedAt(LocalDateTime.parse("2026-06-14T09:14:00"))
            .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyUserStatsExceptRecentActivity();
        when(emailAnalysisRepository.findHistoryByUser(eq(1L), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(analysis)));

        UserStatsResponse response = service.getUserStats(1L, StatsPeriod.WEEK);

        assertThat(response.recentActivity()).hasSize(1);
        UserStatsResponse.RecentItem item = response.recentActivity().get(0);
        assertThat(item.sender()).isEqualTo("a@b.com");
        assertThat(item.risk()).isEqualTo(87);
        assertThat(item.level()).isEqualTo("RED");
        assertThat(item.date()).isEqualTo("2026-06-14 09:14");
    }

    @Test
    void getAdminStats_computesKpisAndEngagement() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(1000L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(50L);
        when(userRepository.countByRoleGrouped()).thenReturn(List.of());
        when(userRepository.countByLastLoginAtAfterAndDeletedAtIsNull(any()))
            .thenReturn(100L).thenReturn(300L).thenReturn(800L);
        when(userRepository.countDailySignups(any(), any())).thenReturn(List.of());

        when(paymentRepository.countActiveSubscriptions(any()))
            .thenReturn(600L);
        when(paymentRepository.countNewActiveSubscriptions(any(), any(), any()))
            .thenReturn(30L);
        when(paymentRepository.countCanceledSubscriptions()).thenReturn(100L);
        when(paymentRepository.countCanceledSubscriptionsBetween(any(), any())).thenReturn(5L);
        when(paymentRepository.countCompletedPaymentsBetween(any(), any())).thenReturn(10L);

        when(emailAnalysisRepository.countByAnalyzedAtBetween(any(), any()))
            .thenReturn(5000L).thenReturn(4000L);
        when(emailAnalysisRepository.countThreatsByAnalyzedAtBetween(any(), any()))
            .thenReturn(250L).thenReturn(200L);
        when(emailAnalysisRepository.countRiskLevelsByDateRange(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.countOriginsByDateRange(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.countDailyThreats(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.countThreatsByHour(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.findFindingsByDateRange(any(), any(), any())).thenReturn(List.of());

        AdminStatsResponse response = service.getAdminStats(StatsPeriod.WEEK);

        assertThat(response.period()).isEqualTo("week");
        assertThat(response.kpis()).hasSize(6);
        assertThat(response.kpis().get(0).label()).isEqualTo("Usuarios totales");
        assertThat(response.kpis().get(1).label()).isEqualTo("Suscripciones activas");
        assertThat(response.kpis().get(3).label()).isEqualTo("Análisis totales");
        assertThat(response.kpis().get(4).label()).isEqualTo("Media análisis / usuario");
        assertThat(response.kpis().get(4).value()).isEqualTo(5.0);

        assertThat(response.engagement().dau()).isEqualTo(100);
        assertThat(response.engagement().wau()).isEqualTo(300);
        assertThat(response.engagement().mau()).isEqualTo(800);
    }

    @Test
    void getAdminStats_conversionRate_zeroWhenNoSignups() {
        stubAdminEmptyExceptSignups();
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(paymentRepository.countCompletedPaymentsBetween(any(), any())).thenReturn(5L);

        AdminStatsResponse response = service.getAdminStats(StatsPeriod.WEEK);

        assertThat(response.conversionRate().value()).isZero();
        assertThat(response.conversionRate().trendPercent()).isZero();
    }

    @Test
    void getAdminStats_dailyThreatsBuckets_matchPeriod() {
        stubAdminEmptyExceptDailyThreats();
        when(emailAnalysisRepository.countDailyThreats(any(), any()))
            .thenReturn(List.of(
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 9)), 3L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 10)), 5L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 11)), 2L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 12)), 7L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 13)), 4L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 14)), 1L},
                new Object[]{Date.valueOf(LocalDate.of(2026, 6, 15)), 6L}
            ));

        AdminStatsResponse response = service.getAdminStats(StatsPeriod.WEEK);

        assertThat(response.dailyThreats()).hasSize(7);
        assertThat(response.dailyThreats().stream().mapToInt(AdminStatsResponse.DailyCount::count).sum())
            .isEqualTo(28);
        assertThat(response.dailyThreats().get(0).label()).isEqualTo("Lun");
        assertThat(response.dailyThreats().get(6).label()).isEqualTo("Dom");
    }

    @Test
    void getAdminStats_sourceSplit_groupsByOrigin() {
        stubAdminEmptyExceptSourceSplit();
        OriginCount heuristic = new OriginCount() {
            @Override public AnalysisOrigin getSource() { return AnalysisOrigin.HEURISTIC; }
            @Override public long getCount() { return 70L; }
        };
        OriginCount ai = new OriginCount() {
            @Override public AnalysisOrigin getSource() { return AnalysisOrigin.AI; }
            @Override public long getCount() { return 30L; }
        };
        when(emailAnalysisRepository.countOriginsByDateRange(any(), any()))
            .thenReturn(List.of(heuristic, ai));

        AdminStatsResponse response = service.getAdminStats(StatsPeriod.WEEK);

        assertThat(response.analysisSourceSplit()).hasSize(3);
        assertThat(response.analysisSourceSplit().get(0).source()).isEqualTo("Heurística");
        assertThat(response.analysisSourceSplit().get(0).count()).isEqualTo(70);
        assertThat(response.analysisSourceSplit().get(1).source()).isEqualTo("IA");
        assertThat(response.analysisSourceSplit().get(1).count()).isEqualTo(30);
        assertThat(response.analysisSourceSplit().get(2).count()).isZero();
    }

    private void stubEmptyUserStats() {
        when(emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0.0);
        when(emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(eq(1L), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findHistoryByUser(eq(1L), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(emailAnalysisRepository.findLastThreatAtByUserId(1L))
            .thenReturn(Optional.empty());
    }

    private void stubEmptyUserStatsExceptRiskDistribution() {
        when(emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0.0);
        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findHistoryByUser(eq(1L), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(emailAnalysisRepository.findLastThreatAtByUserId(1L))
            .thenReturn(Optional.empty());
    }

    private void stubEmptyUserStatsExceptFindings() {
        when(emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0.0);
        when(emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(eq(1L), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findHistoryByUser(eq(1L), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(emailAnalysisRepository.findLastThreatAtByUserId(1L))
            .thenReturn(Optional.empty());
    }

    private void stubEmptyUserStatsExceptRecentActivity() {
        when(emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0L);
        when(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(eq(1L), any(), any()))
            .thenReturn(0.0);
        when(emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(eq(1L), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findFindingsByUserIdAndDateRange(eq(1L), any(), any(), any()))
            .thenReturn(List.of());
        when(emailAnalysisRepository.findLastThreatAtByUserId(1L))
            .thenReturn(Optional.empty());
    }

    private void stubAdminEmptyExceptDailyThreats() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(userRepository.countByRoleGrouped()).thenReturn(List.of());
        when(userRepository.countByLastLoginAtAfterAndDeletedAtIsNull(any())).thenReturn(0L);
        when(userRepository.countDailySignups(any(), any())).thenReturn(List.of());
        when(paymentRepository.countActiveSubscriptions(any())).thenReturn(0L);
        when(paymentRepository.countNewActiveSubscriptions(any(), any(), any())).thenReturn(0L);
        when(paymentRepository.countCanceledSubscriptions()).thenReturn(0L);
        when(paymentRepository.countCanceledSubscriptionsBetween(any(), any())).thenReturn(0L);
        when(paymentRepository.countCompletedPaymentsBetween(any(), any())).thenReturn(0L);
        when(emailAnalysisRepository.countByAnalyzedAtBetween(any(), any())).thenReturn(0L);
        when(emailAnalysisRepository.countThreatsByAnalyzedAtBetween(any(), any())).thenReturn(0L);
        when(emailAnalysisRepository.countRiskLevelsByDateRange(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.countOriginsByDateRange(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.countThreatsByHour(any(), any())).thenReturn(List.of());
        when(emailAnalysisRepository.findFindingsByDateRange(any(), any(), any())).thenReturn(List.of());
    }

    private void stubAdminEmptyExceptSignups() {
        stubAdminEmptyExceptDailyThreats();
        when(emailAnalysisRepository.countDailyThreats(any(), any())).thenReturn(List.of());
    }

    private void stubAdminEmptyExceptSourceSplit() {
        stubAdminEmptyExceptDailyThreats();
        when(emailAnalysisRepository.countDailyThreats(any(), any())).thenReturn(List.of());
    }
}

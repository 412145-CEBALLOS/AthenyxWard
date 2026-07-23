package com.athenyx.backend.service.stats;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.AdminStatsResponse;
import com.athenyx.backend.dto.StatsPeriod;
import com.athenyx.backend.dto.UserStatsResponse;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatsService {

    private static final int CATEGORY_PARSE_LIMIT = 5000;
    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final Map<String, String> RULE_LABELS = new HashMap<>();
    private static final Map<AnalysisOrigin, String> ORIGIN_LABELS = new EnumMap<>(AnalysisOrigin.class);
    private static final List<String> WEEK_DAY_LABELS = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");
    private static final List<String> MONTH_LABELS = List.of(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic");

    static {
        RULE_LABELS.put("AuthenticationPassRule", null);
        RULE_LABELS.put("DisplayNameBrandSpoofRule", "Suplantación de marca");
        RULE_LABELS.put("FakeLoginPageRule", "Página de login falsa");
        RULE_LABELS.put("FreeEmailProviderBrandRule", "Proveedor de email gratuito");
        RULE_LABELS.put("HtmlFormRule", "Formulario HTML sospechoso");
        RULE_LABELS.put("MaliciousUrlRule", "URL maliciosa");
        RULE_LABELS.put("MassMailingServiceRule", "Mailing masivo");
        RULE_LABELS.put("RegexPatternRule", "Patrón sospechoso");
        RULE_LABELS.put("ReplyToMismatchRule", "Desajuste Reply-To");
        RULE_LABELS.put("ReturnPathMismatchRule", "Desajuste Return-Path");
        RULE_LABELS.put("RiskyKeywordsRule", "Palabras clave riesgosas");
        RULE_LABELS.put("ScamLanguagePatternRule", "Lenguaje de estafa");
        RULE_LABELS.put("SenderImpersonationRule", "Suplantación de remitente");
        RULE_LABELS.put("ShortenedUrlRule", "URL acortada");
        RULE_LABELS.put("SuspiciousAttachmentRule", "Adjunto sospechoso");
        RULE_LABELS.put("SuspiciousDomainRule", "Dominio sospechoso");
        RULE_LABELS.put("SuspiciousMetadataRule", "Metadatos sospechosos");
        RULE_LABELS.put("SuspiciousTldRule", "TLD sospechoso");
        RULE_LABELS.put("TimezoneInconsistencyRule", "Inconsistencia horaria");
        RULE_LABELS.put("UrgentLanguageRule", "Lenguaje de urgencia");

        ORIGIN_LABELS.put(AnalysisOrigin.HEURISTIC, "Heurística");
        ORIGIN_LABELS.put(AnalysisOrigin.AI, "IA");
        ORIGIN_LABELS.put(AnalysisOrigin.HYBRID, "Híbrido");
    }

    private final EmailAnalysisRepository emailAnalysisRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ConfigService configService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public UserStatsResponse getUserStats(Long userId, StatsPeriod period) {
        LocalDateTime now = LocalDateTime.now(clock);
        StatsPeriod.DateRange current = period.currentRange(now);
        StatsPeriod.DateRange previous = period.previousRange(now);

        long analyzedCurrent = emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(
            userId, current.from(), current.to());
        long analyzedPrevious = emailAnalysisRepository.countByUserIdAndAnalyzedAtBetween(
            userId, previous.from(), previous.to());

        long threatsCurrent = emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(
            userId, current.from(), current.to());
        long threatsPrevious = emailAnalysisRepository.countThreatsByUserIdAndAnalyzedAtBetween(
            userId, previous.from(), previous.to());

        double avgRiskCurrent = orZero(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(
            userId, current.from(), current.to()));
        double avgRiskPrevious = orZero(emailAnalysisRepository.avgRiskPercentageByUserIdAndAnalyzedAtBetween(
            userId, previous.from(), previous.to()));

        double phishingRateCurrent = analyzedCurrent == 0 ? 0.0 : (threatsCurrent * 100.0) / analyzedCurrent;
        double phishingRatePrevious = analyzedPrevious == 0 ? 0.0 : (threatsPrevious * 100.0) / analyzedPrevious;

        List<UserStatsResponse.KpiMetric> kpis = List.of(
            new UserStatsResponse.KpiMetric("Correos analizados", analyzedCurrent, analyzedPrevious,
                trendPercent(analyzedCurrent, analyzedPrevious), analyzedCurrent >= analyzedPrevious),
            new UserStatsResponse.KpiMetric("Amenazas bloqueadas", threatsCurrent, threatsPrevious,
                trendPercent(threatsCurrent, threatsPrevious), threatsCurrent >= threatsPrevious),
            new UserStatsResponse.KpiMetric("Tasa de phishing", phishingRateCurrent, phishingRatePrevious,
                trendPercent(phishingRateCurrent, phishingRatePrevious), phishingRateCurrent >= phishingRatePrevious),
            new UserStatsResponse.KpiMetric("Riesgo medio", avgRiskCurrent, avgRiskPrevious,
                trendPercent(avgRiskCurrent, avgRiskPrevious), avgRiskCurrent >= avgRiskPrevious)
        );

        List<UserStatsResponse.DailyCount> dailyThreats = buildDailyThreats(
            period,
            toDailyMap(emailAnalysisRepository.countDailyThreats(current.from(), current.to())),
            current);

        List<UserStatsResponse.RiskBucket> riskDistribution = buildRiskDistribution(
            emailAnalysisRepository.countRiskLevelsByUserIdAndDateRange(userId, current.from(), current.to()),
            UserStatsResponse.RiskBucket::new);

        List<UserStatsResponse.CategoryCount> topCategories = buildTopCategories(
            emailAnalysisRepository.findFindingsByUserIdAndDateRange(
                userId, current.from(), current.to(), PageRequest.of(0, CATEGORY_PARSE_LIMIT)),
            UserStatsResponse.CategoryCount::new);

        List<UserStatsResponse.RecentItem> recentActivity = emailAnalysisRepository.findHistoryByUser(
                userId, current.from(), current.to(), PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
            .getContent().stream()
            .map(this::toRecentItem)
            .toList();

        Optional<LocalDateTime> lastThreatAt = emailAnalysisRepository.findLastThreatAtByUserId(userId);

        UserStatsResponse.TrialUsage trialUsage = null;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getRole() == Role.TRIAL) {
            int limit = configService.getInt(ConfigKey.TRIAL_ANALYSIS_LIMIT);
            trialUsage = new UserStatsResponse.TrialUsage(user.getAnalysisCount(), limit);
        }

        return new UserStatsResponse(
            period.name().toLowerCase(),
            kpis,
            dailyThreats,
            riskDistribution,
            topCategories,
            recentActivity,
            lastThreatAt.orElse(null),
            trialUsage
        );
    }

    public AdminStatsResponse getAdminStats(StatsPeriod period) {
        LocalDateTime now = LocalDateTime.now(clock);
        StatsPeriod.DateRange current = period.currentRange(now);
        StatsPeriod.DateRange previous = period.previousRange(now);

        long totalUsersCurrent = userRepository.countByDeletedAtIsNull();
        long totalUsersPrevious = totalUsersCurrent
            - userRepository.countByCreatedAtBetween(current.from(), current.to());

        long activeSubsCurrent = paymentRepository.countActiveSubscriptions(now);
        long activeSubsPrevious = activeSubsCurrent
            - paymentRepository.countNewActiveSubscriptions(now, current.from(), current.to());

        long canceledSubsTotal = paymentRepository.countCanceledSubscriptions();
        long canceledCurrent = paymentRepository.countCanceledSubscriptionsBetween(current.from(), current.to());
        long canceledPrevious = paymentRepository.countCanceledSubscriptionsBetween(previous.from(), previous.to());

        long totalAnalysesCurrent = emailAnalysisRepository.countByAnalyzedAtBetween(current.from(), current.to());
        long totalAnalysesPrevious = emailAnalysisRepository.countByAnalyzedAtBetween(previous.from(), previous.to());

        double avgAnalysesCurrent = totalUsersCurrent == 0 ? 0.0 : (double) totalAnalysesCurrent / totalUsersCurrent;
        double avgAnalysesPrevious = totalUsersPrevious == 0 ? 0.0 : (double) totalAnalysesPrevious / totalUsersPrevious;

        long globalThreatsCurrent = emailAnalysisRepository.countThreatsByAnalyzedAtBetween(current.from(), current.to());
        long globalThreatsPrevious = emailAnalysisRepository.countThreatsByAnalyzedAtBetween(previous.from(), previous.to());

        List<AdminStatsResponse.KpiMetric> kpis = List.of(
            new AdminStatsResponse.KpiMetric("Usuarios totales", totalUsersCurrent, totalUsersPrevious,
                trendPercent(totalUsersCurrent, totalUsersPrevious), totalUsersCurrent >= totalUsersPrevious),
            new AdminStatsResponse.KpiMetric("Suscripciones activas", activeSubsCurrent, activeSubsPrevious,
                trendPercent(activeSubsCurrent, activeSubsPrevious), activeSubsCurrent >= activeSubsPrevious),
            new AdminStatsResponse.KpiMetric("Suscripciones canceladas", canceledSubsTotal, canceledPrevious,
                trendPercent(canceledCurrent, canceledPrevious), canceledCurrent >= canceledPrevious),
            new AdminStatsResponse.KpiMetric("Análisis totales", totalAnalysesCurrent, totalAnalysesPrevious,
                trendPercent(totalAnalysesCurrent, totalAnalysesPrevious), totalAnalysesCurrent >= totalAnalysesPrevious),
            new AdminStatsResponse.KpiMetric("Media análisis / usuario", avgAnalysesCurrent, avgAnalysesPrevious,
                trendPercent(avgAnalysesCurrent, avgAnalysesPrevious), avgAnalysesCurrent >= avgAnalysesPrevious),
            new AdminStatsResponse.KpiMetric("Amenazas globales", globalThreatsCurrent, globalThreatsPrevious,
                trendPercent(globalThreatsCurrent, globalThreatsPrevious), globalThreatsCurrent >= globalThreatsPrevious)
        );

        List<AdminStatsResponse.DailyCount> dailyThreats = buildAdminDailyThreats(
            period,
            toDailyMap(emailAnalysisRepository.countDailyThreats(current.from(), current.to())),
            current);

        List<AdminStatsResponse.RiskBucket> riskDistribution = buildRiskDistribution(
            emailAnalysisRepository.countRiskLevelsByDateRange(current.from(), current.to()),
            AdminStatsResponse.RiskBucket::new);

        List<AdminStatsResponse.RoleBucket> userSplit = userRepository.countByRoleGrouped().stream()
            .map(p -> new AdminStatsResponse.RoleBucket(p.getRole().name(), (int) p.getCount()))
            .toList();

        List<AdminStatsResponse.CategoryCount> topCategories = buildTopCategories(
            emailAnalysisRepository.findFindingsByDateRange(
                current.from(), current.to(), PageRequest.of(0, CATEGORY_PARSE_LIMIT)),
            AdminStatsResponse.CategoryCount::new);

        List<AdminStatsResponse.SourceBucket> analysisSourceSplit = buildSourceSplit(
            emailAnalysisRepository.countOriginsByDateRange(current.from(), current.to()));

        AdminStatsResponse.EngagementMetrics engagement = new AdminStatsResponse.EngagementMetrics(
            (int) userRepository.countByLastLoginAtAfterAndDeletedAtIsNull(now.minus(1, ChronoUnit.DAYS)),
            (int) userRepository.countByLastLoginAtAfterAndDeletedAtIsNull(now.minus(7, ChronoUnit.DAYS)),
            (int) userRepository.countByLastLoginAtAfterAndDeletedAtIsNull(now.minus(30, ChronoUnit.DAYS))
        );

        long signupsCurrent = userRepository.countByCreatedAtBetween(current.from(), current.to());
        long signupsPrevious = userRepository.countByCreatedAtBetween(previous.from(), previous.to());
        long completedPaymentsCurrent = paymentRepository.countCompletedPaymentsBetween(current.from(), current.to());
        long completedPaymentsPrevious = paymentRepository.countCompletedPaymentsBetween(previous.from(), previous.to());

        double conversionCurrent = signupsCurrent == 0 ? 0.0 : (completedPaymentsCurrent * 100.0) / signupsCurrent;
        double conversionPrevious = signupsPrevious == 0 ? 0.0 : (completedPaymentsPrevious * 100.0) / signupsPrevious;
        AdminStatsResponse.ConversionRate conversionRate = new AdminStatsResponse.ConversionRate(
            conversionCurrent, conversionPrevious,
            trendPercent(conversionCurrent, conversionPrevious), conversionCurrent >= conversionPrevious);

        List<AdminStatsResponse.DailyCount> signups = buildAdminSignups(
            period,
            toDailyMap(userRepository.countDailySignups(current.from(), current.to())),
            current);

        List<AdminStatsResponse.HourBucket> threatsByHour = buildHourBuckets(
            emailAnalysisRepository.countThreatsByHour(current.from(), current.to()));

        return new AdminStatsResponse(
            period.name().toLowerCase(),
            kpis,
            dailyThreats,
            riskDistribution,
            userSplit,
            topCategories,
            analysisSourceSplit,
            engagement,
            conversionRate,
            signups,
            threatsByHour
        );
    }

    private UserStatsResponse.RecentItem toRecentItem(EmailAnalysis analysis) {
        return new UserStatsResponse.RecentItem(
            analysis.getAnalyzedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("es-ES"))),
            analysis.getEmail().getSender(),
            analysis.getRiskPercentage(),
            analysis.getRiskLevel().name()
        );
    }

    private <T> List<T> buildDailyThreats(
        StatsPeriod period,
        Map<LocalDate, Long> counts,
        StatsPeriod.DateRange range,
        java.util.function.BiFunction<String, Integer, T> factory) {

        int bucketCount = period == StatsPeriod.WEEK ? 7 : period == StatsPeriod.MONTH ? 4 : 12;
        long bucketDays = (long) Math.ceil((double) period.getDays() / bucketCount);
        List<T> result = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            LocalDateTime bucketFrom = range.from().plusDays(i * bucketDays);
            LocalDateTime bucketTo = (i == bucketCount - 1)
                ? range.to().plusDays(1)
                : range.from().plusDays((i + 1) * bucketDays);
            int total = 0;
            LocalDate cursor = bucketFrom.toLocalDate();
            LocalDate end = bucketTo.toLocalDate();
            while (!cursor.isAfter(end.minusDays(1))) {
                total += counts.getOrDefault(cursor, 0L).intValue();
                cursor = cursor.plusDays(1);
            }
            String label = bucketLabel(period, i, bucketFrom);
            result.add(factory.apply(label, total));
        }
        return result;
    }

    private List<UserStatsResponse.DailyCount> buildDailyThreats(
        StatsPeriod period, Map<LocalDate, Long> counts, StatsPeriod.DateRange range) {
        return buildDailyThreats(period, counts, range, UserStatsResponse.DailyCount::new);
    }

    private List<AdminStatsResponse.DailyCount> buildAdminDailyThreats(
        StatsPeriod period, Map<LocalDate, Long> counts, StatsPeriod.DateRange range) {
        return buildDailyThreats(period, counts, range, AdminStatsResponse.DailyCount::new);
    }

    private List<AdminStatsResponse.DailyCount> buildAdminSignups(
        StatsPeriod period, Map<LocalDate, Long> counts, StatsPeriod.DateRange range) {
        return buildDailyThreats(period, counts, range, AdminStatsResponse.DailyCount::new);
    }

    private String bucketLabel(StatsPeriod period, int index, LocalDateTime bucketFrom) {
        return switch (period) {
            case WEEK -> {
                int dow = bucketFrom.getDayOfWeek().getValue() - 1;
                yield WEEK_DAY_LABELS.get(dow);
            }
            case MONTH -> "Sem " + (index + 1);
            case YEAR -> MONTH_LABELS.get(bucketFrom.getMonthValue() - 1);
        };
    }

    private <T> List<T> buildRiskDistribution(
        List<EmailAnalysisRepository.RiskLevelCount> counts,
        java.util.function.BiFunction<String, Integer, T> factory) {

        Map<ThreatLevel, Long> map = counts.stream()
            .collect(Collectors.toMap(EmailAnalysisRepository.RiskLevelCount::getLevel,
                EmailAnalysisRepository.RiskLevelCount::getCount));
        return List.of(
            factory.apply("GREEN", map.getOrDefault(ThreatLevel.GREEN, 0L).intValue()),
            factory.apply("YELLOW", map.getOrDefault(ThreatLevel.YELLOW, 0L).intValue()),
            factory.apply("RED", map.getOrDefault(ThreatLevel.RED, 0L).intValue())
        );
    }

    private List<AdminStatsResponse.SourceBucket> buildSourceSplit(
        List<EmailAnalysisRepository.OriginCount> counts) {
        Map<AnalysisOrigin, Long> map = counts.stream()
            .collect(Collectors.toMap(EmailAnalysisRepository.OriginCount::getSource,
                EmailAnalysisRepository.OriginCount::getCount));
        return List.of(
            new AdminStatsResponse.SourceBucket(ORIGIN_LABELS.get(AnalysisOrigin.HEURISTIC),
                map.getOrDefault(AnalysisOrigin.HEURISTIC, 0L).intValue()),
            new AdminStatsResponse.SourceBucket(ORIGIN_LABELS.get(AnalysisOrigin.AI),
                map.getOrDefault(AnalysisOrigin.AI, 0L).intValue()),
            new AdminStatsResponse.SourceBucket(ORIGIN_LABELS.get(AnalysisOrigin.HYBRID),
                map.getOrDefault(AnalysisOrigin.HYBRID, 0L).intValue())
        );
    }

    private <T> List<T> buildTopCategories(
        List<String> findingsList,
        java.util.function.BiFunction<String, Integer, T> factory) {

        Map<String, Integer> categoryCounts = new HashMap<>();
        TypeReference<List<com.athenyx.backend.dto.HeuristicAnalysisResponse.HeuristicFindingDto>> typeRef =
            new TypeReference<>() {};

        for (String findingsJson : findingsList) {
            if (findingsJson == null || findingsJson.isBlank()) {
                continue;
            }
            try {
                List<com.athenyx.backend.dto.HeuristicAnalysisResponse.HeuristicFindingDto> findings =
                    objectMapper.readValue(findingsJson, typeRef);
                for (com.athenyx.backend.dto.HeuristicAnalysisResponse.HeuristicFindingDto finding : findings) {
                    if (finding.rule() == null) {
                        continue;
                    }
                    String label = RULE_LABELS.get(finding.rule());
                    if (label == null) {
                        label = finding.rule();
                    }
                    categoryCounts.merge(label, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.debug("Could not parse findings for stats category aggregation: {}", e.getMessage());
            }
        }

        return categoryCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(e -> factory.apply(e.getKey(), e.getValue()))
            .toList();
    }

    private List<AdminStatsResponse.HourBucket> buildHourBuckets(List<Object[]> rows) {
        int[] counts = new int[24];
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            int hour = ((Number) row[0]).intValue();
            int count = ((Number) row[1]).intValue();
            if (hour >= 0 && hour < 24) {
                counts[hour] = count;
            }
        }
        List<AdminStatsResponse.HourBucket> result = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            result.add(new AdminStatsResponse.HourBucket(h, counts[h]));
        }
        return result;
    }

    private Map<LocalDate, Long> toDailyMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            LocalDate date = ((Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            map.put(date, count);
        }
        return map;
    }

    private double trendPercent(double current, double previous) {
        if (previous == 0.0) {
            return current == 0.0 ? 0.0 : 100.0;
        }
        return ((current - previous) / previous) * 100.0;
    }

    private double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}

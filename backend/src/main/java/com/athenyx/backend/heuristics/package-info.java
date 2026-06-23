/**
 * Heuristic engine and scoring pipeline.
 *
 * <h2>Pipeline</h2>
 *
 * <pre>
 *   Email (entity)
 *      │
 *      ▼
 *   EmailHeuristicsInput
 *      │
 *      ▼
 *   HeuristicEngine.run()                ── iterates all 19 rules
 *      │  ├─ MaliciousUrlRule
 *      │  ├─ HtmlFormRule
 *      │  ├─ FreeEmailProviderBrandRule
 *      │  ├─ FakeLoginPageRule
 *      │  ├─ DisplayNameBrandSpoofRule
 *      │  ├─ ReturnPathMismatchRule
 *      │  ├─ ReplyToMismatchRule
 *      │  ├─ RegexPatternRule
 *      │  ├─ MassMailingServiceRule
 *      │  ├─ ShortenedUrlRule
 *      │  ├─ SenderImpersonationRule
 *      │  ├─ ScamLanguagePatternRule
 *      │  ├─ RiskyKeywordsRule
 *      │  ├─ SuspiciousMetadataRule
 *      │  ├─ SuspiciousDomainRule
 *      │  ├─ SuspiciousAttachmentRule
 *      │  ├─ TimezoneInconsistencyRule
 *      │  ├─ SuspiciousTldRule
 *      │  └─ UrgentLanguageRule
 *      ▼
 *   List&lt;HeuristicFinding&gt;
 *      │
 *      ▼
 *   ThreatScorer.score()                 ── weighted aggregation
 *      │  GREEN  (0-39)   → safe
 *      │  YELLOW (40-69)  → suspicious
 *      │  RED    (70-100) → dangerous
 *      ▼
 *   HeuristicResult                      ── persisted as EmailAnalysis
 * </pre>
 *
 * <h2>Key components</h2>
 * <ul>
 *   <li>{@link com.athenyx.backend.heuristics.HeuristicEngine} — applies every
 *       {@link com.athenyx.backend.heuristics.rules.HeuristicRule} bean.</li>
 *   <li>{@link com.athenyx.backend.heuristics.ThreatScorer} — collapses findings
 *       into a 0-100 percentage and a {@link ThreatLevel}.</li>
 *   <li>{@link com.athenyx.backend.heuristics.HeuristicAnalysisService} —
 *       orchestrator with 24 h cache, trial-limit validation, async execution
 *       and persistence via {@link com.athenyx.backend.entity.EmailAnalysis}.</li>
 * </ul>
 *
 * @see com.athenyx.backend.metadata
 */
package com.athenyx.backend.heuristics;

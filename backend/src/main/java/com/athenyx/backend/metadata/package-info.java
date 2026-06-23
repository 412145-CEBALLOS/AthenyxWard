/**
 * Metadata analysis layer for Gmail emails.
 *
 * <p>This package implements the "Metadata Analysis" requirement of
 * {@code SPEC.md} § Sprint 2. It extracts, normalises and validates
 * the metadata headers of an email (From, Return-Path, Reply-To,
 * Received chain, Date, Authentication-Results) and produces a
 * structured result that the heuristic engine can consume.</p>
 *
 * <h2>Main components</h2>
 * <ul>
 *   <li>{@link com.athenyx.backend.metadata.MetadataExtractor} — pulls every relevant
 *       header out of the persisted {@link com.athenyx.backend.entity.Email} and
 *       produces an {@link com.athenyx.backend.metadata.ExtractedHeaders} record.</li>
 *   <li>{@link com.athenyx.backend.metadata.AuthenticationResultsParser} — parses
 *       the {@code Authentication-Results} header (SPF, DKIM, DMARC).
 *       <strong>No DNS lookups are performed</strong> — we only trust the result
 *       that Gmail already computed.</li>
 *   <li>{@link com.athenyx.backend.metadata.SenderValidator} — checks
 *       {@code From} vs {@code Return-Path} and {@code Reply-To} mismatches.</li>
 *   <li>{@link com.athenyx.backend.metadata.TimestampAnalyzer} — flags future
 *       dates, drift &gt; 24 h and anomalous timezones.</li>
 *   <li>{@link com.athenyx.backend.metadata.MassMailingDetector} — identifies
 *       Mailchimp, SendGrid, Mandrill, Postmark, Amazon SES, Mailgun and
 *       generic bulk senders.</li>
 *   <li>{@link com.athenyx.backend.metadata.SenderTrustCalculator} — combines
 *       every signal into a 0-100 score and a
 *       {@link com.athenyx.backend.metadata.SenderTrustLevel}
 *       ({@code TRUSTED}, {@code UNKNOWN}, {@code SUSPICIOUS}).</li>
 *   <li>{@link com.athenyx.backend.metadata.MetadataAnalyzer} — orchestrator that
 *       wires the previous five components together and returns a single
 *       {@link com.athenyx.backend.metadata.MetadataAnalysisResult}.</li>
 *   <li>{@link com.athenyx.backend.metadata.EmailHeaderCache} — in-memory cache
 *       (TTL 24 h) that prevents re-running the analysis for the same email
 *       within the analysis cache window.</li>
 * </ul>
 *
 * <h2>Trust score formula</h2>
 *
 * <p>The score is computed as a weighted sum of positive and negative signals
 * (start at 50, clamp to [0, 100]):</p>
 *
 * <table>
 *   <caption>Signals</caption>
 *   <tr><th>Signal</th><th>Delta</th></tr>
 *   <tr><td>SPF pass</td><td>+30</td></tr>
 *   <tr><td>DKIM pass</td><td>+25</td></tr>
 *   <tr><td>DMARC pass</td><td>+25</td></tr>
 *   <tr><td>Trusted domain (whitelist)</td><td>+20</td></tr>
 *   <tr><td>Free email provider</td><td>-20</td></tr>
 *   <tr><td>Mass mailing service</td><td>-30</td></tr>
 *   <tr><td>Return-Path mismatch</td><td>-25</td></tr>
 *   <tr><td>Reply-To mismatch</td><td>-25</td></tr>
 *   <tr><td>Timezone anomaly</td><td>-15</td></tr>
 *   <tr><td>Future date</td><td>-20</td></tr>
 * </table>
 *
 * <p>Thresholds: {@code TRUSTED >= 70}, {@code UNKNOWN 40-69}, {@code SUSPICIOUS < 40}.</p>
 *
 * @see com.athenyx.backend.heuristics.HeuristicAnalysisService
 * @see com.athenyx.backend.entity.Email
 */
package com.athenyx.backend.metadata;

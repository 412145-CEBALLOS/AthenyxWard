package com.athenyx.backend.gmail;

import com.athenyx.backend.dto.EmailDetail;
import com.athenyx.backend.dto.EmailImportantToggleResponse;
import com.athenyx.backend.dto.EmailPageResponse;
import com.athenyx.backend.dto.EmailSummary;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.entity.GmailPageToken;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.repository.GmailPageTokenRepository;
import com.athenyx.backend.security.TokenEncryptionService;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.Profile;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gmail API integration. Responsibilities:
 * <ul>
 *     <li>Build a {@link Gmail} client from the user's stored (encrypted)
 *         OAuth credentials, transparently refreshing expired access
 *         tokens and persisting the new ones.</li>
 *     <li>List recent messages and lazily hydrate them in a single
 *         batched call.</li>
 *     <li>Detect upstream changes via {@code historyId} and invalidate
 *         the per-user pagination cache when needed.</li>
 *     <li>Persist a <strong>subset</strong> of each message (sender,
 *         subject, snippet, plain-text body, HTML preview, extracted
 *         URLs, dates, read flag) for later analysis.</li>
 *     <li>Render the original Date header through a tolerant
 *         {@link #parseDate(String)} that handles RFC 1123, numeric
 *         offsets, abbreviated zones and bare timestamps.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailService {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final GmailPageTokenRepository gmailPageTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    private static final String APPLICATION_NAME = "Athenyx Ward";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final int MAX_RESULTS = 20;

    public EmailPageResponse fetchEmails(Long userId, int page) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        try {
            Gmail gmailService = buildGmailService(user);

            Profile profile = gmailService.users().getProfile("me").execute();
            String currentHistoryId = profile.getHistoryId().toString();

            if (!currentHistoryId.equals(user.getGmailHistoryId())) {
                gmailPageTokenRepository.deleteAllByUserId(userId);
                user.setGmailHistoryId(currentHistoryId);
                userRepository.save(user);
            }

            prefetchUntilPage(userId, page);
            String pageToken = null;
            if (page > 0) {
                pageToken = gmailPageTokenRepository
                    .findByUserIdAndPage(userId, page)
                    .map(GmailPageToken::getToken)
                    .orElse(null);
            }

            ListMessagesResponse response = gmailService.users().messages()
                    .list("me")
                    .setMaxResults((long) MAX_RESULTS)
                    .setPageToken(pageToken)
                    .execute();

            if (response.getMessages() == null || response.getMessages().isEmpty()) {
                return new EmailPageResponse(List.of(), page, MAX_RESULTS, false);
            }

            String nextPageToken = response.getNextPageToken();
            if (nextPageToken != null) {
                GmailPageToken tokenEntity = GmailPageToken.builder()
                .userId(userId)
                .page(page + 1)
                .token(nextPageToken)
                .build();

                gmailPageTokenRepository.saveIfAbsent(tokenEntity);
            }

            List<EmailSummary> summaries = new ArrayList<>();
            BatchRequest batch = gmailService.batch();

            for (Message msg : response.getMessages()) {
                gmailService.users().messages()
                        .get("me", msg.getId())
                        .setFormat("full")
                        .queue(batch, new JsonBatchCallback<Message>() {
                            @Override
                            public void onSuccess(Message fullMessage, HttpHeaders headers) throws IOException {
                                summaries.add(processMessage(fullMessage, user));
                            }

                            @Override
                            public void onFailure(GoogleJsonError e, HttpHeaders headers) throws IOException {
                                log.error("Error al obtener mail: {}", e.getMessage());
                            }
                        });
            }

            batch.execute();

            boolean hasNextPage = nextPageToken != null;
            return new EmailPageResponse(summaries, page, MAX_RESULTS, hasNextPage);

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            if (statusCode == 401 || statusCode == 403) {
                log.warn("Google token invalid or revoked for user {}, instructing re-login", userId);
                throw new RuntimeException("Google token revoked or expired, please re-login to continue.");
            }
            log.error("Error al obtener correos de Gmail", e);
            throw new RuntimeException("Error al obtener correos de Gmail: " + e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("invalid_grant") || msg.contains("Token has been revoked"))) {
                log.warn("Google token invalid_grant for user {}, instructing re-login", userId);
                throw new RuntimeException("Google token revoked or expired, please re-login to continue.");
            }
            log.error("Error al obtener correos de Gmail", e);
            throw new RuntimeException("Error al obtener correos de Gmail: " + e.getMessage());
        }
    }

    public void prefetchUntilPage(Long userId, int targetPage) throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        Gmail gmailService = buildGmailService(user);

        for (int i = 0; i < targetPage; i++) {
            boolean exists = gmailPageTokenRepository
                .findByUserIdAndPage(userId, i + 1).isPresent();
            if (exists) continue;

            String currentToken = null;
            if (i > 0) {
                currentToken = gmailPageTokenRepository
                    .findByUserIdAndPage(userId, i)
                    .map(GmailPageToken::getToken)
                    .orElse(null);
                if (currentToken == null) break;
            }

            ListMessagesResponse response = gmailService.users().messages()
                .list("me")
                .setMaxResults((long) MAX_RESULTS)
                .setPageToken(currentToken)
                .execute();

            String nextToken = response.getNextPageToken();
            if (nextToken == null) break;

            gmailPageTokenRepository.saveIfAbsent(
                GmailPageToken.builder()
                    .userId(userId)
                    .page(i + 1)
                    .token(nextToken)
                    .build()
            );
        }
    }

    public List<EmailSummary> fetchRecentEmails(Long userId) {
        return fetchEmails(userId, 0).emails();
    }

    public EmailDetail getEmailDetail(Long userId, Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new RuntimeException("Correo no encontrado"));

        if (!email.getUser().getId().equals(userId)) {
            throw new RuntimeException("Acceso denegado");
        }

        email.setRead(true);
        emailRepository.save(email);

        return new EmailDetail(
                email.getId(),
                email.getGmailId(),
                email.getSender(),
                email.getSenderName(),
                email.getSubject(),
                email.getSnippet(),
                email.getContentForAnalysis(),
                email.getHtmlContent(),
                email.getReceivedAt(),
                email.getFetchedAt(),
                email.isRead(),
                email.getOriginalDateHeader(),
                email.isImportant()
        );
    }

    public List<EmailSummary> getImportantEmails(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("Usuario no encontrado");
        }
        List<Email> emails = emailRepository.findByUserIdAndIsImportantTrueOrderByReceivedAtDesc(userId);
        return emails.stream().map(email -> new EmailSummary(
                email.getId(),
                email.getGmailId(),
                email.getSender(),
                email.getSenderName(),
                email.getSubject(),
                email.getSnippet(),
                email.getReceivedAt(),
                email.getFetchedAt(),
                email.isRead(),
                email.getOriginalDateHeader(),
                email.isImportant()
        )).toList();
    }

    public long getImportantEmailCount(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("Usuario no encontrado");
        }
        return emailRepository.countByUserIdAndIsImportantTrue(userId);
    }

    public EmailImportantToggleResponse toggleImportant(Long userId, Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new RuntimeException("Correo no encontrado"));
        if (!email.getUser().getId().equals(userId)) {
            throw new RuntimeException("Acceso denegado");
        }
        email.setImportant(!email.isImportant());
        emailRepository.save(email);
        return new EmailImportantToggleResponse(emailId, email.isImportant());
    }

    private Gmail buildGmailService(User user) throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        Date expirationTime = user.getGoogleAccessTokenExpiresAt() != null
                ? Date.from(user.getGoogleAccessTokenExpiresAt().atZone(ZoneOffset.UTC).toInstant())
                : null;

        String decryptedAccessToken = tokenEncryptionService.decrypt(user.getGoogleAccessToken());
        String decryptedRefreshToken = user.getGoogleRefreshToken() != null
                ? tokenEncryptionService.decrypt(user.getGoogleRefreshToken())
                : null;

        UserCredentials credentials;
        if (decryptedRefreshToken != null) {
            UserCredentials.Builder builder = UserCredentials.newBuilder()
                    .setClientId(googleClientId)
                    .setClientSecret(googleClientSecret)
                    .setAccessToken(new AccessToken(decryptedAccessToken, expirationTime))
                    .setRefreshToken(decryptedRefreshToken);
            credentials = builder.build();
        } else {
            credentials = UserCredentials.newBuilder()
                    .setClientId(googleClientId)
                    .setClientSecret(googleClientSecret)
                    .setAccessToken(new AccessToken(decryptedAccessToken, expirationTime))
                    .build();
        }

        final Long userId = user.getId();
        credentials.addChangeListener(new OAuth2Credentials.CredentialsChangedListener() {
            @Override
            public void onChanged(OAuth2Credentials creds) {
                if (!(creds instanceof UserCredentials uc)) {
                    return;
                }
                try {
                    String newAccess = uc.getAccessToken() != null ? uc.getAccessToken().getTokenValue() : null;
                    String newRefresh = uc.getRefreshToken();
                    Date newExpiration = uc.getAccessToken() != null ? uc.getAccessToken().getExpirationTime() : null;

                    User u = userRepository.findById(userId).orElse(null);
                    if (u == null) {
                        log.warn("User {} not found while persisting refreshed token", userId);
                        return;
                    }
                    if (newAccess != null) {
                        u.setGoogleAccessToken(tokenEncryptionService.encrypt(newAccess));
                    }
                    if (newRefresh != null) {
                        u.setGoogleRefreshToken(tokenEncryptionService.encrypt(newRefresh));
                    }
                    if (newExpiration != null) {
                        u.setGoogleAccessTokenExpiresAt(
                                LocalDateTime.ofInstant(newExpiration.toInstant(), ZoneOffset.UTC));
                    }
                    userRepository.save(u);
                    log.info("Persisted refreshed OAuth2 tokens for user {}", userId);
                } catch (Exception e) {
                    log.error("Failed to persist refreshed credentials for user {}", userId, e);
                }
            }
        });

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Gmail.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private EmailSummary processMessage(Message message, User user) {
        Map<String, String> headers = extractHeaders(message);

        String from = headers.getOrDefault("From", "Desconocido");
        String subject = headers.getOrDefault("Subject", "(Sin asunto)");
        String dateStr = headers.get("Date");

        String senderEmail = extractEmailFromHeader(from);
        String senderName = extractNameFromHeader(from);

        LocalDateTime receivedAt = parseDate(dateStr);
        LocalDateTime fetchedAt = LocalDateTime.now();

        String snippet = message.getSnippet() != null ? message.getSnippet() : "";
        String contentForAnalysis = extractPlainText(message);
        String htmlContent = extractHtmlContent(message);
        String urls = extractUrls(message);

        Email email = emailRepository.findByGmailIdAndUserId(message.getId(), user.getId())
                .orElseGet(() -> {
                    Email newEmail = Email.builder()
                            .gmailId(message.getId())
                            .sender(senderEmail)
                            .senderName(senderName)
                            .subject(subject)
                            .snippet(snippet)
                            .contentForAnalysis(contentForAnalysis)
                            .htmlContent(htmlContent)
                            .extractedUrls(urls)
                            .receivedAt(receivedAt)
                            .originalDateHeader(dateStr)
                            .isRead(false)
                            .user(user)
                            .build();
                    return emailRepository.save(newEmail);
                });

        return new EmailSummary(
                email.getId(),
                message.getId(),
                senderEmail,
                senderName,
                subject,
                snippet,
                receivedAt,
                fetchedAt,
                email.isRead(),
                dateStr,
                email.isImportant()
        );
    }

    private Map<String, String> extractHeaders(Message message) {
        Map<String, String> headers = new HashMap<>();
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                headers.put(header.getName(), header.getValue());
            }
        }
        return headers;
    }

    private String extractEmailFromHeader(String from) {
        if (from == null) return "Desconocido";
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start >= 0 && end > start) {
            return from.substring(start + 1, end);
        }
        return from;
    }

    private String extractNameFromHeader(String from) {
        if (from == null) return "Desconocido";
        int start = from.indexOf('<');
        if (start > 0) {
            return from.substring(0, start).trim().replace("\"", "");
        }
        return from;
    }

    LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            log.warn("Date header is null or blank, using current time");
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        String cleaned = dateStr.trim();
        log.debug("Parsing date string: {}", cleaned);

        // 1) JDK's built-in RFC 1123 formatter: "Tue, 3 Jun 2008 11:05:30 GMT"
        try {
            ZonedDateTime rfc = ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME);
            LocalDateTime result = rfc.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            log.debug("Parsed as RFC 1123: {}", result);
            return result;
        } catch (Exception e) {
            log.trace("RFC 1123 parse failed: {}", e.getMessage());
        }

        // 2) Strip trailing parenthesised zone abbreviation ("(MDT)", "(UTC)", "(PST)").
        //    Then strip the optional "Tue, " day-of-week prefix.
        String datePart = cleaned
                .replaceAll("\\s+\\([A-Z]{2,5}\\)\\s*$", "")
                .replaceAll("\\s+\\(UTC\\)\\s*$", "")
                .trim();
        if (datePart.contains(",")) {
            datePart = datePart.substring(datePart.indexOf(',') + 1).trim();
        }

        // 3) Numeric offset variants. " -0600" and " +0000" → Z; " -06:00" → XXX.
        DateTimeFormatter[] zonedFormatters = {
                DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
                DateTimeFormatter.ofPattern("d MMM yyyy HH:mm Z", Locale.US),
                DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXX", Locale.US),
                DateTimeFormatter.ofPattern("d MMM yyyy HH:mm XXX", Locale.US)
        };
        for (DateTimeFormatter fmt : zonedFormatters) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(datePart, fmt);
                LocalDateTime result = odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                log.debug("Parsed with {}: {}", fmt, result);
                return result;
            } catch (Exception e) {
                log.trace("Offset pattern {} failed: {}", fmt, e.getMessage());
            }
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(datePart, fmt);
                LocalDateTime result = zdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                log.debug("Parsed with {}: {}", fmt, result);
                return result;
            } catch (Exception e) {
                log.trace("Zoned pattern {} failed: {}", fmt, e.getMessage());
            }
        }

        // 4) Abbreviated zone names ("MDT", "PST", …).
        for (String pattern : new String[] {
                "d MMM yyyy HH:mm:ss z",
                "d MMM yyyy HH:mm z"
        }) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(datePart,
                        DateTimeFormatter.ofPattern(pattern, Locale.US));
                LocalDateTime result = zdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                log.debug("Parsed with {}: {}", pattern, result);
                return result;
            } catch (Exception e) {
                log.trace("Abbrev pattern {} failed: {}", pattern, e.getMessage());
            }
        }

        // 5) No timezone at all.
        for (String pattern : new String[] {
                "d MMM yyyy HH:mm:ss",
                "d MMM yyyy HH:mm"
        }) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(datePart,
                        DateTimeFormatter.ofPattern(pattern, Locale.US));
                log.debug("Parsed as local with {}: {}", pattern, ldt);
                return ldt;
            } catch (Exception e) {
                log.trace("Local pattern {} failed: {}", pattern, e.getMessage());
            }
        }

        log.warn("All date parsing patterns failed for: {}, using current time", cleaned);
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String extractPlainText(Message message) {
        if (message.getPayload() == null) return "";

        String text = extractTextFromPart(message.getPayload());
        if (text == null || text.isBlank()) {
            return message.getSnippet() != null ? message.getSnippet() : "";
        }

        return text.length() > 10000 ? text.substring(0, 10000) : text;
    }

    private String extractTextFromPart(MessagePart part) {
        if (part == null) return "";

        if (part.getBody() != null && part.getBody().getData() != null) {
            String mimeType = part.getMimeType() != null ? part.getMimeType() : "";
            if (mimeType.startsWith("text/plain")) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String result = extractTextFromPart(subPart);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }

        return "";
    }

    private String extractUrls(Message message) {
        String content = extractPlainText(message);
        String htmlContent = extractHtmlContent(message);
        String allContent = content + " " + htmlContent;

        List<String> urls = new ArrayList<>();
        String regex = "https?://[^\\s<>\"')\\]]+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(allContent);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return String.join(",", urls);
    }

    private String extractHtmlContent(Message message) {
        return message.getPayload() == null
                ? ""
                : extractHtmlFromPart(message.getPayload());
    }

    private String extractHtmlFromPart(MessagePart part) {
        if (part == null) return "";

        if (part.getBody() != null && part.getBody().getData() != null) {
            String mimeType = part.getMimeType() != null ? part.getMimeType() : "";
            if (mimeType.startsWith("text/html")) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String result = extractHtmlFromPart(subPart);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }

        return "";
    }
}
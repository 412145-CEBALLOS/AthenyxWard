package com.athenyx.backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson serialisation tweaks.
 *
 * <p>The reminder feature exposed a subtle bug: the SPA builds a
 * {@code Date} from the user's local clock (e.g. 06:00 in
 * Argentina, UTC-3), converts it to a UTC ISO with
 * {@code .toISOString()} and posts the result to the backend. The
 * backend deserialises that as an {@link java.time.Instant} (in
 * UTC) and stores it as a {@link LocalDateTime} in MySQL. When the
 * backend then serialises the row back, Jackson's default
 * {@code LocalDateTimeSerializer} emits the value without a
 * timezone suffix ({@code "2026-06-25T09:00:00"}), so the
 * browser's {@code new Date(string)} interprets it as local
 * time. The net result is that the user sees the reminder
 * scheduled for 09:00 — three hours ahead of what they entered.
 *
 * <p>The fix is to always treat the on-the-wire {@code LocalDateTime}
 * as a UTC instant: the serialiser appends the {@code Z} suffix, and
 * the deserialiser reads the value as UTC. Combined with the SPA's
 * own UTC-ISO {@code .toISOString()} convention, the round trip
 * preserves the user's local time.
 *
 * <p>Existing reminder rows were stored as a wall-clock value that
 * matched the user's UTC instant. With this change they continue to
 * round-trip correctly.</p>
 */
@Configuration
public class JacksonConfig {

    /** ISO-8601 formatter with a trailing {@code Z} (always UTC). */
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(java.time.ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeUtcCustomizer() {
        return builder -> {
            // Drop fractional seconds — the SPA doesn't use them and the
            // shorter form keeps JSON responses lighter.
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.modulesToInstall(new JavaTimeModule(), utcLocalDateTimeModule());
        };
    }

    /**
     * Swaps Jackson's default {@code LocalDateTime} (de)serialiser for
     * a UTC-flavoured one. Values are always written as
     * {@code "2026-06-25T09:00:00Z"} and read back assuming the same
     * convention.
     */
    private SimpleModule utcLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("AthenyxUtcLocalDateTime");
        module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
        return module;
    }

    /**
     * Writes the {@link LocalDateTime} value as if it were already in
     * UTC, appending a {@code Z} suffix so the browser parses it as
     * an absolute instant. The numeric value is left untouched — we
     * are not converting timezones, we are simply tagging the wire
     * format so the client knows how to read it.
     */
    private static final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(ISO_UTC.format(value));
        }
    }

    /**
     * Reads a UTC-tagged ISO string back into a {@link LocalDateTime}.
     * The value is parsed as UTC (the {@code Z} suffix) and the wall
     * clock components are kept as-is. Falls back to the standard
     * ISO-local parser for backwards compatibility with payloads
     * that don't carry the suffix.
     */
    private static final class UtcLocalDateTimeDeserializer
            extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isEmpty()) return null;
            if (text.endsWith("Z")) {
                // Parse as an instant (UTC) and project to LocalDateTime
                // — this strips the Z and keeps the wall clock as
                // recorded by the user.
                java.time.Instant instant = java.time.Instant.parse(text);
                return LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
            }
            // Backwards-compat: accept the legacy suffix-less form
            // (e.g. "2026-06-25T09:00:00") produced by other servers
            // or older payloads.
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}

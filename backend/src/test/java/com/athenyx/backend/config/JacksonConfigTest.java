package com.athenyx.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JacksonConfig} — verifies that
 * {@code LocalDateTime} values are serialised with a trailing
 * {@code Z} so the SPA can read them as absolute instants and that
 * the matching deserialiser accepts both the new UTC-tagged form
 * and the legacy suffix-less one.
 */
class JacksonConfigTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().localDateTimeUtcCustomizer().customize(builder);
        mapper = builder.build();
    }

    @Test
    void localDateTime_serialisesWithZSuffix() throws Exception {
        LocalDateTime dt = LocalDateTime.of(2026, 6, 25, 9, 0, 0);
        String json = mapper.writeValueAsString(dt);
        assertEquals("\"2026-06-25T09:00:00Z\"", json);
    }

    @Test
    void localDateTime_deserialisesZSuffix() throws Exception {
        LocalDateTime result = mapper.readValue("\"2026-06-25T09:00:00Z\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 6, 25, 9, 0, 0), result);
    }

    @Test
    void localDateTime_deserialisesLegacySuffixLess() throws Exception {
        // Old payloads from before the UTC tag landed shouldn't
        // break — accept the bare form too.
        LocalDateTime result = mapper.readValue("\"2026-06-25T09:00:00\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 6, 25, 9, 0, 0), result);
    }

    @Test
    void roundTrip_preservesWallClock() throws Exception {
        // This is the property the SPA relies on: a LocalDateTime
        // serialised + deserialised should come back with the same
        // wall clock, so the user's input time round-trips.
        LocalDateTime original = LocalDateTime.of(2026, 6, 25, 6, 0, 0);
        String json = mapper.writeValueAsString(original);
        LocalDateTime restored = mapper.readValue(json, LocalDateTime.class);
        assertEquals(original, restored);
    }

    @Test
    void listOfLocalDateTime_serialisesWithZSuffix() throws Exception {
        // The reminders list endpoint returns a wrapper that
        // contains a list of records with LocalDateTime fields.
        // Make sure nested values are also tagged.
        List<LocalDateTime> values = List.of(
            LocalDateTime.of(2026, 6, 25, 6, 0, 0),
            LocalDateTime.of(2026, 6, 25, 9, 0, 0)
        );
        String json = mapper.writeValueAsString(values);
        assertTrue(json.contains("2026-06-25T06:00:00Z"), json);
        assertTrue(json.contains("2026-06-25T09:00:00Z"), json);
    }

    @Test
    void jdk8ModuleIsRegistered() {
        // Sanity check — if the JavaTimeModule wasn't installed
        // serialising a LocalDateTime would throw.
        boolean found = false;
        for (Object id : mapper.getRegisteredModuleIds()) {
            if (id != null && id.toString().contains("jsr310")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "JavaTimeModule should be registered");
    }
}

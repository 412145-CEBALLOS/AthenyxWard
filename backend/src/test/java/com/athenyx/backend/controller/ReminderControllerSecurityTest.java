package com.athenyx.backend.controller;

import com.athenyx.backend.dto.CreateReminderRequest;
import com.athenyx.backend.dto.ReminderResponse;
import com.athenyx.backend.dto.ReminderSummary;
import com.athenyx.backend.dto.UpdateReminderRequest;
import com.athenyx.backend.service.reminder.ReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReminderController}. Validates
 * {@code @PreAuthorize} annotations and the delegation to the
 * service layer. No HTTP layer is exercised — the integration test
 * covers the full pipeline.
 */
@ExtendWith(MockitoExtension.class)
class ReminderControllerSecurityTest {

    @Mock private ReminderService service;
    @Mock private Authentication auth;

    @InjectMocks private ReminderController controller;

    @Test
    void create_endpoint_isPremiumOrAdminOnly() throws Exception {
        Method m = ReminderController.class.getMethod(
            "create", CreateReminderRequest.class, Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).contains("PREMIUM").contains("ADMIN");
    }

    @Test
    void update_endpoint_isAuthenticated() throws Exception {
        Method m = ReminderController.class.getMethod(
            "update", Long.class, UpdateReminderRequest.class, Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void delete_endpoint_isAuthenticated() throws Exception {
        Method m = ReminderController.class.getMethod(
            "delete", Long.class, Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void list_endpoint_isAuthenticated() throws Exception {
        Method m = ReminderController.class.getMethod(
            "list", String.class, Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void getByEmail_endpoint_isAuthenticated() throws Exception {
        Method m = ReminderController.class.getMethod(
            "getByEmail", Long.class, Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void create_delegatesToService() {
        when(auth.getPrincipal()).thenReturn(1L);
        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), "msg");
        ReminderResponse response = new ReminderResponse(
            100L, 10L, LocalDateTime.of(2026, 6, 24, 10, 0),
            "msg", false,
            LocalDateTime.of(2026, 6, 22, 9, 0),
            LocalDateTime.of(2026, 6, 22, 9, 0));
        when(service.create(1L, req)).thenReturn(response);

        ResponseEntity<ReminderResponse> result = controller.create(req, auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(100L);
    }

    @Test
    void update_delegatesToService() {
        when(auth.getPrincipal()).thenReturn(1L);
        UpdateReminderRequest req = new UpdateReminderRequest(null, null, true);
        ReminderResponse response = new ReminderResponse(
            100L, 10L, LocalDateTime.of(2026, 6, 24, 10, 0),
            "msg", true,
            LocalDateTime.of(2026, 6, 22, 9, 0),
            LocalDateTime.of(2026, 6, 22, 9, 0));
        when(service.update(1L, 100L, req)).thenReturn(response);

        ResponseEntity<ReminderResponse> result = controller.update(100L, req, auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().done()).isTrue();
    }

    @Test
    void delete_returns204() {
        when(auth.getPrincipal()).thenReturn(1L);

        ResponseEntity<Void> result = controller.delete(100L, auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(1L, 100L);
    }

    @Test
    void list_delegatesToService() {
        when(auth.getPrincipal()).thenReturn(1L);
        ReminderResponse r = new ReminderResponse(
            100L, 10L, LocalDateTime.of(2026, 6, 24, 10, 0),
            "msg", false,
            LocalDateTime.of(2026, 6, 22, 9, 0),
            LocalDateTime.of(2026, 6, 22, 9, 0));
        when(service.findByUser(eq(1L), eq(ReminderService.Filter.PENDING)))
            .thenReturn(List.of(r));

        ResponseEntity<?> result = controller.list("pending", auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void list_defaultsToAllWhenFilterIsNull() {
        when(auth.getPrincipal()).thenReturn(1L);
        when(service.findByUser(eq(1L), eq(ReminderService.Filter.ALL)))
            .thenReturn(List.of());

        controller.list(null, auth);

        verify(service).findByUser(1L, ReminderService.Filter.ALL);
    }

    @Test
    void getByEmail_returnsSummary() {
        when(auth.getPrincipal()).thenReturn(1L);
        ReminderSummary summary = new ReminderSummary(
            100L, LocalDateTime.of(2026, 6, 24, 10, 0), false);
        when(service.findSummaryByEmail(1L, 10L)).thenReturn(summary);

        ResponseEntity<ReminderSummary> result = controller.getByEmail(10L, auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(summary);
    }

    @Test
    void getByEmail_returnsNullBodyWhenNoneExists() {
        when(auth.getPrincipal()).thenReturn(1L);
        when(service.findSummaryByEmail(1L, 0L)).thenReturn(null);

        ResponseEntity<ReminderSummary> result = controller.getByEmail(0L, auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNull();
    }
}

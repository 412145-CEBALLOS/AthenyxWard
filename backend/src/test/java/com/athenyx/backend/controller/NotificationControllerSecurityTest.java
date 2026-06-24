package com.athenyx.backend.controller;

import com.athenyx.backend.dto.UpcomingReminderNotification;
import com.athenyx.backend.service.NotificationService;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationController}. Verifies the
 * {@code @PreAuthorize} annotation and the delegation to
 * {@link NotificationService}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerSecurityTest {

    @Mock private NotificationService service;
    @Mock private Authentication auth;

    @InjectMocks private NotificationController controller;

    @Test
    void getUpcoming_endpoint_isPremiumOrAdminOnly() throws Exception {
        Method m = NotificationController.class.getMethod("getUpcoming", Authentication.class);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertThat(a).isNotNull();
        assertThat(a.value()).contains("PREMIUM").contains("ADMIN");
    }

    @Test
    void getUpcoming_delegatesToService() {
        when(auth.getPrincipal()).thenReturn(1L);
        UpcomingReminderNotification n = new UpcomingReminderNotification(
            10L, 99L, "Subj", "a@b.com", "msg",
            LocalDateTime.of(2026, 6, 24, 13, 0), false);
        when(service.getUpcomingReminders(1L)).thenReturn(List.of(n));

        ResponseEntity<List<UpcomingReminderNotification>> result = controller.getUpcoming(auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).reminderId()).isEqualTo(10L);
    }

    @Test
    void getUpcoming_returnsEmptyListWhenServiceFindsNothing() {
        when(auth.getPrincipal()).thenReturn(1L);
        when(service.getUpcomingReminders(1L)).thenReturn(List.of());

        ResponseEntity<List<UpcomingReminderNotification>> result = controller.getUpcoming(auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }
}

package com.athenyx.backend.controller;

import com.athenyx.backend.dto.AdminStatsResponse;
import com.athenyx.backend.dto.StatsPeriod;
import com.athenyx.backend.dto.UserStatsResponse;
import com.athenyx.backend.service.stats.StatsService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsControllerTest {

    @Mock
    private StatsService statsService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private StatsController controller;

    @Test
    void getUserStats_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = StatsController.class.getMethod("getUserStats", String.class, Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void getAdminStats_endpoint_isAdminOnly() throws Exception {
        Method m = StatsController.class.getMethod("getAdminStats", String.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getUserStats_returns200WithResponseBody() {
        when(authentication.getPrincipal()).thenReturn(1L);
        UserStatsResponse response = new UserStatsResponse(
            "week",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null
        );
        when(statsService.getUserStats(eq(1L), any(StatsPeriod.class))).thenReturn(response);

        ResponseEntity<UserStatsResponse> result = controller.getUserStats("week", authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().period()).isEqualTo("week");
    }

    @Test
    void getUserStats_defaultsToWeek() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(statsService.getUserStats(eq(1L), eq(StatsPeriod.WEEK))).thenReturn(
            new UserStatsResponse("week", List.of(), List.of(), List.of(), List.of(), List.of(), null, null));

        ResponseEntity<UserStatsResponse> result = controller.getUserStats(null, authentication);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().period()).isEqualTo("week");
    }

    @Test
    void getUserStats_rejectsInvalidPeriod() {
        when(authentication.getPrincipal()).thenReturn(1L);

        assertThatThrownBy(() -> controller.getUserStats("invalid", authentication))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAdminStats_returns200WithResponseBody() {
        AdminStatsResponse response = new AdminStatsResponse(
            "week",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new AdminStatsResponse.EngagementMetrics(1, 2, 3),
            new AdminStatsResponse.ConversionRate(0.0, 0.0, 0.0, true),
            List.of(),
            List.of()
        );
        when(statsService.getAdminStats(eq(StatsPeriod.MONTH))).thenReturn(response);

        ResponseEntity<AdminStatsResponse> result = controller.getAdminStats("month");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().period()).isEqualTo("week");
    }

    @Test
    void getAdminStats_rejectsInvalidPeriod() {
        assertThatThrownBy(() -> controller.getAdminStats("invalid"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

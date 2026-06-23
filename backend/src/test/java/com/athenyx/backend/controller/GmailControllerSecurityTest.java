package com.athenyx.backend.controller;

import com.athenyx.backend.dto.EmailImportantToggleResponse;
import com.athenyx.backend.dto.EmailSummary;
import com.athenyx.backend.gmail.GmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailControllerSecurityTest {

    @Mock
    private GmailService gmailService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private GmailController controller;

    @Test
    void toggleImportant_endpoint_hasPreAuthorizeAnnotation() throws Exception {
        Method method = GmailController.class.getMethod("toggleImportant", Long.class, Authentication.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("PREMIUM").contains("ADMIN");
    }

    @Test
    void getImportantEmails_endpoint_hasNoPreAuthorizeAnnotation() throws Exception {
        Method method = GmailController.class.getMethod("getImportantEmails", Authentication.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNull();
    }

    @Test
    void getImportantEmailCount_endpoint_hasNoPreAuthorizeAnnotation() throws Exception {
        Method method = GmailController.class.getMethod("getImportantEmailCount", Authentication.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNull();
    }

    @Test
    void toggleImportant_withPremiumUser_delegatesToService() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(gmailService.toggleImportant(1L, 10L))
                .thenReturn(new EmailImportantToggleResponse(10L, true));

        ResponseEntity<EmailImportantToggleResponse> response = controller.toggleImportant(10L, authentication);

        assertThat(response.getBody().isImportant()).isTrue();
    }

    @Test
    void toggleImportant_withAdminUser_delegatesToService() {
        when(authentication.getPrincipal()).thenReturn(2L);
        when(gmailService.toggleImportant(2L, 20L))
                .thenReturn(new EmailImportantToggleResponse(20L, false));

        ResponseEntity<EmailImportantToggleResponse> response = controller.toggleImportant(20L, authentication);

        assertThat(response.getBody().isImportant()).isFalse();
    }

    @Test
    void getImportantEmails_returnsEmailList() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(gmailService.getImportantEmails(1L)).thenReturn(List.of(
                new EmailSummary(10L, "gid", "a@b.com", "A", "Subj", "snip",
                        LocalDateTime.now(), LocalDateTime.now(), true, "now", true, null, null)
        ));

        ResponseEntity<List<EmailSummary>> response = controller.getImportantEmails(authentication);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isImportant()).isTrue();
    }

    @Test
    void getImportantEmailCount_returnsCount() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(gmailService.getImportantEmailCount(1L)).thenReturn(5L);

        ResponseEntity<?> response = controller.getImportantEmailCount(authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(((java.util.Map<?, ?>) response.getBody()).get("count")).isEqualTo(5L);
    }
}

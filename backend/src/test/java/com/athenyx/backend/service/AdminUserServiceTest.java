package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.audit.event.UserDeactivatedEvent;
import com.athenyx.backend.audit.event.UserDeletedEvent;
import com.athenyx.backend.audit.event.RoleChangedEvent;
import com.athenyx.backend.dto.AdminUserResponse;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import com.athenyx.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private AdminUserService service;

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .googleId("admin-gid")
                .email("admin@example.com")
                .name("Admin")
                .role(Role.ADMIN)
                .build();

        targetUser = User.builder()
                .id(2L)
                .googleId("target-gid")
                .email("target@example.com")
                .name("Target")
                .role(Role.PREMIUM)
                .isActive(true)
                .build();
    }

    @Test
    void updateRole_incrementsTokenVersion() {
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateRole(1L, "admin@example.com", 2L, Role.ADMIN);

        verify(userRepository).incrementTokenVersion(2L);
    }

    @Test
    void updateRole_doesNotIncrementTokenVersion_whenSelfOperation() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> service.updateRole(1L, "admin@example.com", 1L, Role.ADMIN))
                .isInstanceOf(AdminUserService.AdminSelfOperationException.class)
                .hasFieldOrPropertyWithValue("code", "cannot_change_own_role");

        verify(userRepository, never()).incrementTokenVersion(any());
    }

    @Test
    void updateActive_deactivating_incrementsTokenVersion() {
        targetUser.setActive(true);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse result = service.updateActive(1L, "admin@example.com", 2L, false);

        assertThat(result.isActive()).isFalse();
        verify(userRepository).incrementTokenVersion(2L);
        verify(auditEventPublisher).publishUserDeactivated(eq(1L), eq("admin@example.com"),
                eq("target@example.com"), eq(true));
    }

    @Test
    void updateActive_activating_doesNotIncrementTokenVersion() {
        targetUser.setActive(false);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse result = service.updateActive(1L, "admin@example.com", 2L, true);

        assertThat(result.isActive()).isTrue();
        verify(userRepository, never()).incrementTokenVersion(any());
    }

    @Test
    void updateActive_deactivatingSelf_throws() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> service.updateActive(1L, "admin@example.com", 1L, false))
                .isInstanceOf(AdminUserService.AdminSelfOperationException.class)
                .hasFieldOrPropertyWithValue("code", "cannot_deactivate_self");

        verify(userRepository, never()).incrementTokenVersion(any());
    }

    @Test
    void softDelete_incrementsTokenVersion() {
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(targetUser));

        service.softDelete(1L, "admin@example.com", 2L);

        verify(userRepository).incrementTokenVersion(2L);
        verify(userRepository).softDelete(eq(2L), any(LocalDateTime.class));
        verify(auditEventPublisher).publishUserDeleted(eq(1L), eq("admin@example.com"),
                eq("target@example.com"));
    }

    @Test
    void softDelete_self_throws() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> service.softDelete(1L, "admin@example.com", 1L))
                .isInstanceOf(AdminUserService.AdminSelfOperationException.class)
                .hasFieldOrPropertyWithValue("code", "cannot_delete_self");

        verify(userRepository, never()).incrementTokenVersion(any());
    }
}

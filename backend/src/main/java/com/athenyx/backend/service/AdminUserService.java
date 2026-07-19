package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.audit.event.RoleChangedEvent;
import com.athenyx.backend.audit.event.TrialResetEvent;
import com.athenyx.backend.audit.event.UserDeactivatedEvent;
import com.athenyx.backend.audit.event.UserDeletedEvent;
import com.athenyx.backend.dto.*;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final ReminderRepository reminderRepository;
    private final AuditEventPublisher auditEventPublisher;

    public AdminUserListResponse list(String query, Role role, Boolean active, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = userRepository.findAllFiltered(query, role, active, pageable);

        List<AdminUserResponse> items = result.getContent().stream()
            .map(this::toResponse)
            .toList();

        return new AdminUserListResponse(items, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    public AdminUserDetailResponse getDetail(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new UserNotFoundException(id));

        long emailCount = emailRepository.countByUserId(id);
        long reminderCount = reminderRepository.countByUserId(id);

        return new AdminUserDetailResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPictureUrl(),
            truncateGoogleId(user.getGoogleId()),
            user.getRole(),
            user.getTrialEndDate(),
            user.getAnalysisCount(),
            user.getUpdatedAt(),
            user.getCreatedAt(),
            user.isActive(),
            user.getDeletedAt(),
            emailCount,
            reminderCount
        );
    }

    @Transactional
    public AdminUserResponse updateRole(Long actorId, String actorEmail, Long targetId, Role newRole) {
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        if (target.getId().equals(actorId)) {
            throw new AdminSelfOperationException("cannot_change_own_role");
        }

        Role oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);
        userRepository.incrementTokenVersion(targetId);

        auditEventPublisher.publishRoleChanged(actorId, actorEmail, target.getEmail(), oldRole.name(), newRole.name());

        return toResponse(saved);
    }

    @Transactional
    public AdminUserResponse updateActive(Long actorId, String actorEmail, Long targetId, boolean active) {
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        if (target.getId().equals(actorId)) {
            throw new AdminSelfOperationException(active ? "cannot_activate_self" : "cannot_deactivate_self");
        }

        target.setActive(active);
        User saved = userRepository.save(target);

        if (!active) {
            userRepository.incrementTokenVersion(targetId);
        }

        auditEventPublisher.publishUserDeactivated(actorId, actorEmail, target.getEmail(), !active);

        return toResponse(saved);
    }

    @Transactional
    public ResetTrialResponse resetTrial(Long actorId, String actorEmail, Long targetId) {
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        String previousTrialEnd = target.getTrialEndDate() != null ? target.getTrialEndDate().toString() : null;
        LocalDateTime newTrialEnd = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);

        userRepository.resetTrial(targetId, newTrialEnd);
        target.setAnalysisCount(0);
        target.setTrialEndDate(newTrialEnd);

        auditEventPublisher.publishTrialReset(actorId, actorEmail, target.getEmail(), previousTrialEnd);

        return new ResetTrialResponse(newTrialEnd, 0);
    }

    @Transactional
    public void softDelete(Long actorId, String actorEmail, Long targetId) {
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        if (target.getId().equals(actorId)) {
            throw new AdminSelfOperationException("cannot_delete_self");
        }

        userRepository.incrementTokenVersion(targetId);
        userRepository.softDelete(targetId, LocalDateTime.now(ZoneOffset.UTC));
        auditEventPublisher.publishUserDeleted(actorId, actorEmail, target.getEmail());
    }

    public List<UserSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.min(limit, 10));
        return userRepository.searchByEmail(query, pageable).stream()
            .map(u -> new UserSearchResult(u.getId(), u.getName(), u.getEmail(),
                u.getPictureUrl(), u.getRole(), u.isActive()))
            .toList();
    }

    private AdminUserResponse toResponse(User u) {
        return new AdminUserResponse(
            u.getId(),
            u.getName(),
            u.getEmail(),
            u.getPictureUrl(),
            u.getRole(),
            u.getTrialEndDate(),
            u.getAnalysisCount(),
            u.getUpdatedAt(),
            u.isActive(),
            u.getCreatedAt()
        );
    }

    private String truncateGoogleId(String googleId) {
        if (googleId == null) return null;
        if (googleId.length() <= 12) return googleId;
        return googleId.substring(0, 12) + "…";
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long id) {
            super("User not found: " + id);
        }
    }

    public static class AdminSelfOperationException extends RuntimeException {
        private final String code;

        public AdminSelfOperationException(String code) {
            super(code);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
